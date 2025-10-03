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

public class JsonToYamlVisitor extends TreeVisitor<Tree, ExecutionContext> {
    @Override
    public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
        return sourceFile instanceof Json.Document;
    }

    @Override
    public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
        if (tree instanceof Json.Document) {
            Json.Document doc = (Json.Document) tree;
            String yaml = new JsonAsYamlPrinter<ExecutionContext>().reduce(doc, new PrintOutputCapture<>(ctx)).getOut();
            SourceFile sourceFile = new YamlParser().parse(yaml).findFirst().orElse(null);
            if (sourceFile instanceof Yaml) {
                Path path = doc.getSourcePath();
                if (PathUtils.matchesGlob(path, "**.json")) {
                    path = Paths.get(path.toString().replaceAll("\\.json$", ".yaml"));
                }
                return sourceFile
                        .withSourcePath(path)
                        .withMarkers(doc.getMarkers())
                        .withId(doc.getId());
            }
            if (sourceFile instanceof ParseError) {
                StringBuilder message = new StringBuilder("Something went wrong parsing the yaml value:\n");
                sourceFile.getMarkers().findFirst(ParseExceptionResult.class)
                        .ifPresent(parseExceptionResult -> message.append(parseExceptionResult.getMessage()).append("\n"));
                message.append("\n\nThe input passed to the parser:\n").append(yaml);
                return Markup.warn(doc, new RuntimeException(message.toString()));
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
                p.append(prefix).append("  - ");
                visit(value, p);
            });
            return array;
        }

        @Override
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
            Json value = getCursor().getParentTreeCursor().getValue();
            if (value instanceof Json.Member && ((Json.Member) value).getKey() == literal) {
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
