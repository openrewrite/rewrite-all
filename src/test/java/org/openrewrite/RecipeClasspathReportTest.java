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
package org.openrewrite;

import org.junit.jupiter.api.Test;
import org.openrewrite.table.RecipeClasspathRow;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.test.SourceSpecs.text;

class RecipeClasspathReportTest implements RewriteTest {

    @DocumentExample
    @Test
    void listsRecipesOnClasspath() {
        rewriteRun(
          spec -> spec.recipe(new RecipeClasspathReport())
            .dataTable(RecipeClasspathRow.Row.class, rows -> {
                assertThat(rows).isNotEmpty();
                assertThat(rows).anyMatch(row ->
                  row.getRecipeName().equals("org.openrewrite.RecipeClasspathReport"));
                // Every row should have a non-blank recipe name and display name
                assertThat(rows).allSatisfy(row -> {
                    assertThat(row.getRecipeName()).isNotBlank();
                    assertThat(row.getDisplayName()).isNotBlank();
                });
            }),
          // Provide a source file so the recipe run is triggered
          text("hello")
        );
    }
}
