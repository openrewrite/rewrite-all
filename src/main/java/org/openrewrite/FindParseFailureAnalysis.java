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
package org.openrewrite;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.table.ParseFailureAnalysis;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindParseFailureAnalysis extends ScanningRecipe<FindParseFailureAnalysis.Accumulator> {
    transient ParseFailureAnalysis report = new ParseFailureAnalysis(this);

    @Option(displayName = "Mark source files",
            description = "Adds a `SearchResult` marker to LST elements that resulted in a parser exception.",
            required = false)
    @Nullable
    Boolean markFailures;

    @Option(displayName = "`ParseExceptionAnalysis.errorName`",
            description = "Limits the marked results to a specific node or exception name.",
            required = false)
    @Nullable
    String errorName;

    @Override
    public String getDisplayName() {
        return "Parser exception report";
    }

    @Override
    public String getDescription() {
        return "Collects a count of ParseExceptionResults per `Tree`. Optionally, marks each `J.Unknown` to view the unparsed code.";
    }

    @Data
    static class Accumulator {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        List<SourceFile> results = new ArrayList<>();
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile s = (SourceFile) tree;
                if (Boolean.TRUE.equals(markFailures)) {
                    acc.results.add((SourceFile) new AnalysisVisitor(s, acc.getCounts(), markFailures, errorName).visit(s, ctx));
                } else {
                    new AnalysisVisitor(s, acc.getCounts(), markFailures, errorName).visit(s, ctx);
                }
                return s;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        for (Map.Entry<String, Map<String, Integer>> fileExtensionEntries : acc.getCounts().entrySet()) {
            for (Map.Entry<String, Integer> nodeTypeCounts : fileExtensionEntries.getValue().entrySet()) {
                report.insertRow(ctx, new ParseFailureAnalysis.Row(fileExtensionEntries.getKey(), nodeTypeCounts.getKey(), nodeTypeCounts.getValue()));
            }
        }
        return acc.results;
    }

    private static class AnalysisVisitor extends TreeVisitor<Tree, ExecutionContext> {
        private final Map<String, Map<String, Integer>> counts;
        private final Set<String> ids = new HashSet<>();
        private final boolean markFailures;

        @Nullable
        private final String markNodeType;

        private final String extension;

        public AnalysisVisitor(SourceFile source, Map<String, Map<String, Integer>> counts, @Nullable Boolean markFailures, @Nullable String markNodeType) {
            this.counts = counts;
            this.markFailures = Boolean.TRUE.equals(markFailures);
            this.markNodeType = markNodeType;

            this.extension = source.getSourcePath().toString().substring(source.getSourcePath().toString().lastIndexOf(".") + 1);
        }

        @Nullable
        @Override
        public Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
            if (tree == null) {
                return null;
            }

            Tree t = super.visit(tree, ctx);
            if (t != null) {
                ParseExceptionResult result = t.getMarkers().findFirst(ParseExceptionResult.class).orElse(null);
                if (result != null) {
                    if (ParseFailureAnalysis.containsMessage(result.getMessage())) {
                        String name = ParseFailureAnalysis.getErrorName(result.getMessage());
                        if (markFailures) {
                            if ((markNodeType == null || markNodeType.equals(name))) {
                                if (ids.add(result.getId().toString())) {
                                    t = SearchResult.found(t);
                                }
                            }
                        }
                        Map<String, Integer> nodeTypeCounts = counts.computeIfAbsent(extension, k -> new HashMap<>());
                        nodeTypeCounts.merge(name, 1, Integer::sum);
                    }
                }
            }
            return t;
        }
    }
}
