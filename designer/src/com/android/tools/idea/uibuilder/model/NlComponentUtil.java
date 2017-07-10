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
package com.android.tools.idea.uibuilder.model;

import com.google.common.collect.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class NlComponentUtil {
  /**
   * Partitions the given list of components into a map where each value is a list of siblings,
   * in the same order as in the original list, and where the keys are the parents (or null
   * for the components that do not have a parent).
   * <p/>
   * The value lists will never be empty. The parent key will be null for components without parents.
   *
   * @param components the components to be grouped
   * @return a map from parents (or null) to a list of components with the corresponding parent
   */
  @NotNull
  public static Multimap<NlComponent, NlComponent> groupSiblings(@NotNull Collection<? extends NlComponent> components) {
    if (components.isEmpty()) {
      return ImmutableMultimap.of();
    }

    if (components.size() == 1) {
      Multimap<NlComponent, NlComponent> siblingLists = ArrayListMultimap.create(1, 1);
      NlComponent component = components.iterator().next();
      siblingLists.put(component.getParent(), component);
      return siblingLists;
    }

    Multimap<NlComponent, NlComponent> siblingLists = HashMultimap.create();
    for (NlComponent component : components) {
      NlComponent parent = component.getParent();
      siblingLists.put(parent, component);
    }

    return siblingLists;
  }

  /**
   * Check if the provided potential descendant component has an ancestor in the list
   *
   * @return true if potentialAncestor element have potentialDescendant as child or grand-child
   */
  public static boolean isDescendant(@NotNull NlComponent potentialDescendant, @NotNull List<NlComponent> potentialAncestors) {
    for (NlComponent component : potentialAncestors) {
      NlComponent same = potentialDescendant;
      while (same != null) {
        if (same == component) {
          return true;
        }
        same = same.getParent();
      }
    }
    return false;
  }
}
