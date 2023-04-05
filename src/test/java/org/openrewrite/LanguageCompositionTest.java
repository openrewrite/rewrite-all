package org.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.java;

public class LanguageCompositionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new LanguageComposition());
    }

    @Test
    void countsJava() {
        rewriteRun(
            spec -> spec.dataTable(LanguageCompositionReport.Row.class, table -> {
                assertThat(table).hasSize(1);
                LanguageCompositionReport.Row row = table.get(0);
                assertThat(row.getJavaLineCount()).isEqualTo(3);
                assertThat(row.getJavaFileCount()).isEqualTo(1);
            }),
            //language=java
            java("""
                package com.whatever;
                
                class A {
                    void foo() {
                    }
                }
                """)
        );
    }
}
