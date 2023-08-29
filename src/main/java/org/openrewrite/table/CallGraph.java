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
import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class CallGraph extends DataTable<CallGraph.Row> {

    public CallGraph(Recipe recipe) {
        super(recipe,
                "Method call graph",
                "Records method callers and the methods they invoke.");
    }

    @Value
    public static class Row {
        @Column(displayName = "From class",
                description = "The fully qualified name of the class from which the action is issued.")
        String fromClass;

        @Column(displayName = "From name",
                description = "The name of the method or scope from which the action is issued.")
        String fromName;

        @Column(displayName = "From arguments",
                description = "The argument types, if any, to the method or scope from which the action is issued. " +
                              "Expressed as a comma-separated list")
        String fromArguments;

        @Column(displayName = "From type",
                description = "The type of resource the action is being issued from.")
        ResourceType fromType;

        @Column(displayName = "Action",
                description = "The type of access being made to the resource.")
        ResourceAction action;

        @Column(displayName = "To class",
                description = "The fully-qualified name of the class containing the resource being accessed.")
        String toClass;

        @Column(displayName = "To name",
                description = "The name of the resource being accessed.")
        String toName;

        @Column(displayName = "To arguments",
                description = "The argument types, if any, to the resource being accessed. " +
                              "Expressed as a comma-separated list")
        String toArguments;

        @Column(displayName = "To type",
                description = "The type of resource being accessed.")
        ResourceType toType;

        @Column(displayName = "Return type",
                description = "The return type of the method.")
        String returnType;
    }

    public enum ResourceType {
        METHOD,
        FIELD,
        CONSTRUCTOR
    }

    public enum ResourceAction {
        CALL,
        READ,
        WRITE
    }
}
