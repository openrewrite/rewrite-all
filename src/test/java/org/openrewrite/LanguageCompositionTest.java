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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;
import org.openrewrite.python.tree.Py;
import org.openrewrite.table.LanguageCompositionPerFile;
import org.openrewrite.table.LanguageCompositionPerFolder;
import org.openrewrite.table.LanguageCompositionPerRepository;
import org.openrewrite.table.SourcesFileErrors;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;
import org.openrewrite.text.PlainText;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.PathUtils.separatorsToSystem;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.javascript.Assertions.javascript;
import static org.openrewrite.javascript.Assertions.jsx;
import static org.openrewrite.javascript.Assertions.tsx;
import static org.openrewrite.javascript.Assertions.typescript;
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
                  var hasJava = false;
                  var hasPlainText = false;
                  for (LanguageCompositionPerRepository.Row row : table) {
                      if ("Java".equals(row.getLanguage())) {
                          assertThat(row.getFileCount()).isOne();
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
                  var hasJava = false;
                  var hasPlainText = false;
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
                  assertThat(table)
                          .hasSize(2)
                          .contains(
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
    void javascriptAndTypescript() {
        rewriteRun(
          spec -> {
              spec.dataTable(LanguageCompositionPerRepository.Row.class, table -> {
                  assertThat(table).hasSize(2);
                  var hasJavaScript = false;
                  var hasTypescript = false;
                  for (LanguageCompositionPerRepository.Row row : table) {
                      if ("JavaScript".equals(row.getLanguage())) {
                          assertThat(row.getFileCount()).isEqualTo(3);
                          hasJavaScript = true;
                      } else if ("Typescript".equals(row.getLanguage())) {
                          assertThat(row.getFileCount()).isEqualTo(2);
                          hasTypescript = true;
                      }
                  }
                  assertThat(hasJavaScript).isTrue();
                  assertThat(hasTypescript).isTrue();
              });
              spec.dataTable(LanguageCompositionPerFile.Row.class, table -> {
                  assertThat(table).hasSize(5);
                  var hasJavaScript = false;
                  var hasTypescript = false;
                  for (LanguageCompositionPerFile.Row row : table) {
                      if ("JavaScript".equals(row.getLanguage())) {
                          hasJavaScript = true;
                      } else if ("Typescript".equals(row.getLanguage())) {
                          hasTypescript = true;
                      }
                  }
                  assertThat(hasJavaScript).isTrue();
                  assertThat(hasTypescript).isTrue();
              });
              spec.dataTable(LanguageCompositionPerFolder.Row.class, table -> {
                  assertThat(table)
                    .hasSize(2)
                    .contains(
                      new LanguageCompositionPerFolder.Row(separatorsToSystem("src/javascript"), "JavaScript", 3, 15),
                      new LanguageCompositionPerFolder.Row(separatorsToSystem("src/typescript"), "Typescript", 2, 14)
                    );
              });
          },
          dir("src/javascript",
            javascript(
              //language=javascript
              """
                console.log("Hello, world!");
                """,
              sourceSpecs -> sourceSpecs.path("example.js")
            ),
            jsx(
              //language=jsx
              """
                import React from 'react';

                // A minimal functional component
                const App = () => {
                    return (
                      <div>
                        <h1>Hello, World!</h1>
                        <p>This is a minimal JSX example.</p>
                      </div>
                    );
                };

                export default App;
                """,
              sourceSpecs -> sourceSpecs.path("example.jsx")
            ),
            javascript(
              //language=javascript
              """
                console.log("Hello, world!");
                """,
              sourceSpecs -> sourceSpecs.path("example.mjs")
            )
          ),
          dir("src/typescript",
            typescript(
              //language=typescript
              """
                console.log("Hello, world!");
                """,
              sourceSpecs -> sourceSpecs.path("example.ts")
            ),
            tsx(
              //language=tsx
              """
                import React from 'react';

                // A minimal functional component
                const App = () => {
                    return (
                      <div>
                        <h1>Hello, World!</h1>
                        <p>This is a minimal JSX example.</p>
                      </div>
                    );
                };

                export default App;
                """,
              sourceSpecs -> sourceSpecs.path("example.tsx")
            )
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
                  assertThat(table)
                          .hasSize(3)
                          .containsOnlyOnce(
                    new LanguageCompositionPerFile.Row(separatorsToSystem("src/main/file.txt"), "Plain text", PlainText.class.getName(), 2, false),
                    new LanguageCompositionPerFile.Row(separatorsToSystem("src/resources/file.txt"), "Plain text", PlainText.class.getName(), 3, false),
                    new LanguageCompositionPerFile.Row(separatorsToSystem("file.txt"), "Plain text", PlainText.class.getName(), 4, false)
                  );
              });
              spec.dataTable(LanguageCompositionPerFolder.Row.class, table -> {
                  assertThat(table)
                          .hasSize(3)
                          .containsOnlyOnce(
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

    @Test
    void successfulParseRemainsClassifiedWhenLineCountingFails() {
        // A Py$CompilationUnit is a successful Python parse. In production the Python LST is printed over RPC to
        // count lines; when that printing fails (e.g. the RPC worker is out of memory) the file must still be
        // reported as Python rather than rolled up under "Error". See customer-requests#2326.
        SourceFile pythonThatFailsToPrint = new ThrowingPy();
        RecipeRun run = new LanguageComposition().run(
          new InMemoryLargeSourceSet(List.of(pythonThatFailsToPrint)),
          new InMemoryExecutionContext());

        List<LanguageCompositionPerFile.Row> rows = run.getDataTableRows(LanguageCompositionPerFile.class);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getLanguage()).isEqualTo("Python");
        assertThat(rows.getFirst().getLinesOfText()).isZero();

        // The counting failure is not silently swallowed: it is recorded in the standard error table.
        List<SourcesFileErrors.Row> errors = run.getDataTableRows(SourcesFileErrors.class);
        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.getSourcePath()).isEqualTo("src/example.py");
            assertThat(error.getStackTrace()).contains("Simulated RPC printing failure for Python LST");
        });
    }

    /**
     * A successfully parsed Python source file whose printing throws, simulating an RPC printing failure.
     */
    @SuppressWarnings("unchecked")
    private static class ThrowingPy implements Py, SourceFile {
        private final UUID id = Tree.randomId();

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public <T extends Tree> T withId(UUID id) {
            return (T) this;
        }

        @Override
        public Markers getMarkers() {
            return Markers.EMPTY;
        }

        @Override
        public <T extends Tree> T withMarkers(Markers markers) {
            return (T) this;
        }

        @Override
        public <P> boolean isAcceptable(TreeVisitor<?, P> v, P p) {
            return true;
        }

        @Override
        public Space getPrefix() {
            return Space.EMPTY;
        }

        @Override
        public <J2 extends J> J2 withPrefix(Space space) {
            return (J2) this;
        }

        @Override
        public Path getSourcePath() {
            return Paths.get("src/example.py");
        }

        @Override
        public <T extends SourceFile> T withSourcePath(Path path) {
            return (T) this;
        }

        @Override
        public Charset getCharset() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public <T extends SourceFile> T withCharset(Charset charset) {
            return (T) this;
        }

        @Override
        public boolean isCharsetBomMarked() {
            return false;
        }

        @Override
        public <T extends SourceFile> T withCharsetBomMarked(boolean marked) {
            return (T) this;
        }

        @Override
        public @Nullable Checksum getChecksum() {
            return null;
        }

        @Override
        public <T extends SourceFile> T withChecksum(Checksum checksum) {
            return (T) this;
        }

        @Override
        public @Nullable FileAttributes getFileAttributes() {
            return null;
        }

        @Override
        public <T extends SourceFile> T withFileAttributes(FileAttributes fileAttributes) {
            return (T) this;
        }

        @Override
        public <P> String printAll(PrintOutputCapture<P> out) {
            throw new IllegalStateException("Simulated RPC printing failure for Python LST");
        }
    }

    SourceSpecs textFileWithLineCount(int lineCount) {
        var sb = new StringBuilder();
        for (var i = 0; i < lineCount; i++) {
            sb.append("line ").append(i).append("\n");
        }
        return text(sb.toString());
    }
}
