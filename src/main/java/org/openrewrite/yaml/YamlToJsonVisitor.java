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
package org.openrewrite.yaml;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.json.JsonParser;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markup;
import org.openrewrite.tree.ParseError;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;

public class YamlToJsonVisitor extends TreeVisitor<Tree, ExecutionContext> {

    private final JsonParser jsonParser = new JsonParser();
    private final YamlAsJsonPrinter<ExecutionContext> printer = new YamlAsJsonPrinter<>();

    @Override
    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
        return sourceFile instanceof Yaml.Documents;
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof Yaml.Documents) {
            Yaml.Documents doc = (Yaml.Documents) tree;
            String json = printer.reduce(doc, new PrintOutputCapture<>(ctx)).getOut();
            SourceFile sourceFile = jsonParser.parse(json).findFirst().orElse(null);
            if (sourceFile instanceof Json.Document) {
                Path path = doc.getSourcePath();
                if (PathUtils.matchesGlob(path, "**.yaml") || PathUtils.matchesGlob(path, "**.yml")) {
                    path = Paths.get(path.toString().replaceAll("\\.ya?ml$", ".json"));
                }
                return sourceFile.withSourcePath(path).withMarkers(doc.getMarkers()).withId(doc.getId());
            }
            if (sourceFile instanceof ParseError) {
                StringBuilder message = new StringBuilder("Something went wrong parsing the json value:\n");
                sourceFile.getMarkers().findFirst(ParseExceptionResult.class)
                        .ifPresent(result -> message.append(result.getMessage()).append("\n"));
                message.append("\n\nThe input passed to the parser:\n").append(json);
                return Markup.warn(doc, new RuntimeException(message.toString()));
            }
        }
        return tree;
    }

    private static class YamlAsJsonPrinter<P> extends YamlIsoVisitor<PrintOutputCapture<P>> {

        private int getIndentLevel() {
            return (int) getCursor().getPathAsStream(tree ->
                    tree instanceof Yaml.Mapping || tree instanceof Yaml.Sequence
            ).count();
        }

        private String getIndent() {
            return StringUtils.repeat("  ", getIndentLevel());
        }

        @Override
        public Yaml.Documents visitDocuments(Yaml.Documents documents, PrintOutputCapture<P> p) {
            if (!documents.getDocuments().isEmpty()) {
                visit(documents.getDocuments().get(0).getBlock(), p);
            }
            return documents;
        }

        @Override
        public Yaml.Mapping visitMapping(Yaml.Mapping mapping, PrintOutputCapture<P> p) {
            p.append("{");
            boolean first = true;
            for (Yaml.Mapping.Entry entry : mapping.getEntries()) {
                if (!first) {
                    p.append(",");
                }
                first = false;
                p.append("\n").append(getIndent());
                visit(entry, p);
            }
            if (!mapping.getEntries().isEmpty()) {
                p.append("\n").append(StringUtils.repeat("  ", getIndentLevel() - 1));
            }
            p.append("}");
            return mapping;
        }

        @Override
        public Yaml.Mapping.Entry visitMappingEntry(Yaml.Mapping.Entry entry, PrintOutputCapture<P> p) {
            visit(entry.getKey(), p);
            p.append(": ");
            visit(entry.getValue(), p);
            return entry;
        }

        @Override
        public Yaml.Sequence visitSequence(Yaml.Sequence sequence, PrintOutputCapture<P> p) {
            p.append("[");
            boolean first = true;
            for (Yaml.Sequence.Entry entry : sequence.getEntries()) {
                if (!first) {
                    p.append(",");
                }
                first = false;
                p.append("\n").append(getIndent());
                visit(entry.getBlock(), p);
            }
            if (!sequence.getEntries().isEmpty()) {
                p.append("\n").append(StringUtils.repeat("  ", getIndentLevel() - 1));
            }
            p.append("]");
            return sequence;
        }

        @Override
        public Yaml.Scalar visitScalar(Yaml.Scalar scalar, PrintOutputCapture<P> p) {
            String value = scalar.getValue();

            // Handle null values
            if ("null".equals(value) || value.isEmpty()) {
                p.append("null");
                return scalar;
            }

            // Handle booleans
            if ("true".equals(value) || "false".equals(value)) {
                p.append(value);
                return scalar;
            }

            // Try to parse as number
            try {
                if (value.contains(".")) {
                    Double.parseDouble(value);
                    p.append(value);
                    return scalar;
                }
                Long.parseLong(value);
                p.append(value);
                return scalar;
            } catch (NumberFormatException e) {
                // Not a number, treat as string
            }

            // String value - always quote
            p.append("\"");
            p.append(value.replace("\\", "\\\\").replace("\"", "\\\""));
            p.append("\"");
            return scalar;
        }
    }
}
