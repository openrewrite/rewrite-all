/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.assertions;

import org.intellij.lang.annotations.Language;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.json.JsonParser;
import org.openrewrite.json.tree.Json;
import org.openrewrite.test.SourceSpec;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

public class ConvertingSourceSpec<FROM extends Tree, TO extends Tree> extends SourceSpec<SourceFile> {
    public ConvertingSourceSpec(
      String before,
      @Nullable UnaryOperator<String> after,
      Parser.Builder parser,
      Class<FROM> fromClass,
      Class<TO> toClass) {
        super(SourceFile.class, null, parser, before, after);
        assertThat(fromClass)
          .withFailMessage(
            "The <%s> parser does not support <%s>. Did you pass the correct Tree or SourceFile type as 'from' or use the wrong parser Builder?",
            parser.getDslName(),
            fromClass.getSimpleName())
          .isAssignableFrom(parser.getSourceFileType());
        beforeRecipe(from -> assertThat(from).isInstanceOf(fromClass));
        afterRecipe(to -> assertThat(to).isInstanceOf(toClass));
    }

    public static SourceSpecs jsonToYaml(@Language("json") String before,
                                         @Language("yaml") String after) {
        return jsonToYaml(before, after, spec ->
          spec
            .path("convert.json")
            .afterRecipe(yaml -> assertThat(yaml.getSourcePath()).isEqualTo(Path.of("convert.yaml"))));
    }

    private static SourceSpecs jsonToYaml(@Language("json") String before,
                                          @Language("yaml") String after,
                                          Consumer<SourceSpec<SourceFile>> spec) {
        ConvertingSourceSpec<Json, Yaml> source = new ConvertingSourceSpec<>(
          before,
          s -> after,
          JsonParser.builder(),
          Json.class,
          Yaml.class);
        spec.accept(source);
        return source;
    }

    public static SourceSpecs yamlToJson(@Language("yaml") String before,
                                         @Language("json") String after) {
        return yamlToJson(before, after, spec ->
          spec
            .path("convert.yaml")
            .afterRecipe(json -> assertThat(json.getSourcePath()).isEqualTo(Path.of("convert.json"))));
    }

    private static SourceSpecs yamlToJson(@Language("yaml") String before,
                                          @Language("json") String after,
                                          Consumer<SourceSpec<SourceFile>> spec) {
        ConvertingSourceSpec<Yaml, Json> source = new ConvertingSourceSpec<>(
          before,
          s -> after,
          YamlParser.builder(),
          Yaml.class,
          Json.class);
        spec.accept(source);
        return source;
    }
}
