/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.colorpicker2

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color

class ColorPipetteButtonTest {

  @Test
  fun testColorPipetteCanPickupColor() {
    val model = ColorPickerModel()
    model.setColor(Color.WHITE, null)

    val fakePipette = FakeColorPipette()
    val pipetteButton = ColorPipetteButton(model, fakePipette)

    fakePipette.pickedColor = Color.RED
    pipetteButton.doClick()
    assertEquals(Color.RED, model.color)

    fakePipette.pickedColor = Color.YELLOW
    pipetteButton.doClick()
    assertEquals(Color.YELLOW, model.color)

    fakePipette.pickedColor = Color.BLUE
    fakePipette.shouldSucceed = false
    pipetteButton.doClick()
    assertEquals(Color.YELLOW, model.color)
  }
}
