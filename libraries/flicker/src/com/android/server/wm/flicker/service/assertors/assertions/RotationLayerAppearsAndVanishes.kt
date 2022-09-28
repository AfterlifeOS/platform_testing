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

package com.android.server.wm.flicker.service.assertors.assertions

import com.android.server.wm.flicker.service.assertors.ComponentBuilder
import com.android.server.wm.flicker.traces.layers.LayersTraceSubject
import com.android.server.wm.traces.common.ComponentNameMatcher
import com.android.server.wm.traces.common.transition.Transition

/**
 * Checks that the [ComponentNameMatcher.ROTATION] layer appears during the transition, doesn't
 * flicker, and disappears before the transition is complete.
 */
class RotationLayerAppearsAndVanishes(component: ComponentBuilder) :
    BaseAssertionBuilderWithComponent(component) {
    /** {@inheritDoc} */
    override fun doEvaluate(transition: Transition, layerSubject: LayersTraceSubject) {
        layerSubject
            .isVisible(component.build(transition))
            .then()
            .isVisible(ComponentNameMatcher.ROTATION)
            .then()
            .isVisible(component.build(transition))
            .isInvisible(ComponentNameMatcher.ROTATION)
            .forAllEntries()
    }
}
