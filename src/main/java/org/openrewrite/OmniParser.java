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

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.openrewrite.hcl.HclParser;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.json.JsonParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.protobuf.ProtoParser;
import org.openrewrite.quark.QuarkParser;
import org.openrewrite.shaded.jgit.ignore.IgnoreNode;
import org.openrewrite.text.PlainTextParser;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyList;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class OmniParser implements Parser {
    private static final Collection<String> DEFAULT_IGNORED_DIRECTORIES = Arrays.asList(
            "build",
            "target",
            "out",
            ".gradle",
            ".idea",
            ".project",
            "node_modules",
            ".git",
            ".metadata",
            ".DS_Store",
            ".moderne"
    );

    private final Collection<Path> exclusions;
    private final Collection<PathMatcher> exclusionMatchers;
    private final int sizeThresholdMb;
    private final Collection<Path> excludedDirectories;
    private final Collection<PathMatcher> plainTextMasks;
    private final boolean parallel;
    private final List<Parser> parsers;
    private final Consumer<Path> onParseStart;
    private final Consumer<Integer> onParse;

    public Stream<SourceFile> parseAll(Path rootDir) {
        return parse(acceptedPaths(rootDir), rootDir, new InMemoryExecutionContext());
    }

    @Override
    public Stream<SourceFile> parse(Iterable<Path> sourceFiles, @Nullable Path relativeTo, ExecutionContext ctx) {
        int count = 0;
        for (Path ignored : sourceFiles) {
            count++;
        }
        onParse.accept(count);
        return Parser.super.parse(sourceFiles, relativeTo, ctx);
    }

    public List<Path> acceptedPaths(Path rootDir) {
        List<Path> parseable = new ArrayList<>();
        Map<Path, IgnoreNode> gitignoreStack = new LinkedHashMap<>();

        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    loadGitignore(dir).ifPresent(ignoreNode -> gitignoreStack.put(dir, ignoreNode));
                    return isExcluded(dir, rootDir) ||
                           isIgnoredDirectory(dir, rootDir) ||
                           excludedDirectories.contains(dir) ||
                           isGitignored(gitignoreStack.values(), dir, rootDir) ?
                            FileVisitResult.SKIP_SUBTREE :
                            FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isOther() && !attrs.isSymbolicLink() &&
                        !isExcluded(file, rootDir) &&
                        !isGitignored(gitignoreStack.values(), file, rootDir)) {
                        if (!isOverSizeThreshold(attrs.size()) && !isParsedAsPlainText(file, rootDir)) {
                            for (Parser parser : parsers) {
                                if (parser.accept(file)) {
                                    parseable.add(file);
                                    break;
                                }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    gitignoreStack.remove(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // cannot happen, since none of the visit methods throw an IOException
            throw new UncheckedIOException(e);
        }
        return parseable;
    }

    @Override
    public Stream<SourceFile> parseInputs(Iterable<Input> sources, @Nullable Path relativeTo,
                                          ExecutionContext ctx) {
        return StreamSupport.stream(sources.spliterator(), parallel).flatMap(input -> {
            Path path = input.getPath();
            for (Parser parser : parsers) {
                if (parser.accept(path)) {
                    onParseStart.accept(path);
                    return parser.parseInputs(Collections.singletonList(input), relativeTo, ctx);
                }
            }
            return Stream.empty();
        });
    }

    @Override
    public boolean accept(Path path) {
        for (Parser parser : parsers) {
            if (parser.accept(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Path sourcePathFromSourceText(Path prefix, String sourceCode) {
        return Paths.get("resource.me");
    }

    private boolean isOverSizeThreshold(long fileSize) {
        return sizeThresholdMb > 0 && fileSize > sizeThresholdMb * 1024L * 1024L;
    }

    private boolean isExcluded(Path path, Path rootDir) {
        if (exclusions.contains(path)) {
            return true;
        }
        for (PathMatcher excluded : exclusionMatchers) {
            if (excluded.matches(rootDir.relativize(path))) {
                return true;
            }
        }
        return false;
    }

    private boolean isParsedAsPlainText(Path path, Path rootDir) {
        if (!plainTextMasks.isEmpty()) {
            Path computed = rootDir.relativize(path);
            if (!computed.startsWith("/")) {
                computed = Paths.get("/").resolve(computed);
            }
            for (PathMatcher matcher : plainTextMasks) {
                if (matcher.matches(computed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isIgnoredDirectory(Path path, Path rootDir) {
        for (Path pathSegment : rootDir.relativize(path)) {
            if (DEFAULT_IGNORED_DIRECTORIES.contains(pathSegment.toString())) {
                return true;
            }
        }
        return false;
    }

    private Optional<IgnoreNode> loadGitignore(Path dir) {
        Path gitignore = dir.resolve(".gitignore");
        if (!Files.exists(gitignore)) {
            return Optional.empty();
        }
        IgnoreNode ignoreNode = new IgnoreNode();
        try (InputStream is = Files.newInputStream(gitignore)) {
            ignoreNode.parse(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading '" + gitignore + "'", e);
        }
        return Optional.of(ignoreNode);
    }

    private boolean isGitignored(Collection<IgnoreNode> gitignoreStack, Path path, Path rootDir) {
        // We are retrieving the elements in insertion order thanks to Deque
        for (IgnoreNode ignoreNode : gitignoreStack) {
            Boolean result = ignoreNode.checkIgnored(rootDir.relativize(path).toFile().getPath(), path.toFile().isDirectory());
            if (result != null) {
                return result;
            }
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends Parser.Builder {
        private Collection<Path> exclusions = emptyList();
        private Collection<PathMatcher> exclusionMatchers = emptyList();
        private int sizeThresholdMb = 10;
        private Collection<Path> excludedDirectories = emptyList();
        private Collection<PathMatcher> plainTextMasks = emptyList();
        private boolean parallel;
        private Consumer<Path> onParseStart = path -> {
        };
        private Consumer<Integer> onParse = inputCount -> {
        };

        private List<Parser> parsers = Arrays.asList(
                new JsonParser(),
                new XmlParser(),
                new YamlParser(),
                new PropertiesParser(),
                new ProtoParser(),
                HclParser.builder().build(),
                new PlainTextParser(),
                new QuarkParser()
        );

        public Builder() {
            super(SourceFile.class);
        }

        public Builder exclusions(Collection<Path> exclusions) {
            this.exclusions = exclusions;
            return this;
        }

        public Builder exclusionMatchers(Collection<PathMatcher> exclusions) {
            this.exclusionMatchers = exclusions;
            return this;
        }

        public Builder sizeThresholdMb(int sizeThresholdMb) {
            this.sizeThresholdMb = sizeThresholdMb;
            return this;
        }

        public Builder excludedDirectories(Collection<Path> excludedDirectories) {
            this.excludedDirectories = excludedDirectories;
            return this;
        }

        public Builder plainTextMasks(Collection<PathMatcher> plainTextMasks) {
            this.plainTextMasks = plainTextMasks;
            return this;
        }

        public Builder onParseStart(Consumer<Path> onParseStart) {
            this.onParseStart = onParseStart;
            return this;
        }

        public Builder onParse(Consumer<Integer> onParse) {
            this.onParse = onParse;
            return this;
        }

        public Builder parsers(Parser... parsers) {
            this.parsers = Arrays.asList(parsers);
            return this;
        }

        public Builder addParsers(Parser... parsers) {
            this.parsers.addAll(Arrays.asList(parsers));
            return this;
        }

        /**
         * Resource parsers are safe to execute in parallel. This is not true of all parsers, for example
         * the MavenParser.
         *
         * @param parallel whether the parser stream should be parallelized.
         * @return this builder.
         */
        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        @Override
        public OmniParser build() {
            return new OmniParser(exclusions, exclusionMatchers, sizeThresholdMb,
                    excludedDirectories, plainTextMasks, parallel, parsers, onParseStart, onParse);
        }

        @Override
        public String getDslName() {
            return "omni";
        }
    }
}
