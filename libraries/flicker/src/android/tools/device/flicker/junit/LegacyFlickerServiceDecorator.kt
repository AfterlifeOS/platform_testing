/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.device.flicker.junit

import android.os.Bundle
import android.tools.common.FLICKER_TAG
import android.tools.common.Logger
import android.tools.common.Scenario
import android.tools.common.flicker.FlickerConfig
import android.tools.common.flicker.FlickerService
import android.tools.common.flicker.annotation.FlickerServiceCompatible
import android.tools.common.flicker.config.FlickerConfig
import android.tools.common.flicker.config.FlickerServiceConfig
import android.tools.common.flicker.config.ScenarioId
import android.tools.device.flicker.FlickerServiceResultsCollector.Companion.FAAS_METRICS_PREFIX
import android.tools.device.flicker.IS_FAAS_ENABLED
import android.tools.device.flicker.datastore.CachedResultReader
import android.tools.device.flicker.datastore.DataStore
import android.tools.device.flicker.isShellTransitionsEnabled
import android.tools.device.traces.TRACE_CONFIG_REQUIRE_CHANGES
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement
import org.junit.runners.model.TestClass

class LegacyFlickerServiceDecorator(
    testClass: TestClass,
    val scenario: Scenario?,
    private val transitionRunner: ITransitionRunner,
    private val skipNonBlocking: Boolean,
    private val arguments: Bundle,
    inner: IFlickerJUnitDecorator?
) : AbstractFlickerRunnerDecorator(testClass, inner) {
    private val flickerService by lazy { FlickerService(getFlickerConfig()) }

    private fun getFlickerConfig(): FlickerConfig {
        val annotatedMethods =
            testClass.getAnnotatedMethods(
                android.tools.common.flicker.annotation.FlickerConfigProvider::class.java
            )
        if (annotatedMethods.size == 0) {
            return FlickerConfig().use(FlickerServiceConfig.DEFAULT)
        }

        val flickerConfigProviderProviderFunction = annotatedMethods[0]
        return flickerConfigProviderProviderFunction.invokeExplosively(testClass) as FlickerConfig
    }

    private val isClassFlickerServiceCompatible: Boolean
        get() =
            testClass.annotations.filterIsInstance<FlickerServiceCompatible>().firstOrNull() != null

    override fun getChildDescription(method: FrameworkMethod): Description {
        requireNotNull(scenario) { "Expected to have a scenario to run" }
        return if (isMethodHandledByDecorator(method)) {
            Description.createTestDescription(
                testClass.javaClass,
                "${method.name}[${scenario.description}]",
                *method.annotations
            )
        } else {
            inner?.getChildDescription(method) ?: error("Descriptor not found")
        }
    }

    override fun getTestMethods(test: Any): List<FrameworkMethod> {
        val result = inner?.getTestMethods(test)?.toMutableList() ?: mutableListOf()
        if (shouldComputeTestMethods()) {
            Logger.withTracing(
                "$FAAS_METRICS_PREFIX getTestMethods ${testClass.javaClass.simpleName}"
            ) {
                requireNotNull(scenario) { "Expected to have a scenario to run" }
                result.addAll(computeFlickerServiceTests(test, scenario))
                Logger.d(FLICKER_TAG, "Computed ${result.size} flicker tests")
            }
        }
        return result
    }

    override fun getMethodInvoker(method: FrameworkMethod, test: Any): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                if (isMethodHandledByDecorator(method)) {
                    val description = getChildDescription(method)
                    (method as InjectedTestCase).execute(description)
                } else {
                    inner?.getMethodInvoker(method, test)?.evaluate()
                }
            }
        }
    }

    private fun isMethodHandledByDecorator(method: FrameworkMethod): Boolean {
        return method is InjectedTestCase && method.injectedBy == this
    }

    private fun shouldComputeTestMethods(): Boolean {
        // Don't compute when called from validateInstanceMethods since this will fail
        // as the parameters will not be set. And AndroidLogOnlyBuilder is a non-executing runner
        // used to run tests in dry-run mode, so we don't want to execute in flicker transition in
        // that case either.
        val stackTrace = Thread.currentThread().stackTrace
        val isDryRun =
            stackTrace.any { it.methodName == "validateInstanceMethods" } ||
                stackTrace.any {
                    it.className == "androidx.test.internal.runner.AndroidLogOnlyBuilder"
                } ||
                stackTrace.any {
                    it.className == "androidx.test.internal.runner.NonExecutingRunner"
                }

        val filters = getFiltersFromArguments()
        // a method is filtered out if there's a filter and the filter doesn't include it's class
        // or if the filter includes its class, but it's not flicker as a service
        val isFilteredOut =
            filters.isNotEmpty() && !(filters[testClass.javaClass.simpleName] ?: false)

        return IS_FAAS_ENABLED &&
            isShellTransitionsEnabled &&
            isClassFlickerServiceCompatible &&
            !isFilteredOut &&
            !isDryRun
    }

    private fun getFiltersFromArguments(): Map<String, Boolean> {
        val testFilters = arguments.getString(OPTION_NAME) ?: return emptyMap()
        val result = mutableMapOf<String, Boolean>()

        // Test the display name against all filter arguments.
        for (testFilter in testFilters.split(",")) {
            val filterComponents = testFilter.split("#")
            if (filterComponents.size != 2) {
                Logger.e(
                    LOG_TAG,
                    "Invalid filter-tests instrumentation argument supplied, $testFilter."
                )
                continue
            }
            val methodName = filterComponents[1]
            val className = filterComponents[0]
            result[className] = methodName.startsWith(FAAS_METRICS_PREFIX)
        }

        return result
    }

    /**
     * Runs the flicker transition to collect the traces and run FaaS on them to get the FaaS
     * results and then create functional test results for each of them.
     */
    private fun computeFlickerServiceTests(
        test: Any,
        testScenario: Scenario
    ): Collection<InjectedTestCase> {
        if (!DataStore.containsResult(testScenario)) {
            val description =
                Description.createTestDescription(
                    this::class.java.simpleName,
                    "computeFlickerServiceTests"
                )
            transitionRunner.runTransition(testScenario, test, description)
        }
        val reader = CachedResultReader(testScenario, TRACE_CONFIG_REQUIRE_CHANGES)

        val expectedScenarios =
            testClass.annotations
                .filterIsInstance<FlickerServiceCompatible>()
                .first()
                .expectedCujs
                .map { ScenarioId(it) }
                .toSet()

        return FlickerServiceDecorator.getFaasTestCases(
            testScenario,
            expectedScenarios,
            "",
            reader,
            flickerService,
            instrumentation,
            this,
            skipNonBlocking = skipNonBlocking,
        )
    }

    companion object {
        private const val OPTION_NAME = "filter-tests"
        private val LOG_TAG = LegacyFlickerServiceDecorator::class.java.simpleName
    }
}
