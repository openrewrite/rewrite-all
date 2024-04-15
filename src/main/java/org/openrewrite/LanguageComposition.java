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

import lombok.AllArgsConstructor;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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

    @Value
    @AllArgsConstructor
    public static class FileComposition {
        String language;
        int fileLineCount;
        int languageLineCount;

        public FileComposition(String language, int fileLineCount) {
            this.language = language;
            this.fileLineCount = fileLineCount;
            this.languageLineCount = fileLineCount;
        }
    }

    public static FileComposition determineFileComposition(SourceFile s) {
        if (s instanceof Quark || s instanceof Binary || s instanceof Remote) {
            return new FileComposition("Other/unknown/unparseable", 0);
        }
        int fileLineCount = fileLineCount(s);
        if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.CobolPreprocessor$Copybook")) {
            return new FileComposition("Copybook", fileLineCount);
        }
        if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.Cobol")) {
            return new FileComposition("Cobol", fileLineCount);
        }
        if (s instanceof K) {
            int languageLineCount = org.openrewrite.java.CountLinesVisitor.countLines(s);
            return new FileComposition("Kotlin", fileLineCount, languageLineCount);
        }
        if (s instanceof G) {
            int languageLineCount = org.openrewrite.groovy.CountLinesVisitor.countLines(s);
            return new FileComposition("Groovy", fileLineCount, languageLineCount);
        }
        if (s instanceof Py) {
            return new FileComposition("Python", fileLineCount);
        }
        if (s instanceof J) {
            int languageLineCount = org.openrewrite.java.CountLinesVisitor.countLines(s);
            return new FileComposition("Java", fileLineCount, languageLineCount);
        }
        if (s instanceof Json) {
            int languageLineCount = org.openrewrite.json.CountLinesVisitor.countLines(s);
            return new FileComposition("Json", fileLineCount, languageLineCount);
        }
        if (s instanceof Hcl) {
            int languageLineCount = org.openrewrite.hcl.CountLinesVisitor.countLines(s);
            return new FileComposition("Hcl", fileLineCount, languageLineCount);
        }
        if (s instanceof Properties) {
            int languageLineCount = org.openrewrite.properties.CountLinesVisitor.countLines(s);
            return new FileComposition("Properties", fileLineCount, languageLineCount);
        }
        if (s instanceof Proto) {
            int languageLineCount = org.openrewrite.protobuf.CountLinesVisitor.countLines(s);
            return new FileComposition("Protobuf", fileLineCount, languageLineCount);
        }
        if (s instanceof Xml) {
            int languageLineCount = org.openrewrite.xml.CountLinesVisitor.countLines(s);
            return new FileComposition("Xml", fileLineCount, languageLineCount);
        }
        if (s instanceof Yaml) {
            int languageLineCount = org.openrewrite.yaml.CountLinesVisitor.countLines(s);
            return new FileComposition("Yaml", fileLineCount, languageLineCount);
        }
        if (s instanceof PlainText) {
            String language = determinePlainTextType(s);
            return new FileComposition(language, fileLineCount);
        }
        if (s instanceof ParseError) {
            return new FileComposition("Parse error", fileLineCount);
        }
        return new FileComposition("Unknown", fileLineCount);
    }

    public static String determinePlainTextType(SourceFile s) {
        if (s.getSourcePath().endsWith(".js") || s.getSourcePath().endsWith(".jsx") || s.getSourcePath().endsWith(".mjs")) {
            return "JavaScript";
        }
        if (s.getSourcePath().endsWith(".ts") || s.getSourcePath().endsWith(".tsx")) {
            return "Typescript";
        }
        if (s.getSourcePath().endsWith(".py")) {
            return "Python";
        }
        return "Plain text";
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
                FileComposition fileComposition = determineFileComposition(s);
                Counts counts = acc.getFolderToLanguageToCounts()
                        .computeIfAbsent(folderPath, k -> new HashMap<>())
                        .computeIfAbsent(fileComposition.language, k -> new Counts());
                counts.fileCount++;
                counts.lineCount += fileComposition.languageLineCount;
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        fileComposition.language,
                        s.getClass().getName(),
                        fileComposition.fileLineCount,
                        hasParseFailure));
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

    private static int fileLineCount(SourceFile s) {
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
