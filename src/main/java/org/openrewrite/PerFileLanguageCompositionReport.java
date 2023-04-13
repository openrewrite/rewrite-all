package org.openrewrite;

import lombok.Value;

public class PerFileLanguageCompositionReport extends DataTable<org.openrewrite.table.LanguageComposition.Row> {

    public PerFileLanguageCompositionReport(Recipe recipe) {
        super(recipe, "Per-file language composition report",
                "A list of individual files and their language composition.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "The path to the source file.")
        String sourcePath;

        @Column(displayName = "Language",
                description = "The language of the source file.")
        String language;

        @Column(displayName = "Weight",
                description = "The weight of the source file, in terms of " +
                              "total number of AST nodes, markers, and type " +
                              "attribution nodes.")
        Long weight;

        @Column(displayName = "Lines of text",
                description = "The number of lines of text in the source file. " +
                              "No language-specific knowledge to skip comments, blank lines, or any other non-code line.")
        Integer linesOfText;
    }
}
