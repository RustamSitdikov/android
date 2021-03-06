/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.google.common.collect.Iterables;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;

public class MlkitLightClassTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.override(true);
    StudioFlags.MLKIT_LIGHT_CLASSES.override(true);

    PsiFile modelFile = myFixture.addFileToProject("/assets/my_model.tflite", "model data.");
    PsiTestUtil.addSourceContentToRoots(myModule, modelFile.getVirtualFile());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.clearOverride();
      StudioFlags.MLKIT_LIGHT_CLASSES.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testHighlighting_java() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import com.google.mlkit.auto.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel();\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_kotlin() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import com.google.mlkit.auto.MyModel\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        MyModel()\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testModuleService() {
    MlkitModuleService mlkitService = MlkitModuleService.getInstance(myModule);
    List<LightModelClass> lightClasses = mlkitService.getLightModelClassList();
    assertThat(lightClasses).hasSize(1);
    LightModelClass lightClass = Iterables.getOnlyElement(lightClasses);
    assertThat(lightClass.getName()).isEqualTo("MyModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClass)).isEqualTo(myModule);
  }
}
