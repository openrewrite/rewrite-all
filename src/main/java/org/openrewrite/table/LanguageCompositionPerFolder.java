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

public class LanguageCompositionPerFolder extends DataTable<LanguageCompositionPerFolder.Row> {

    public LanguageCompositionPerFolder(Recipe recipe) {
        super(recipe, "Per-file language composition report",
                "A list of individual files and their language composition.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Folder path",
                description = "The path to the folder relative to repository root.")
        String folderPath;

        @Column(displayName = "Language",
                description = "Language of the source file.")
        String language;

        @Column(displayName = "File count",
                description = "Count of files of this language.")
        int fileCount;

        @Column(displayName = "Line count",
                description = "Count of lines of this language.")
        int lineCount;
    }
}
