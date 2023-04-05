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
import org.openrewrite.text.PlainText;
import org.openrewrite.xml.tree.Xml;
import org.openrewrite.yaml.tree.Yaml;

import java.util.List;

@Value
@EqualsAndHashCode(callSuper = true)
public class LanguageComposition extends Recipe {

    transient LanguageCompositionReport report = new LanguageCompositionReport(this);

    @Override
    public String getDisplayName() {
        return "Language composition report";
    }

    @Override
    public String getDescription() {
        //language=markdown
        return "Counts the number of lines of the various kinds of source code and data formats parsed by OpenRewrite. " +
               "Comments are not included in line counts. " +
               "This recipe emits its results as a data table, making no changes to any source file.";
    }

    @Override
    public List<SourceFile> visit(List<SourceFile> before, ExecutionContext ctx) {
        int cobolFileCount = 0;
        int cobolLineCount = 0;
        int groovyFileCount = 0;
        int groovyLineCount = 0;
        int hclFileCount = 0;
        int hclLineCount = 0;
        int javaFileCount = 0;
        int javaLineCount = 0;
        int jsonFileCount = 0;
        int jsonLineCount = 0;
        int kotlinFileCount = 0;
        int kotlinLineCount = 0;
        int plainTextFileCount = 0;
        int plainTextLineCount = 0;
        int propertiesFileCount = 0;
        int propertiesLineCount = 0;
        int protobufFileCount = 0;
        int protobufLineCount = 0;
        int pythonFileCount = 0;
        int pythonLineCount = 0;
        int xmlFileCount = 0;
        int xmlLineCount = 0;
        int yamlFileCount = 0;
        int yamlLineCount = 0;
        int otherFileCount = 0;
        for(SourceFile s : before) {
            if(s instanceof Quark || s instanceof Binary || s instanceof Remote) {
                otherFileCount++;
            } else if("org.openrewrite.cobol.tree.Cobol".equals(s.getClass().getName())) {
                cobolFileCount++;
                cobolLineCount += genericLineCount(s);
            } else if(s instanceof K) {
                kotlinFileCount++;
                // Don't have a kotlin-specific counter yet and Java count should be very close
                kotlinLineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
            } else if(s instanceof G) {
                groovyFileCount++;
                groovyLineCount += org.openrewrite.groovy.CountLinesVisitor.countLines(s);
            } else if(s instanceof Py) {
                pythonFileCount++;
                pythonLineCount += genericLineCount(s);
            }  else if(s instanceof J) {
                javaFileCount++;
                javaLineCount += org.openrewrite.java.CountLinesVisitor.countLines(s);
            }  else if(s instanceof Json) {
                jsonFileCount++;
                jsonLineCount += org.openrewrite.json.CountLinesVisitor.countLines(s);
            } else if(s instanceof Hcl) {
                hclFileCount++;
                hclLineCount += org.openrewrite.hcl.CountLinesVisitor.countLines(s);
            } else if(s instanceof Properties) {
                propertiesFileCount++;
                propertiesLineCount += org.openrewrite.properties.CountLinesVisitor.countLines(s);
            } else if(s instanceof Proto) {
                protobufFileCount++;
                protobufLineCount += org.openrewrite.protobuf.CountLinesVisitor.countLines(s);
            } else if(s instanceof Xml) {
                xmlFileCount++;
                xmlLineCount += org.openrewrite.xml.CountLinesVisitor.countLines(s);
            } else if(s instanceof Yaml) {
                yamlFileCount++;
                yamlLineCount += org.openrewrite.yaml.CountLinesVisitor.countLines(s);
            } else if (s instanceof PlainText) {
                plainTextFileCount++;
                plainTextLineCount += genericLineCount(s);
            }
        }
        report.insertRow(ctx, new LanguageCompositionReport.Row(
                cobolFileCount, cobolLineCount,
                groovyFileCount, groovyLineCount,
                hclFileCount, hclLineCount,
                javaFileCount, javaLineCount,
                jsonFileCount, jsonLineCount,
                kotlinFileCount, kotlinLineCount,
                plainTextFileCount, plainTextLineCount,
                propertiesFileCount, propertiesLineCount,
                protobufFileCount, protobufLineCount,
                pythonFileCount, pythonLineCount,
                xmlFileCount, xmlLineCount,
                yamlFileCount, yamlLineCount,
                otherFileCount));
        return before;
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
            if(c == '\n') {
                count++;
            }
            return this;
        }

        @Override
        public PrintOutputCapture<Integer> append(@Nullable String text) {
            if(text == null) {
                return this;
            }
            if(text.contains("\n")) {
                count++;
            }
            return this;
        }

        int getLineCount() {
            return count;
        }
    }
}
