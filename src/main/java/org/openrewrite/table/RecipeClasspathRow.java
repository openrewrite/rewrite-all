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
package org.openrewrite.table;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

@JsonIgnoreType
public class RecipeClasspathRow extends DataTable<RecipeClasspathRow.Row> {

    public RecipeClasspathRow(Recipe recipe) {
        super(recipe,
                "Recipe classpath report",
                "Lists all recipes available on the classpath with their origin, version, and JAR path.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Recipe name",
                description = "Fully qualified name of the recipe.")
        String recipeName;

        @Column(displayName = "Display name",
                description = "Human-readable display name of the recipe.")
        String displayName;

        @Column(displayName = "Origin",
                description = "Code source location (JAR or directory) the recipe was loaded from.")
        String origin;

        @Column(displayName = "Version",
                description = "Version of the artifact containing the recipe, if determinable.")
        String version;

        @Column(displayName = "JAR path",
                description = "Full filesystem path to the JAR containing the recipe.")
        String jarPath;
    }
}
