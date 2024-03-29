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
import org.openrewrite.table.DuplicateSourceFiles;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.properties.Assertions.properties;
import static org.openrewrite.test.SourceSpecs.text;

class FindDuplicateSourceFilesTest implements RewriteTest {

    @DocumentExample
    @Test
    void findDuplicates() {
        rewriteRun(
          spec -> spec.recipe(new FindDuplicateSourceFiles())
            .dataTable(DuplicateSourceFiles.Row.class, rows ->
              assertThat(rows).hasSize(1)),
          text(
            "hello=world",
            "~~>hello=world",
            spec -> spec.path("hello.properties")
          ),
          properties(
            "hello=world",
            "~~>hello=world",
            spec -> spec.path("hello.properties")
          )
        );
    }

    @Test
    void noDupes() {
        rewriteRun(
          spec -> spec.recipe(new FindDuplicateSourceFiles()),
          text(
            "hello=world",
            spec -> spec.path("hello.properties")
          )
        );
    }
}
