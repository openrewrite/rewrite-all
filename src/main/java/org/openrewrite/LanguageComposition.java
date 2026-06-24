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
import org.jspecify.annotations.Nullable;
import org.openrewrite.binary.Binary;
import org.openrewrite.internal.ExceptionUtils;
import org.openrewrite.cobol.tree.CobolPreprocessor;
import org.openrewrite.controlm.tree.ControlM;
import org.openrewrite.csharp.tree.Cs;
import org.openrewrite.docker.tree.Docker;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.hcl.tree.Hcl;
import org.openrewrite.java.tree.J;
import org.openrewrite.javascript.tree.JS;
import org.openrewrite.jcl.tree.Jcl;
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
import org.openrewrite.table.SourcesFileErrors;
import org.openrewrite.text.PlainText;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.tree.ParseError;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.tree.Yaml;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

import static java.util.Collections.emptyList;

@EqualsAndHashCode(callSuper = false)
@Value
public class LanguageComposition extends ScanningRecipe<LanguageComposition.Accumulator> {

    private static final String OTHER = "Other/unknown/unparseable";

    transient LanguageCompositionPerRepository perRepositoryReport = new LanguageCompositionPerRepository(this);
    transient LanguageCompositionPerFolder perFolderReport = new LanguageCompositionPerFolder(this);
    transient LanguageCompositionPerFile perFileReport = new LanguageCompositionPerFile(this);
    transient SourcesFileErrors errorsTable = new SourcesFileErrors(this);

    String displayName = "Language composition report";

    String description = "Counts the number of lines of the various kinds of source code and data formats parsed by OpenRewrite. " +
            "Comments are not included in line counts. " +
            "This recipe emits its results as two data tables, making no changes to any source file. " +
            "One data table is per-file, the other is per-repository.";

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

                String language;
                try {
                    language = language(s);
                } catch (RuntimeException | Error e) {
                    // Classification itself failed, e.g. a NoClassDefFoundError when a language module is not on
                    // the classpath. Record the file as "Error" so it still appears in the reports, then rethrow
                    // so the failure also surfaces in the Error data table.
                    Counts errorCounts = acc.getFolderToLanguageToCounts()
                            .computeIfAbsent(folderPath, k -> new HashMap<>())
                            .computeIfAbsent("Error", k -> new Counts());
                    errorCounts.fileCount++;
                    perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                            s.getSourcePath().toString(),
                            "Error",
                            s.getClass().getName(),
                            0,
                            false));
                    throw e;
                }
                if (language == null) {
                    // A JavaScript/TypeScript source with an extension we don't report on
                    return tree;
                }

                // Counting lines prints the LST, which for some languages (e.g. Python) happens over RPC and can
                // fail independently of a successful parse. A counting failure must not reclassify the file as
                // "Error"; it remains classified by its language with a line count of zero, while the failure is
                // recorded in the standard error table so it stays diagnosable.
                int linesOfText = OTHER.equals(language) ? 0 : safeLineCount(s, ctx, () -> LineCounter.count(s));
                int languageLineCount = OTHER.equals(language) ? 0 : safeLineCount(s, ctx, () -> codeLineCount(s, language, linesOfText));

                Counts counts = acc.getFolderToLanguageToCounts()
                        .computeIfAbsent(folderPath, k -> new HashMap<>())
                        .computeIfAbsent(language, k -> new Counts());
                counts.fileCount++;
                counts.lineCount += languageLineCount;
                perFileReport.insertRow(ctx, new LanguageCompositionPerFile.Row(
                        s.getSourcePath().toString(),
                        language,
                        s.getClass().getName(),
                        linesOfText,
                        hasParseFailure));
                return tree;
            }
        };
    }

    /**
     * Classify a source file by language. This relies only on cheap type checks so that, unlike line counting,
     * it does not fail for a successfully parsed file. Returns {@code null} for JavaScript/TypeScript sources
     * whose extension we don't report on. May throw a {@link NoClassDefFoundError} when a language module is not
     * on the classpath, in which case the caller records the file as "Error".
     */
    private static @Nullable String language(SourceFile s) {
        if (s instanceof Quark || s instanceof Binary || s instanceof Remote) {
            return OTHER;
        } else if (s instanceof CobolPreprocessor.Copybook) {
            return "Copybook";
        } else if (s.getClass().getName().startsWith("org.openrewrite.cobol.tree.Cobol")) { // Also CobolPreprocessor
            return "Cobol";
        } else if (s instanceof ControlM) {
            return "Control-M";
        } else if (s instanceof Docker) {
            return "Docker";
        } else if (s instanceof Jcl) {
            return "JCL";
        } else if (s instanceof K) {
            return "Kotlin";
        } else if (s instanceof G) {
            return "Groovy";
        } else if (s instanceof Py) {
            return "Python";
        } else if (s instanceof Cs.CompilationUnit) {
            return "C#";
        } else if (s instanceof JS) {
            String sourcePath = s.getSourcePath().toString();
            if (sourcePath.endsWith(".js") || sourcePath.endsWith(".jsx") || sourcePath.endsWith(".mjs")) {
                return "JavaScript";
            } else if (sourcePath.endsWith(".ts") || sourcePath.endsWith(".tsx")) {
                return "Typescript";
            }
            return null;
        } else if (s instanceof J) {
            return "Java";
        } else if (s instanceof Json) {
            return "Json";
        } else if (s instanceof Hcl) {
            return "Hcl";
        } else if (s instanceof Properties) {
            return "Properties";
        } else if (s instanceof Proto) {
            return "Protobuf";
        } else if (s instanceof Toml) {
            return "Toml";
        } else if (s instanceof Xml) {
            return "Xml";
        } else if (s instanceof Yaml) {
            return "Yaml";
        } else if (s instanceof PlainText) {
            return "Plain text";
        } else if (s instanceof ParseError) {
            return "Parse error";
        }
        return "Unknown";
    }

    /**
     * The number of lines of code attributed to the per-folder and per-repository reports. Languages with a
     * dedicated counter use it; the rest fall back to the generic count of lines of text.
     */
    private static int codeLineCount(SourceFile s, String language, int genericLineCount) {
        switch (language) {
            case "Kotlin":
                // Don't have a kotlin-specific counter yet and Java count should be very close
            case "Java":
                return org.openrewrite.java.CountLinesVisitor.countLines(s);
            case "Groovy":
                return org.openrewrite.groovy.CountLinesVisitor.countLines(s);
            case "Json":
                return org.openrewrite.json.CountLinesVisitor.countLines(s);
            case "Hcl":
                return org.openrewrite.hcl.CountLinesVisitor.countLines(s);
            case "Properties":
                return org.openrewrite.properties.CountLinesVisitor.countLines(s);
            case "Protobuf":
                return org.openrewrite.protobuf.CountLinesVisitor.countLines(s);
            case "Xml":
                return org.openrewrite.xml.CountLinesVisitor.countLines(s);
            case "Yaml":
                return org.openrewrite.yaml.CountLinesVisitor.countLines(s);
            default:
                return genericLineCount;
        }
    }

    /**
     * Count lines without letting a counting failure reclassify the file. Counting prints the LST, which for
     * some languages happens over RPC and can fail even though the file parsed successfully. Recoverable
     * failures are recorded in {@link SourcesFileErrors} (the same table the framework uses for recipe errors)
     * and degrade to a count of zero; unrecoverable {@link Error}s (e.g. {@link OutOfMemoryError}) are allowed
     * to propagate.
     */
    private int safeLineCount(SourceFile s, ExecutionContext ctx, IntSupplier counter) {
        try {
            return counter.getAsInt();
        } catch (RuntimeException e) {
            errorsTable.insertRow(ctx, new SourcesFileErrors.Row(
                    s.getSourcePath().toString(),
                    getName(),
                    ExceptionUtils.sanitizeStackTrace(e, LanguageComposition.class)));
            return 0;
        }
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

        return emptyList();
    }

    private static class Counts {
        int lineCount;
        int fileCount;
    }

    private static class LineCounter extends PrintOutputCapture<Integer> {
        private int count;
        private boolean startedLine;

        public LineCounter() {
            super(0);
        }

        static int count(SourceFile s) {
            LineCounter counter = new LineCounter();
            s.printAll(counter);
            return counter.getLineCount();
        }

        @Override
        public PrintOutputCapture<Integer> append(char c) {
            if (c == '\n') {
                count++;
                startedLine = false;
            } else {
                startedLine = true;
            }
            return this;
        }

        @Override
        public PrintOutputCapture<Integer> append(@Nullable String text) {
            if (text == null) {
                return this;
            }
            for (int i = 0; i < text.length(); i++) {
                append(text.charAt(i));
            }
            return this;
        }

        int getLineCount() {
            return count + (startedLine ? 1 : 0);
        }
    }

}
