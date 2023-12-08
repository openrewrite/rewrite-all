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

import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.table.DuplicateSourceFiles;

import java.nio.file.Path;
import java.util.*;

public class FindDuplicateSourceFiles extends ScanningRecipe<Map<Path, List<String>>> {
    transient DuplicateSourceFiles duplicateSourceFiles = new DuplicateSourceFiles(this);

    @Override
    public String getDisplayName() {
        return "Find duplicate source files";
    }

    @Override
    public String getDescription() {
        return "Record the presence of LSTs with duplicate paths, indicating that the same file was parsed more than once.";
    }

    @Override
    public Map<Path, List<String>> getInitialValue(ExecutionContext ctx) {
        return new HashMap<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Map<Path, List<String>> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile) {
                    acc.computeIfAbsent(((SourceFile) tree).getSourcePath(), k -> new ArrayList<>())
                            .add(tree.getClass().getSimpleName());
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Map<Path, List<String>> acc, ExecutionContext ctx) {
        for (Map.Entry<Path, List<String>> path : acc.entrySet()) {
            if (path.getValue().size() > 1) {
                duplicateSourceFiles.insertRow(ctx, new DuplicateSourceFiles.Row(
                        path.getKey().toString(),
                        path.getValue().size(),
                        new HashSet<>(path.getValue())
                ));
            }
        }
        return Collections.emptyList();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Map<Path, List<String>> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                assert tree instanceof SourceFile;
                SourceFile s = (SourceFile) tree;
                if(acc.containsKey(s.getSourcePath()) && acc.get(s.getSourcePath()).size() > 1) {
                    s = SearchResult.found(s);
                }
                return s;
            }
        };
    }
}
