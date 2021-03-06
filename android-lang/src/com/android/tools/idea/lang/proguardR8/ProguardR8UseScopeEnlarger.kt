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
package com.android.tools.idea.lang.proguardR8

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Adds Proguard/R8 files to a search scope for PsiMember/KtProperty.
 *
 * We need to extend useScope [com.intellij.psi.search.PsiSearchHelper.getUseScope] for a non-public PsiMember(includes PsiClasses) and
 * KtProperty because they can be used in a Proguard/R8 files.
 */
class ProguardR8UseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element is PsiMember || element is KtProperty) {
      return GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.allScope(element.project),
        ProguardR8FileType.INSTANCE
      )
    }
    return null
  }
}