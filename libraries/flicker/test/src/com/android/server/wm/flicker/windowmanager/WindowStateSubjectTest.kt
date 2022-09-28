/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.wm.flicker.windowmanager

import com.android.server.wm.flicker.TestComponents
import com.android.server.wm.flicker.assertThatErrorContainsDebugInfo
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.readWmTraceFromFile
import com.android.server.wm.flicker.traces.windowmanager.WindowManagerTraceSubject
import com.android.server.wm.traces.common.Cache
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test

class WindowStateSubjectTest {
    private val trace
        get() = readWmTraceFromFile("wm_trace_openchrome.pb")

    @Before
    fun before() {
        Cache.clear()
    }

    @Test
    fun exceptionContainsDebugInfoImaginary() {
        val error =
            assertThrows(AssertionError::class.java) {
                WindowManagerTraceSubject.assertThat(trace)
                    .first()
                    .windowState(TestComponents.IMAGINARY.className)
                    .exists()
            }
        assertThatErrorContainsDebugInfo(error)
        Truth.assertThat(error).hasMessageThat().contains(TestComponents.IMAGINARY.className)
        Truth.assertThat(error).hasMessageThat().contains("Window title")
    }

    @Test
    fun exceptionContainsDebugInfoConcrete() {
        val error =
            assertThrows(AssertionError::class.java) {
                WindowManagerTraceSubject.assertThat(trace).first().subjects.first().doesNotExist()
            }
        assertThatErrorContainsDebugInfo(error)
    }
}
