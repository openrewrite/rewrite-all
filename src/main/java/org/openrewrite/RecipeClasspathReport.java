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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.config.Environment;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.table.RecipeClasspathRow;

import java.net.URL;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = false)
@Value
public class RecipeClasspathReport extends ScanningRecipe<RecipeClasspathReport.Accumulator> {

    @Option(displayName = "Recipe name glob",
            description = "A glob pattern to filter recipes by their fully qualified name. " +
                    "For example, `org.openrewrite.java.*` will only include recipes in that package.",
            required = false)
    @Nullable
    String recipeNameGlob;

    transient RecipeClasspathRow recipeClasspath = new RecipeClasspathRow(this);

    private static final Pattern VERSION_PATTERN = Pattern.compile("-(\\d+\\.\\d+[^/]*?)\\.jar$");

    @Override
    public String getDisplayName() {
        return "Recipe classpath report";
    }

    @Override
    public String getDescription() {
        return "Lists all recipes available on the classpath with their origin, version, and JAR path. " +
                "Useful for debugging recipe loading and version conflicts.";
    }

    public static class Accumulator {
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return TreeVisitor.noop();
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Environment env = Environment.builder()
                .scanRuntimeClasspath()
                .build();

        for (Recipe recipe : env.listRecipes()) {
            if (recipeNameGlob != null && !StringUtils.matchesGlob(recipe.getName(), recipeNameGlob)) {
                continue;
            }
            Class<?> recipeClass = recipe.getClass();
            String jarPath = "";
            String version = "";

            try {
                ProtectionDomain pd = recipeClass.getProtectionDomain();
                if (pd != null) {
                    CodeSource cs = pd.getCodeSource();
                    if (cs != null) {
                        URL location = cs.getLocation();
                        if (location != null) {
                            try {
                                jarPath = Paths.get(location.toURI()).toString();
                            } catch (Exception e) {
                                jarPath = location.getPath();
                            }
                            Matcher m = VERSION_PATTERN.matcher(jarPath);
                            if (m.find()) {
                                version = m.group(1);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            recipeClasspath.insertRow(ctx, new RecipeClasspathRow.Row(
                    recipe.getName(),
                    recipe.getDisplayName(),
                    jarPath,
                    version
            ));
        }
        return emptyList();
    }
}
