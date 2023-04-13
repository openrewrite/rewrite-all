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
package org.openrewrite.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class LanguageCompositionPerRepository extends DataTable<LanguageCompositionPerRepository.Row> {

    public LanguageCompositionPerRepository(Recipe recipe) {
        super(recipe,
                "Language composition report",
                "Counts the number of files and lines of source code in the various formats OpenRewrite knows how to parse.");
    }

    @Value
    public static class Row {
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
