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
package org.openrewrite;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.table.CallGraph;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class FindCallGraph extends Recipe {
    transient CallGraph callGraph = new CallGraph(this);

    @Override
    public String getDisplayName() {
        return "Find call graph";
    }

    @Override
    public String getDescription() {
        return "Produces a data table where each row represents a method call.";
    }

    @Option(displayName = "Include standard library",
            description = "When enabled calls to methods in packages beginning with \"java\", \"groovy\", and \"kotlin\" " +
                          "will be included in the report. " +
                          "By default these are omitted.",
            required = false)
    boolean includeStdLib;

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            final Set<JavaType.Method> methodsCalledInScope = Collections.newSetFromMap(new IdentityHashMap<>());
            final Set<JavaType.Method> methodsCalledInit = Collections.newSetFromMap(new IdentityHashMap<>());
            final Set<JavaType.Method> methodsCalledClassInit = Collections.newSetFromMap(new IdentityHashMap<>());
            boolean inInitializer;
            boolean inStaticInitializer;

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                for (Statement statement : classDecl.getBody().getStatements()) {
                    if (statement instanceof J.Block) {
                        J.Block block = (J.Block) statement;
                        if (block.isStatic()) {
                            inStaticInitializer = true;
                        } else {
                            inInitializer = true;
                        }
                    } else if (statement instanceof J.VariableDeclarations) {
                        J.VariableDeclarations variableDeclarations = (J.VariableDeclarations) statement;
                        if (variableDeclarations.getModifiers().stream().anyMatch(mod -> mod.getType() == J.Modifier.Type.Static)) {
                            inStaticInitializer = true;
                        } else {
                            inInitializer = true;
                        }
                    }
                    visit(statement, ctx);
                    inStaticInitializer = false;
                    inInitializer = false;
                }

                return classDecl;
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);
                methodsCalledInScope.clear();
                return m;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                recordCall(newClass.getMethodType(), ctx);
                return super.visitNewClass(newClass, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                recordCall(method.getMethodType(), ctx);
                return super.visitMethodInvocation(method, ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                recordCall(memberRef.getMethodType(), ctx);
                return super.visitMemberReference(memberRef, ctx);
            }

            private void recordCall(@Nullable JavaType.Method method, ExecutionContext ctx) {
                if (method == null) {
                    return;
                }
                String fqn = method.getDeclaringType().getFullyQualifiedName();
                if (!includeStdLib && (fqn.startsWith("java.") || fqn.startsWith("groovy.") || fqn.startsWith("kotlin."))) {
                    return;
                }
                J.MethodDeclaration declaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (declaration == null) {
                    J.ClassDeclaration classDecl = getCursor().firstEnclosing(J.ClassDeclaration.class);
                    if (classDecl != null && classDecl.getType() != null &&
                        ((inInitializer && methodsCalledInit.add(method))
                         || (inStaticInitializer && methodsCalledClassInit.add(method)))) {
                        callGraph.insertRow(ctx, row(classDecl.getType(), method));
                    }
                } else if (declaration.getMethodType() != null && methodsCalledInScope.add(method)) {
                    callGraph.insertRow(ctx, row(declaration.getMethodType(), method));
                }
            }

            private CallGraph.Row row(JavaType.FullyQualified from, JavaType.Method to) {
                String fromName;
                if (inInitializer) {
                    fromName = "<init>";
                } else if (inStaticInitializer) {
                    fromName = "<clinit>";
                } else {
                    fromName = "";
                }
                return new CallGraph.Row(
                        from.getFullyQualifiedName(),
                        fromName,
                        "",
                        CallGraph.ResourceType.METHOD,
                        CallGraph.ResourceAction.CALL,
                        to.getDeclaringType().getFullyQualifiedName(),
                        to.getName(),
                        parameters(to),
                        resourceType(to)
                );
            }

            private CallGraph.Row row(JavaType.Method from, JavaType.Method to) {
                return new CallGraph.Row(
                        from.getDeclaringType().getFullyQualifiedName(),
                        from.getName(),
                        parameters(from),
                        resourceType(from),
                        CallGraph.ResourceAction.CALL,
                        to.getDeclaringType().getFullyQualifiedName(),
                        to.getName(),
                        parameters(to),
                        resourceType(to)
                );
            }
        };

    }

    private static String parameters(JavaType.Method method) {
        StringJoiner joiner = new StringJoiner(",");
        for (JavaType javaType : method.getParameterTypes()) {
            String string = javaType.toString();
            joiner.add(string);
        }
        return joiner.toString();
    }

    private static CallGraph.ResourceType resourceType(JavaType.Method method) {
        if (method.isConstructor()) {
            return CallGraph.ResourceType.CONSTRUCTOR;
        }
        return CallGraph.ResourceType.METHOD;
    }
}
