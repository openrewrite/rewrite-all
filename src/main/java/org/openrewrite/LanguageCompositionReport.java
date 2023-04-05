package org.openrewrite;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.Value;

@JsonIgnoreType
public class LanguageCompositionReport extends DataTable<LanguageCompositionReport.Row> {

    public LanguageCompositionReport(Recipe recipe) {
        super(recipe,
            "Language composition report",
            "Counts the number of files and lines of source code in the various formats OpenRewrite knows how to parse.");
    }

    @Value
    public static class Row {

        @Column(displayName = "Cobol file count",
                description = "Count of files containing code, including copybooks.")
        int cobolFileCount;
        @Column(displayName = "Cobol line count",
                description = "Count of lines of cobol code, including copybooks.")
        int cobolLineCount;

        @Column(displayName = "Groovy file count",
                description = "Count of files containing groovy code, including build.gradle files.")
        int groovyFileCount;

        @Column(displayName = "Groovy line count",
                description = "Count of lines of groovy code, including build.gradle files.")
        int groovyLineCount;

        @Column(displayName = "HCL file count",
                description = "Count of files containing Hashicorp Configuration Language (HCL) code, including Terraform files.")
        int hclFileCount;

        @Column(displayName = "HCL line count",
                description = "Count of lines of Hashicorp Configuration Language (HCL) code, including Terraform files.")
        int hclLineCount;

        @Column(displayName = "Java file count",
                description = "Count of files containing Java code.")
        int javaFileCount;

        @Column(displayName = "Java line count",
                description = "Count of lines of Java code.")
        int javaLineCount;

        @Column(displayName = "Javascript file count",
                description = "Count of files containing JSON data.")
        int jsonFileCount;

        @Column(displayName = "Javascript line count",
                description = "Count of lines of JSON data.")
        int jsonLineCount;

        @Column(displayName = "Kotlin file count",
                description = "Count of files containing Kotlin code.")
        int kotlinFileCount;

        @Column(displayName = "Kotlin line count",
                description = "Count of lines of Kotlin code.")
        int kotlinLineCount;

        @Column(displayName = "Plain text file count",
                description = "Count of files containing plain text.")
        int plainTextFileCount;

        @Column(displayName = "Plain text line count",
                description = "Count of lines of plain text.")
        int plainTextLineCount;

        @Column(displayName = "Properties file count",
                description = "Count of files containing Java properties.")
        int propertiesFileCount;

        @Column(displayName = "Properties line count",
                description = "Count of lines of Java properties files.")
        int propertiesLineCount;

        @Column(displayName = "Protobuf file count",
                description = "Count of files containing Google Protocol Buffers (protobuf) code.")
        int protobufFileCount;

        @Column(displayName = "Protobuf line count",
                description = "Count of lines of Google Protocol Buffers (protobuf) code.")
        int protobufLineCount;

        @Column(displayName = "Python file count",
                description = "Count of files containing Python code.")
        int pythonFileCount;

        @Column(displayName = "Python line count",
                description = "Count of lines of Python code.")
        int pythonLineCount;

        @Column(displayName = "XML file count",
                description = "Count of files containing XML data, including Maven poms.")
        int xmlFileCount;

        @Column(displayName = "XML line count",
                description = "Count of lines of XML data, including Maven poms.")
        int xmlLineCount;

        @Column(displayName = "YAML file count",
                description = "Count of files containing YAML data.")
        int yamlFileCount;

        @Column(displayName = "YAML line count",
                description = "Count of lines of YAML data.")
        int yamlLineCount;

        @Column(displayName = "Other file count",
                description = "Count of files that are not otherwise parsed.")
        int otherFileCount;
    }
}
