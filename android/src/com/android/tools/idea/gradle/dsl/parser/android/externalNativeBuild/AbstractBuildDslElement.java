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
package com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild;

import static com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild.AbstractBuildModelImpl.PATH;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBuildDslElement extends GradleDslBlockElement {
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap = Stream.of(new Object[][]{
    {"path", property, PATH, VAR}
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> groovyToModelNameMap = Stream.of(new Object[][]{
    {"path", property, PATH, VAR},
    {"path", exactly(1), PATH, SET}
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public AbstractBuildDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}