/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.table;

import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

import java.util.List;
import java.util.Set;

public class DuplicateSourceFiles extends DataTable<DuplicateSourceFiles.Row> {

    public DuplicateSourceFiles(Recipe recipe) {
        super(recipe,
                "Duplicate source files",
                "A list of source files that occur more than once in an LST.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file.")
        String sourcePath;

        @Column(displayName = "Count",
                description = "The number of times an LST element with this source file path is present.")
        int count;

        @Column(displayName = "Types",
                description = "The LST types of the source file.")
        Set<String> types;
    }
}
