/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.apptransition.tests;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.test.launcherhelper.LauncherStrategyFactory;
import android.support.test.uiautomator.UiDevice;
import android.system.helpers.LockscreenHelper;
import android.system.helpers.OverviewHelper;
import android.system.helpers.SettingsHelper;
import android.util.Log;
import android.view.IWindowManager;
import android.view.Surface;
import android.view.WindowManagerGlobal;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.logging.AtraceLogger;

import com.android.launcher3.tapl.LauncherInstrumentation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests to test various latencies in the system.
 */
public class LatencyTests extends Instrumentation {

    private static final int DEFAULT_ITERATION_COUNT = 10;
    private static final String KEY_ITERATION_COUNT = "iteration_count";
    private static final long CLOCK_SETTLE_DELAY = 2000;
    private static final String FINGERPRINT_WAKE_FAKE_COMMAND = "am broadcast -a "
            + "com.android.systemui.latency.ACTION_FINGERPRINT_WAKE";
    private static final String TURN_ON_SCREEN_COMMAND = "am broadcast -a "
            + "com.android.systemui.latency.ACTION_TURN_ON_SCREEN";
    private static final String ENABLE_LATENCY_TEST =
            "device_config put latency_tracker enabled true";
    private static final String RESET_ENABLE_LATENCY_TEST =
            "device_config delete latency_tracker enabled";
    private static final String AM_START_COMMAND_TEMPLATE = "am start -a %s";
    private static final String PIN = "1234";
    private static final String KEY_TRACE_DIRECTORY = "trace_directory";
    private static final String KEY_TRACE_CATEGORY = "trace_categories";
    private static final String KEY_TRACE_BUFFERSIZE = "trace_bufferSize";
    private static final String KEY_TRACE_DUMPINTERVAL = "tracedump_interval";
    private static final String DEFAULT_TRACE_CATEGORIES = "sched,freq,gfx,view,dalvik,webview,"
            + "input,wm,disk,am,wm";
    private static final String DEFAULT_TRACE_BUFFER_SIZE = "20000";
    private static final String DEFAULT_TRACE_DUMP_INTERVAL = "10";
    private static final String DELIMITER = ",";
    private static final String TEST_EXPANDNOTIFICATIONS = "testExpandNotificationsLatency";
    private static final String TEST_FINGERPRINT = "testFingerprintWakeAndUnlock";
    private static final String TEST_SCREEN_TURNON = "testScreenTurnOn";
    private static final String TEST_PINCHECK_DELAY = "testPinCheckDelay";
    private static final String TEST_APPTORECENTS = "testAppToRecents";
    private static final String TEST_ROTATION_LATENCY = "testRotationLatency";
    private static final String TEST_SETTINGS_SEARCH = "testSettingsSearch";

    private String mTraceDirectoryStr = null;
    private File mRootTrace = null;
    private int mTraceBufferSize = 0;
    private int mTraceDumpInterval = 0;
    private Set<String> mTraceCategoriesSet = null;
    private AtraceLogger mAtraceLogger = null;

    private UiDevice mDevice;
    private LauncherInstrumentation mLauncher;
    private int mIterationCount;

    @Before
    public void setUp() throws Exception {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        try {
            mDevice.executeShellCommand(ENABLE_LATENCY_TEST);
        } catch (IOException ioe) {
            Log.e("LatencyTests", "Failed to enable latency commands");
        }
        Bundle mArgs = InstrumentationRegistry.getArguments();
        mIterationCount = Integer.parseInt(mArgs.getString(KEY_ITERATION_COUNT,
                Integer.toString(DEFAULT_ITERATION_COUNT)));
        mDevice.pressHome();

        // Parse the trace parameters
        mTraceDirectoryStr = mArgs.getString(KEY_TRACE_DIRECTORY);
        if (isTracesEnabled()) {
            String traceCategoriesStr = mArgs
                    .getString(KEY_TRACE_CATEGORY, DEFAULT_TRACE_CATEGORIES);
            mTraceBufferSize = Integer.parseInt(mArgs.getString(KEY_TRACE_BUFFERSIZE,
                    DEFAULT_TRACE_BUFFER_SIZE));
            mTraceDumpInterval = Integer.parseInt(mArgs.getString(KEY_TRACE_DUMPINTERVAL,
                    DEFAULT_TRACE_DUMP_INTERVAL));
            mTraceCategoriesSet = new HashSet<String>();
            if (!traceCategoriesStr.isEmpty()) {
                String[] traceCategoriesSplit = traceCategoriesStr.split(DELIMITER);
                for (int i = 0; i < traceCategoriesSplit.length; i++) {
                    mTraceCategoriesSet.add(traceCategoriesSplit[i]);
                }
            }
        }
        // Need to run strategy initialization code as a precondition for tests.
        LauncherStrategyFactory.getInstance(mDevice);
        mLauncher = new LauncherInstrumentation(getInstrumentation());
    }

    @After
    public void tearDown() {
        try {
            mDevice.executeShellCommand(RESET_ENABLE_LATENCY_TEST);
        } catch (IOException ioe) {
            Log.e("LatencyTests", "Failed to reset latency commands");
        }
    }

    /**
     * Test to track how long it takes to expand the notification shade when swiping.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=0 delay=x".
     */
    @Test
    public void testExpandNotificationsLatency() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        for (int i = 0; i < mIterationCount; i++) {
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_EXPANDNOTIFICATIONS, i));
            }
            swipeDown();
            mDevice.waitForIdle();
            swipeUp();
            mDevice.waitForIdle();

            // Wait for clocks to settle down
            SystemClock.sleep(CLOCK_SETTLE_DELAY);
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }

        }
    }

    private void swipeDown() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                0, mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2,
                15);
    }

    private void swipeUp() {
        mDevice.swipe(mDevice.getDisplayWidth() / 2,
                mDevice.getDisplayHeight() / 2,
                mDevice.getDisplayWidth() / 2,
                0,
                15);
    }

    /**
     * Test to track how long it takes until the animation starts in a fingerprint-wake-and-unlock
     * sequence.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=2 delay=x".
     */
    @Test
    public void testFingerprintWakeAndUnlock() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        for (int i = 0; i < mIterationCount; i++) {
            mDevice.sleep();

            // Wait for clocks to settle down
            SystemClock.sleep(CLOCK_SETTLE_DELAY);
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_FINGERPRINT, i));
            }

            mDevice.executeShellCommand(FINGERPRINT_WAKE_FAKE_COMMAND);
            mDevice.waitForIdle();

            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }
        }
    }

    /**
     * Test how long it takes until the screen is fully turned on.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=5 delay=x".
     */
    @Test
    public void testScreenTurnOn() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        for (int i = 0; i < mIterationCount; i++) {
            mDevice.sleep();

            // Wait for clocks to settle down
            SystemClock.sleep(CLOCK_SETTLE_DELAY);
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_SCREEN_TURNON, i));
            }

            mDevice.executeShellCommand(TURN_ON_SCREEN_COMMAND);
            mDevice.waitForIdle();
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }
        }

        // Put device to home screen.
        mDevice.pressMenu();
        mDevice.waitForIdle();
    }

    /**
     * Test how long it takes until the credential (PIN) is checked.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=3 delay=x".
     */
    @Test
    public void testPinCheckDelay() throws Exception {
        LockscreenHelper.getInstance().setScreenLockViaShell(PIN, LockscreenHelper.MODE_PIN);
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        for (int i = 0; i < mIterationCount; i++) {
            mDevice.sleep();
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_PINCHECK_DELAY, i));
            }

            // Make sure not to launch camera with "double-tap".
            Thread.sleep(300);
            mDevice.wakeUp();
            LockscreenHelper.getInstance().unlockScreen(PIN);
            mDevice.waitForIdle();
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }
        }
        LockscreenHelper.getInstance().removeScreenLockViaShell(PIN);
        mDevice.pressHome();
    }

    /**
     * Test how long it takes for rotation animation.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=6 delay=x".
     */
    @Test
    public void testRotationLatency() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        mDevice.wakeUp();
        for (int i = 0; i < mIterationCount; i++) {
            mDevice.executeShellCommand(String.format(AM_START_COMMAND_TEMPLATE,
                    Settings.ACTION_SETTINGS));
            mDevice.waitForIdle();
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_ROTATION_LATENCY, i));
            }

            IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            wm.freezeRotation(Surface.ROTATION_0, /* caller= */ "LatencyTests");
            mDevice.waitForIdle();
            wm.freezeRotation(Surface.ROTATION_90, /* caller= */ "LatencyTests");
            mDevice.waitForIdle();
            wm.thawRotation(/* caller= */ "LatencyTests");
            mDevice.waitForIdle();

            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }
        }
        mDevice.pressHome();
        mDevice.waitForIdle();
    }

    /**
     * Test that measure how long the total time takes until recents is visible after pressing it.
     * Note that this is different from {@link AppTransitionTests#testAppToRecents} as we are
     * measuring the full latency here, but in the app transition test we only measure the time
     * spent after startActivity is called. This might be different as SystemUI does a lot of binder
     * calls before calling startActivity.
     * <p>
     * Every iteration will output a log in the form of "LatencyTracker/action=1 delay=x".
     */
    @Test
    public void testAppToRecents() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        OverviewHelper.getInstance().populateManyRecentApps();
        for (int i = 0; i < mIterationCount; i++) {
            mDevice.executeShellCommand(String.format(AM_START_COMMAND_TEMPLATE,
                    Settings.ACTION_SETTINGS));
            mDevice.waitForIdle();

            // Wait for clocks to settle.
            SystemClock.sleep(CLOCK_SETTLE_DELAY);
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_APPTORECENTS, i));
            }
            mLauncher.getLaunchedAppState().switchToOverview();

            // Make sure all the animations are really done.
            SystemClock.sleep(200);
            if (null != mAtraceLogger) {
                mAtraceLogger.atraceStop();
            }
        }
    }

    @Test
    public void testSettingsSearch() throws Exception {
        if (isTracesEnabled()) {
            createTraceDirectory();
        }
        SettingsHelper settingsHelper = SettingsHelper.getInstance();

        for (int i = 0; i < mIterationCount; i++) {
            mDevice.executeShellCommand(String.format(AM_START_COMMAND_TEMPLATE,
                    Settings.ACTION_SETTINGS));
            settingsHelper.openSearch(InstrumentationRegistry.
                    getInstrumentation().getContext());
            if (mAtraceLogger != null) {
                mAtraceLogger.atraceStart(mTraceCategoriesSet, mTraceBufferSize,
                        mTraceDumpInterval, mRootTrace,
                        String.format("%s-%d", TEST_SETTINGS_SEARCH, i));
            }
            settingsHelper.performNoResultQuery();
            if (mAtraceLogger != null) {
                mAtraceLogger.atraceStop();
            }
            mDevice.pressHome();
            mDevice.waitForIdle();
        }
    }

    /**
     * Create trace directory for the latency tests to store the trace files.
     */
    private void createTraceDirectory() throws Exception {
        mRootTrace = new File(mTraceDirectoryStr);
        if (!mRootTrace.exists() && !mRootTrace.mkdirs()) {
            throw new Exception("Unable to create the trace directory");
        }
        mAtraceLogger = AtraceLogger.getAtraceLoggerInstance(getInstrumentation());
    }

    /**
     * @return
     */
    private boolean isTracesEnabled() {
        return (null != mTraceDirectoryStr && !mTraceDirectoryStr.isEmpty());
    }
}
