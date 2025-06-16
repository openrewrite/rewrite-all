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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.table.CallGraph;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

@EqualsAndHashCode(callSuper = false)
@Value
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

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (classDecl.getType() == null) {
                    return Markup.warn(classDecl, new IllegalStateException("Class declaration is missing type attribution"));
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                return super.visitNewClass(recordCall(newClass, ctx), ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                return super.visitMethodInvocation(recordCall(method, ctx), ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                return super.visitMemberReference(recordCall(memberRef, ctx), ctx);
            }

            private <T extends J> T recordCall(T j, ExecutionContext ctx) {
                JavaType.Method method = null;
                if (j instanceof J.MethodInvocation) {
                    method = ((J.MethodInvocation) j).getMethodType();
                } else if (j instanceof J.NewClass) {
                    method = ((J.NewClass) j).getMethodType();
                } else if (j instanceof J.MemberReference) {
                    method = ((J.MemberReference) j).getMethodType();
                }
                if (method == null) {
                    return Markup.warn(j, new IllegalStateException("Method type not found"));
                }
                String fqn = method.getDeclaringType().getFullyQualifiedName();
                if (!includeStdLib && (fqn.startsWith("java.") || fqn.startsWith("groovy.") || fqn.startsWith("kotlin."))) {
                    return j;
                }
                Cursor scope = getCursor().dropParentUntil(it -> it instanceof J.MethodDeclaration || it instanceof J.ClassDeclaration || it instanceof SourceFile);
                String sourceSet = Optional.ofNullable(scope.firstEnclosing(SourceFile.class))
                        .map(Tree::getMarkers)
                        .flatMap(m -> m.findFirst(SourceSet.class))
                        .map(SourceSet::getName)
                        .orElse("unknown");
                if (scope.getValue() instanceof J.ClassDeclaration) {
                    boolean isInStaticInitializer = inStaticInitializer();
                    if ((isInStaticInitializer && scope.computeMessageIfAbsent("METHODS_CALLED_IN_STATIC_INITIALIZATION", k -> new HashSet<>()).add(method)) ||
                        (!isInStaticInitializer && scope.computeMessageIfAbsent("METHODS_CALLED_IN_INSTANCE_INITIALIZATION", k -> new HashSet<>()).add(method))) {
                        callGraph.insertRow(ctx, row(sourceSet, requireNonNull(((J.ClassDeclaration) scope.getValue()).getType()).getFullyQualifiedName(), method));
                    }
                } else if (scope.getValue() instanceof J.MethodDeclaration) {
                    Set<JavaType.Method> methodsCalledInScope = scope.computeMessageIfAbsent("METHODS_CALLED_IN_SCOPE", k -> new HashSet<>());
                    if (methodsCalledInScope.add(method)) {
                        callGraph.insertRow(ctx, row(sourceSet,requireNonNull(((J.MethodDeclaration) scope.getValue()).getMethodType()), method));
                    }
                } else if (scope.getValue() instanceof SourceFile) {
                    // In Java there has to be a class declaration, but that isn't the case in Groovy/Kotlin/etc.
                    // So we'll just use the source file path instead
                    Set<JavaType.Method> methodsCalledInScope = scope.computeMessageIfAbsent("METHODS_CALLED_IN_SCOPE", k -> new HashSet<>());
                    if (methodsCalledInScope.add(method)) {
                        callGraph.insertRow(ctx, row(sourceSet, ((SourceFile) scope.getValue()).getSourcePath().toString(), method));
                    }
                }
                return j;
            }

            private boolean inStaticInitializer() {
                AtomicBoolean inStaticInitializer = new AtomicBoolean();
                getCursor().dropParentUntil(it -> {
                    if (it instanceof SourceFile) {
                        return true;
                    } else if (it instanceof J.Block) {
                        J.Block b = (J.Block) it;
                        if (b.isStatic()) {
                            inStaticInitializer.set(true);
                            return true;
                        }
                    } else if (it instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations) it;
                        if (vd.hasModifier(J.Modifier.Type.Static)) {
                            inStaticInitializer.set(true);
                            return true;
                        }
                    }
                    return false;
                });
                return inStaticInitializer.get();
            }

            private CallGraph.Row row(String sourceSet, String fqn, JavaType.Method to) {
                return new CallGraph.Row(
                        sourceSet,
                        fqn,
                        inStaticInitializer() ? "<clinit>" : "<init>",
                        "",
                        CallGraph.ResourceType.METHOD,
                        CallGraph.ResourceAction.CALL,
                        to.getDeclaringType().getFullyQualifiedName(),
                        to.getName(),
                        parameters(to),
                        resourceType(to),
                        returnType(to)
                );
            }

            private CallGraph.Row row(String sourceSet,JavaType.Method from, JavaType.Method to) {
                return new CallGraph.Row(
                        sourceSet,
                        from.getDeclaringType().getFullyQualifiedName(),
                        from.getName(),
                        parameters(from),
                        resourceType(from),
                        CallGraph.ResourceAction.CALL,
                        to.getDeclaringType().getFullyQualifiedName(),
                        to.getName(),
                        parameters(to),
                        resourceType(to),
                        returnType(to)
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

    private static String returnType(JavaType.Method method) {
        return method.getReturnType().toString();
    }
}
