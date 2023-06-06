/*
 * Copyright 2023 the original author or authors.
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
package org.openrewrite.table;

import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;

/**
 * Aggregate parsing errors by file extension, cause, and count to identify and accelerate fixing parsing errors.
 */
public class ParseFailureAnalysis extends DataTable<ParseFailureAnalysis.Row> {

    public ParseFailureAnalysis(Recipe recipe) {
        super(recipe, "Find and aggregate parsing errors",
                "Finds and aggregates parsing exceptions to fix the most common issues first.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source file extension",
                description = "The file extension of the source.")
        String fileExtension;

        @Column(displayName = "Exception name or node type",
                description = "Identifies the cause of a parsing error.")
        String errorName;

        @Column(displayName = "Error count",
                description = "A count of the errors by name and file extension.")
        int exceptionCount;
    }

    /**
     * Generate a parsing exception message from a given node type for analysis.
     * @param errorName A unique name that represents the node type or exception name that caused the parsing exception.
     * @return analysis message.
     */
    public static String getAnalysisMessage(String errorName) {
        return getAnalysisMessage(errorName, null);
    }

    /**
     * Generate a parsing exception message from a given node type for analysis.
     * @param errorName A unique name that represents the node type that caused the parsing exception.
     * @param sourceSnippet Optional source snippet to identify where the exception occurred.
     * @return analysis message.
     */
    public static String getAnalysisMessage(String errorName, @Nullable String sourceSnippet) {
        return "Unable to parse node of type {{" + errorName + (sourceSnippet != null ? "}} at: " + sourceSnippet : "}}");
    }

    public static boolean containsMessage(String message) {
        return message.contains("Unable to parse node of type {{");
    }

    public static String getErrorName(String message) {
        if (message.contains("{{")) {
            return message.substring(message.indexOf("{{") + 2, message.indexOf("}}"));
        } else {
            return message.substring(0, message.indexOf(":"));
        }
    }

    public static String getSourceSnippet(String message) {
        int start = message.indexOf("}} at: ") + 7;
        return start > 6 ? message.substring(start) : "";
    }
}
