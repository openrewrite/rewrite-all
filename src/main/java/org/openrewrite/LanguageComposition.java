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

import lombok.Data;
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
import org.openrewrite.table.LanguageCompositionPerFolder;
import org.openrewrite.table.LanguageCompositionPerRepository;
import org.openrewrite.text.PlainText;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.tree.Yaml;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = false)
public class LanguageComposition extends ScanningRecipe<LanguageComposition.Accumulator> {

    transient LanguageCompositionPerRepository perRepositoryReport = new LanguageCompositionPerRepository(this);
    transient LanguageCompositionPerFolder perFolderReport = new LanguageCompositionPerFolder(this);
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

    @Data
    public static class Accumulator {
        Map<String, Map<String, Counts>> folderToLanguageToCounts = new HashMap<>();
    }

    private static String containingFolderPath(SourceFile s) {
        String sourcePath = PathUtils.separatorsToUnix(s.getSourcePath().toString());
        int lastSlash = sourcePath.lastIndexOf('/');
        if (lastSlash == -1) {
            return "";
        }
        return s.getSourcePath().toString().substring(0, lastSlash);
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        return new Accumulator();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }

                SourceFile s = (SourceFile) tree;
                String folderPath = containingFolderPath(s);
                // Parse failures *should* only ever appear on PlainText sources, but always checking finds a parser bug
                boolean hasParseFailure = s.getMarkers().findFirst(ParseExceptionResult.class).isPresent();
                if (s instanceof Quark || s instanceof Binary || s instanceof Remote) {
                    Counts quarkCounts = acc.getFolderToLanguageToCounts()
                            .computeIfAbsent(folderPath, k -> new HashMap<>())
                            .computeIfAbsent("Other/unknown/unparseable", k -> new Counts());
                    quarkCounts.fileCount++;
                    perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                            s.getSourcePath().toString(),
                            "Other/unknown/unparseable",
                            s.getClass().getName(),
                            0,
                            hasParseFailure));
                } else {
                    int genericLineCount = genericLineCount(s);
                    if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.CobolPreprocessor$Copybook")) {
                        Counts copybookCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Copybook", k -> new Counts());
                        copybookCounts.fileCount++;
                        copybookCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Copybook",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.Cobol")) {
                        Counts cobolCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Cobol", k -> new Counts());
                        cobolCounts.fileCount++;
                        cobolCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Cobol",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof K) {
                        Counts kotlinCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Kotlin", k -> new Counts());
                        kotlinCounts.fileCount++;
                        // Don't have a kotlin-specific counter yet and Java count should be very close
                        kotlinCounts.lineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Kotlin",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof G) {
                        Counts groovyCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Groovy", k -> new Counts());
                        groovyCounts.fileCount++;
                        groovyCounts.lineCount += org.openrewrite.groovy.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Groovy",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Py) {
                        Counts pythonCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Python", k -> new Counts());
                        pythonCounts.fileCount++;
                        pythonCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Python",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof J) {
                        Counts javaCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Java", k -> new Counts());
                        javaCounts.fileCount++;
                        javaCounts.lineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Java",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Json) {
                        Counts jsonCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Json", k -> new Counts());
                        jsonCounts.fileCount++;
                        jsonCounts.lineCount += org.openrewrite.json.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Json",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Hcl) {
                        Counts hclCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Hcl", k -> new Counts());
                        hclCounts.fileCount++;
                        hclCounts.lineCount += org.openrewrite.hcl.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Hcl",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Properties) {
                        Counts propertiesCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Properties", k -> new Counts());
                        propertiesCounts.fileCount++;
                        propertiesCounts.lineCount += org.openrewrite.properties.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Properties",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Proto) {
                        Counts protobufCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Protobuf", k -> new Counts());
                        protobufCounts.fileCount++;
                        protobufCounts.lineCount += org.openrewrite.protobuf.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Protobuf",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Xml) {
                        Counts xmlCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Xml", k -> new Counts());
                        xmlCounts.fileCount++;
                        xmlCounts.lineCount += org.openrewrite.xml.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Xml",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof Yaml) {
                        Counts yamlCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Yaml", k -> new Counts());
                        yamlCounts.fileCount++;
                        yamlCounts.lineCount += org.openrewrite.yaml.CountLinesVisitor.countLines(s);
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Yaml",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof PlainText) {
                        Counts plainTextCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Plain text", k -> new Counts());
                        plainTextCounts.fileCount++;
                        plainTextCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Plain text",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else if (s instanceof ParseError) {
                        Counts parseErrorCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Parse error", k -> new Counts());
                        parseErrorCounts.fileCount++;
                        parseErrorCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Parse error",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    } else {
                        Counts unknownCounts = acc.getFolderToLanguageToCounts()
                                .computeIfAbsent(folderPath, k -> new HashMap<>())
                                .computeIfAbsent("Unknown", k -> new Counts());
                        unknownCounts.fileCount++;
                        unknownCounts.lineCount += genericLineCount;
                        perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                                s.getSourcePath().toString(),
                                "Unknown",
                                s.getClass().getName(),
                                genericLineCount,
                                hasParseFailure));
                    }
                }
                return tree;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Accumulator acc, ExecutionContext ctx) {
        Map<String, Counts> languageToCount = new HashMap<>();
        for (Map.Entry<String, Map<String, Counts>> entry : acc.getFolderToLanguageToCounts().entrySet()) {
            for (Map.Entry<String, Counts> languageEntry : entry.getValue().entrySet()) {
                perFolderReport.insertRow(ctx, new LanguageCompositionPerFolder.Row(entry.getKey(),
                        languageEntry.getKey(), languageEntry.getValue().fileCount, languageEntry.getValue().lineCount));

                Counts counts = languageToCount.computeIfAbsent(languageEntry.getKey(), k -> new Counts());
                counts.fileCount += languageEntry.getValue().fileCount;
                counts.lineCount += languageEntry.getValue().lineCount;
            }
        }
        for (Map.Entry<String, Counts> entry : languageToCount.entrySet()) {
            perRepositoryReport.insertRow(ctx, new LanguageCompositionPerRepository.Row(entry.getKey(), entry.getValue().fileCount, entry.getValue().lineCount));
        }

        return Collections.emptyList();
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
