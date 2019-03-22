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
package com.android.tools.idea.gradle.project.sync.setup.post.module;

import com.android.tools.idea.gradle.project.sync.setup.post.ModuleSetupStep;
import com.android.tools.idea.project.AndroidRunConfigurations;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidRunConfigurationSetupStep extends ModuleSetupStep {

  @Override
  public void setUpModule(@NotNull Module module, @Nullable ProgressIndicator indicator) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null && facet.getConfiguration().isAppProject()) {
      getConfigurations().createRunConfiguration(facet);
    }
  }

  @NotNull
  protected AndroidRunConfigurations getConfigurations() {
    return AndroidRunConfigurations.getInstance();
  }
}
