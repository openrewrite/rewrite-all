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
package org.openrewrite.json;

import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.json.tree.Json;
import org.openrewrite.marker.Markup;
import org.openrewrite.tree.ParseError;
import org.openrewrite.yaml.YamlParser;
import org.openrewrite.yaml.tree.Yaml;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

public class JsonToYamlConverterVisitor extends TreeVisitor<Tree, ExecutionContext> {

    private final YamlParser yamlParser = new YamlParser();
    private final JsonAsYamlPrinter<ExecutionContext> printer = new JsonAsYamlPrinter<>();

    @Override
    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
        return sourceFile instanceof Json.Document;
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof Json.Document) {
            Json.Document doc = (Json.Document) tree;
            PrintOutputCapture<ExecutionContext> p = new PrintOutputCapture<>(ctx);
            printer.visit(doc, p);
            SourceFile sourceFile = yamlParser.parse(p.getOut()).findFirst().orElse(null);
            if (sourceFile instanceof Yaml) {
                Path path = doc.getSourcePath();
                if (PathUtils.matchesGlob(path, "**.json")) {
                    path = Paths.get(path.toString().replaceAll("\\.json$", ".yaml"));
                }
                return sourceFile.withSourcePath(path).withMarkers(doc.getMarkers()).withId(doc.getId());
            } else if (sourceFile instanceof ParseError) {
                ParseError error = (ParseError) sourceFile;
                Optional<ParseExceptionResult> exceptionResult = error.getMarkers().findFirst(ParseExceptionResult.class);
                StringBuilder message = new StringBuilder();
                message.append("Something went wrong parsing the yaml value:\n");
                if (exceptionResult.isPresent()) {
                    message.append(exceptionResult.get().getMessage());
                    message.append("\n");
                }
                message
                        .append("\n\nThe input passed to the parser:\n")
                        .append(p.getOut());
                tree = Markup.warn(doc, new RuntimeException(message.toString()));
            }
        }
        return tree;
    }

    private static class JsonAsYamlPrinter<P> extends JsonIsoVisitor<PrintOutputCapture<P>> {
        private String calculatePrefix() {
            String prefix = "\n";
            int count = (int) getCursor().getParentTreeCursor().getPathAsStream(tree -> tree instanceof Json.JsonObject || tree instanceof Json.Array).count() - 1;
            if (getCursor().getValue() instanceof Json.Member && getCursor().getParentTreeCursor().getValue() instanceof Json.JsonObject) {
                Json.JsonObject object = getCursor().getParentTreeCursor().getValue();
                if (!object.getMembers().isEmpty() && getCursor().getValue() == object.getMembers().get(0)) {
                    Cursor maybeArray = getCursor().getParent(2);
                    if (count == 0 || maybeArray != null && maybeArray.getValue() instanceof Json.Array) {
                        return "";
                    }
                }
            }
            return prefix + StringUtils.repeat(" ", count * 2);
        }

        @Override
        public Json.Array visitArray(Json.Array array, PrintOutputCapture<P> p) {
            String prefix = calculatePrefix();
            array.getValues().forEach(value -> {
                p.append(prefix)
                        .append(' ')
                        .append(' ')
                        .append('-')
                        .append(' ');
                visit(value, p);
            });
            return array;
        }

        public Json.Member visitMember(Json.Member member, PrintOutputCapture<P> p) {
            p.append(calculatePrefix());
            visit(member.getKey(), p);
            p.append(':');
            if (!(member.getValue() instanceof Json.JsonObject || member.getValue() instanceof Json.Array)) {
                p.append(' ');
            }
            visit(member.getValue(), p);
            return member;
        }

        @Override
        public Json.Identifier visitIdentifier(Json.Identifier identifier, PrintOutputCapture<P> p) {
            p.append(identifier.getName());
            return identifier;
        }

        @Override
        public Json.Literal visitLiteral(Json.Literal literal, PrintOutputCapture<P> p) {
            // No quotes around yaml keys
            if (getCursor().getParentTreeCursor().getValue() instanceof Json.Member && ((Json.Member) getCursor().getParentTreeCursor().getValue()).getKey() == literal) {
                p.append(Objects.toString(literal.getValue()));
                return literal;
            }
            p.append(literal.getSource());
            return literal;
        }

        @Override
        public Json.Empty visitEmpty(Json.Empty empty, PrintOutputCapture<P> p) {
            p.append("null");
            return empty;
        }
    }
}
