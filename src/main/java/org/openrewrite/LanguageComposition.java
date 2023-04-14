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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.binary.Binary;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.json.tree.Json;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.protobuf.tree.Proto;
import org.openrewrite.python.tree.Py;
import org.openrewrite.quark.Quark;
import org.openrewrite.remote.Remote;
import org.openrewrite.table.LanguageCompositionPerFile;
import org.openrewrite.table.LanguageCompositionPerRepository;
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Value
@EqualsAndHashCode(callSuper = true)
public class LanguageComposition extends Recipe {

    transient LanguageCompositionPerRepository report = new LanguageCompositionPerRepository(this);
    transient LanguageCompositionPerFile perFileReport = new LanguageCompositionPerFile(this);

    @Override
    public String getDisplayName() {
        return "Language composition report";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Counts the number of lines of the various kinds of source code and data formats parsed by OpenRewrite. " +
               "Comments are not included in line counts. " +
               "This recipe emits its results as two data tables, making no changes to any source file. " +
               "One data table is per-file, the other is per-repository.";
    }

    @Override
    public List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        Map<String, Counts> map = new HashMap<>();
        Set<Integer> ids = new LinkedHashSet<>();
        for (SourceFile s : before) {
            AtomicBoolean hasParseFailure = new AtomicBoolean();
            new TreeVisitor<Tree, AtomicBoolean>() {
                @Override
                public Tree visit(@Nullable Tree tree, AtomicBoolean atomicBoolean) {
                    Tree t = super.visit(tree, atomicBoolean);
                    if (t != null && t.getMarkers().findFirst(ParseExceptionResult.class).isPresent()) {
                        atomicBoolean.set(true);
                    }
                    return t;
                }
            }.visit(s, hasParseFailure);
            if (s instanceof Quark || s instanceof Binary || s instanceof Remote) {
                Counts quarkCounts = map.computeIfAbsent("Other/unknown/unparseable", k -> new Counts());
                quarkCounts.fileCount++;
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Other/unknown/unparseable",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        0,
                        hasParseFailure.get()));
            } else if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.Cobol")) {
                Counts cobolCounts = map.computeIfAbsent("Cobol", k -> new Counts());
                cobolCounts.fileCount++;
                cobolCounts.lineCount += genericLineCount(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Cobol",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        cobolCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof K) {
                Counts kotlinCounts = map.computeIfAbsent("Kotlin", k -> new Counts());
                kotlinCounts.fileCount++;
                // Don't have a kotlin-specific counter yet and Java count should be very close
                kotlinCounts.lineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Kotlin",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        kotlinCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof G) {
                Counts groovyCounts = map.computeIfAbsent("Groovy", k -> new Counts());
                groovyCounts.fileCount++;
                groovyCounts.lineCount += org.openrewrite.groovy.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Groovy",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        groovyCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Py) {
                Counts pythonCounts = map.computeIfAbsent("Python", k -> new Counts());
                pythonCounts.fileCount++;
                pythonCounts.lineCount += genericLineCount(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Python",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        pythonCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof J) {
                Counts javaCounts = map.computeIfAbsent("Java", k -> new Counts());
                javaCounts.fileCount++;
                javaCounts.lineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Java",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        javaCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Json) {
                Counts jsonCounts = map.computeIfAbsent("Json", k -> new Counts());
                jsonCounts.fileCount++;
                jsonCounts.lineCount += org.openrewrite.json.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Json",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        jsonCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Hcl) {
                Counts hclCounts = map.computeIfAbsent("Hcl", k -> new Counts());
                hclCounts.fileCount++;
                hclCounts.lineCount += org.openrewrite.hcl.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Hcl",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        hclCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Properties) {
                Counts propertiesCounts = map.computeIfAbsent("Properties", k -> new Counts());
                propertiesCounts.fileCount++;
                propertiesCounts.lineCount += org.openrewrite.properties.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Properties",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        propertiesCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Proto) {
                Counts protobufCounts = map.computeIfAbsent("Protobuf", k -> new Counts());
                protobufCounts.fileCount++;
                protobufCounts.lineCount += org.openrewrite.protobuf.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Protobuf",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        protobufCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Xml) {
                Counts xmlCounts = map.computeIfAbsent("Xml", k -> new Counts());
                xmlCounts.fileCount++;
                xmlCounts.lineCount += org.openrewrite.xml.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Xml",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        xmlCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof Yaml) {
                Counts yamlCounts = map.computeIfAbsent("Yaml", k -> new Counts());
                yamlCounts.fileCount++;
                yamlCounts.lineCount += org.openrewrite.yaml.CountLinesVisitor.countLines(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Yaml",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        yamlCounts.lineCount,
                        hasParseFailure.get()));
            } else if (s instanceof PlainText) {
                Counts plainTextCounts = map.computeIfAbsent("Plain text", k -> new Counts());
                plainTextCounts.fileCount++;
                plainTextCounts.lineCount += genericLineCount(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Plain text",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        plainTextCounts.lineCount,
                        hasParseFailure.get()));
            } else {
                Counts unknownCounts = map.computeIfAbsent("Unknown", k -> new Counts());
                unknownCounts.fileCount++;
                unknownCounts.lineCount += genericLineCount(s);
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        "Unknown",
                        s.getClass().getName(),
                        s.getWeight(id -> ids.add(System.identityHashCode(id))),
                        unknownCounts.lineCount,
                        hasParseFailure.get()));
            }
        }
        for(Map.Entry<String, Counts> entry : map.entrySet()) {
            report.insertRow(ctx, new LanguageCompositionPerRepository.Row(entry.getKey(), entry.getValue().fileCount, entry.getValue().lineCount));
        }
        return before;
    }

    private static class Counts {
        int lineCount;
        int fileCount;
    }

    private static int genericLineCount(SourceFile s) {
        LineCounter counter = new LineCounter();
        s.printAll(counter);
        return counter.getLineCount();
    }

    private static class LineCounter extends PrintOutputCapture<Integer> {
        private int count;

        public LineCounter() {
            super(0);
        }

        @Override
        public PrintOutputCapture<Integer> append(char c) {
            if (c == '\n') {
                count++;
            }
            return this;
        }

        @Override
        public PrintOutputCapture<Integer> append(@Nullable String text) {
            if (text == null) {
                return this;
            }
            if (text.contains("\n")) {
                count += text.split("([\r\n]+)").length;
            }
            return this;
        }

        int getLineCount() {
            return count;
        }
    }
}
