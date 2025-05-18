/*
 * Copyright 2021 the original author or authors.
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
package org.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.table.LanguageCompositionPerFile;
import org.openrewrite.table.LanguageCompositionPerFolder;
import org.openrewrite.table.LanguageCompositionPerRepository;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.text.PlainText;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.PathUtils.separatorsToSystem;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.dir;
import static org.openrewrite.test.SourceSpecs.text;

class LanguageCompositionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LanguageComposition());
    }

    @Test
    void javaAndPlainText() {
        rewriteRun(
          spec -> {
              spec.dataTable(LanguageCompositionPerRepository.Row.class, table -> {
                  assertThat(table).hasSize(2);
                  boolean hasJava = false;
                  boolean hasPlainText = false;
                  for (LanguageCompositionPerRepository.Row row : table) {
                      if ("Java".equals(row.getLanguage())) {
                          assertThat(row.getFileCount()).isEqualTo(1);
                          hasJava = true;
                      } else if ("Plain text".equals(row.getLanguage())) {
                          assertThat(row.getFileCount()).isEqualTo(2);
                          hasPlainText = true;
                      }
                  }
                  assertThat(hasJava).isTrue();
                  assertThat(hasPlainText).isTrue();
              });
              spec.dataTable(LanguageCompositionPerFile.Row.class, table -> {
                  assertThat(table).hasSize(3);
                  boolean hasJava = false;
                  boolean hasPlainText = false;
                  for (LanguageCompositionPerFile.Row row : table) {
                      if ("Java".equals(row.getLanguage())) {
                          hasJava = true;
                      } else if ("Plain text".equals(row.getLanguage())) {
                          hasPlainText = true;
                      }
                  }
                  assertThat(hasJava).isTrue();
                  assertThat(hasPlainText).isTrue();
              });
              spec.dataTable(LanguageCompositionPerFolder.Row.class, table -> {
                  assertThat(table).hasSize(2);
                  assertThat(table).contains(
                    new LanguageCompositionPerFolder.Row(separatorsToSystem("src/java/main/com/whatever"), "Java", 1, 3),
                    new LanguageCompositionPerFolder.Row("", "Plain text", 2, 4)
                  );
              });
          },
          dir("src/java/main",
            //language=java
            java(
              """
                package com.whatever;
                
                class A {
                    void foo() {
                    }
                }
                """
            )
            ),
          text(
            """
              hello
              world
              
              """
          ),
          text(
            """
              hello
              world
              
              """
          )
        );
    }

    @Test
    void hasParseFailures() {
        rewriteRun(
          spec -> {
              spec.allSources(s -> s.markers(new ParseExceptionResult(Tree.randomId(), "all", "test", "Parsing failed.", null)));
              spec.dataTable(LanguageCompositionPerFile.Row.class, table -> {
                  assertThat(table).hasSize(1);
                  assertThat(table.getFirst().getHasParseFailures()).isTrue();
              });
          },
          //language=java
          java(
            """
              package com.whatever;
                              
              class A {
                  void foo() {
                  }
              }
              """
          )
        );
    }

    @Test
    void folderCounts() {
        rewriteRun(
          spec -> {
              spec.dataTable(LanguageCompositionPerFile.Row.class, table -> {
                  assertThat(table).hasSize(3);
                  assertThat(table).containsOnlyOnce(
                    new LanguageCompositionPerFile.Row(separatorsToSystem("src/main/file.txt"), "Plain text", PlainText.class.getName(), 2, false),
                    new LanguageCompositionPerFile.Row(separatorsToSystem("src/resources/file.txt"), "Plain text", PlainText.class.getName(), 3, false),
                    new LanguageCompositionPerFile.Row(separatorsToSystem("file.txt"), "Plain text", PlainText.class.getName(), 4, false)
                  );
              });
              spec.dataTable(LanguageCompositionPerFolder.Row.class, table -> {
                  assertThat(table).hasSize(3);
                  assertThat(table).containsOnlyOnce(
                    new LanguageCompositionPerFolder.Row(separatorsToSystem("src/main"), "Plain text", 1, 2),
                    new LanguageCompositionPerFolder.Row(separatorsToSystem("src/resources"), "Plain text", 1, 3),
                    new LanguageCompositionPerFolder.Row(separatorsToSystem(""), "Plain text", 1, 4)
                  );
              });
          },
          dir("src",
            dir("main", textFileWithLineCount(2)),
            dir("resources", textFileWithLineCount(3))
          ),
          textFileWithLineCount(4)
        );
    }

    SourceSpecs textFileWithLineCount(int lineCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineCount; i++) {
            sb.append("line ").append(i).append("\n");
        }
        return text(sb.toString());
    }
}
