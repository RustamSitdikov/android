/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import org.jetbrains.android.AndroidTestCase;

public class SceneModeTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testCreateSceneView() {
    NlDesignSurface surface = mock(NlDesignSurface.class);
    LayoutlibSceneManager manager = mock(LayoutlibSceneManager.class);
    NlModel model = mock(NlModel.class);
    when(model.getType()).thenReturn(LayoutFileType.INSTANCE);
    when(manager.getModel()).thenReturn(model);

    SceneView primary = SceneMode.RENDER_AND_BLUEPRINT.createPrimarySceneView(surface, manager);
    SceneView secondary = SceneMode.RENDER_AND_BLUEPRINT.createSecondarySceneView(surface, manager);
    assertThat(primary, instanceOf(ScreenView.class));
    assertThat(secondary, instanceOf(BlueprintView.class));
  }
}