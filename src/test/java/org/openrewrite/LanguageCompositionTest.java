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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.test.SourceSpecs.text;

public class LanguageCompositionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LanguageComposition());
    }

    @Test
    void countsJava() {
        rewriteRun(
            spec -> {
                spec.dataTable(LanguageCompositionReport.Row.class, table -> {
                    assertThat(table).hasSize(2);
                    boolean hasJava = false;
                    boolean hasPlainText = false;
                    for (LanguageCompositionReport.Row row : table) {
                        if(row.getLanguage().equals("Java")) {
                            assertThat(row.getFileCount()).isEqualTo(1);
                            assertThat(row.getLineCount()).isEqualTo(3);
                            hasJava = true;
                        } else if (row.getLanguage().equals("Plain text")) {
                            assertThat(row.getFileCount()).isEqualTo(1);
                            assertThat(row.getLineCount()).isGreaterThan(0);
                            hasPlainText = true;
                        }
                    }
                    assertThat(hasJava).isTrue();
                    assertThat(hasPlainText).isTrue();
                });
                spec.dataTable(org.openrewrite.table.LanguageComposition.Row.class, table -> {
                    assertThat(table).hasSize(2);
                    boolean hasJava = false;
                    boolean hasPlainText = false;
                    for(org.openrewrite.table.LanguageComposition.Row row : table) {
                        if (row.getLanguage().equals("Java")) {
                            assertThat(row.getWeight()).isGreaterThan(0);
                            assertThat(row.getLinesOfText()).isEqualTo(3);
                            hasJava = true;
                        } else if (row.getLanguage().equals("Plain text")){
                            assertThat(row.getWeight()).isGreaterThan(0);
                            assertThat(row.getLinesOfText()).isEqualTo(2);
                            hasPlainText = true;
                        }
                    }
                    assertThat(hasJava).isTrue();
                    assertThat(hasPlainText).isTrue();
                });
            },
            //language=java
            java("""
                package com.whatever;
                
                class A {
                    void foo() {
                    }
                }
                """),
            text("""
                hello
                world
                
                """)

        );
    }
}
