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

package android.tools.common.flicker.extractors

import android.tools.common.flicker.config.ScenarioConfig
import android.tools.common.io.IReader
import android.tools.common.traces.events.Cuj
import android.tools.common.traces.events.CujType

class TaggedScenarioExtractorBuilder {
    private var targetTag: CujType? = null
    private var config: ScenarioConfig? = null
    private var transitionMatcher: TransitionMatcher = TaggedCujTransitionMatcher()
    private var adjustCuj: CujAdjust =
        object : CujAdjust {
            override fun adjustCuj(cujEntry: Cuj, reader: IReader): Cuj = cujEntry
        }

    fun setTargetTag(value: CujType): TaggedScenarioExtractorBuilder = apply { targetTag = value }

    fun setConfig(value: ScenarioConfig): TaggedScenarioExtractorBuilder = apply { config = value }

    fun setTransitionMatcher(value: TransitionMatcher): TaggedScenarioExtractorBuilder = apply {
        transitionMatcher = value
    }

    fun setAdjustCuj(value: CujAdjust): TaggedScenarioExtractorBuilder = apply { adjustCuj = value }

    fun build(): TaggedScenarioExtractor {
        val targetTag = targetTag ?: error("Missing targetTag")
        val config = config ?: error("Missing type")
        return TaggedScenarioExtractor(targetTag, config, transitionMatcher, adjustCuj)
    }
}
