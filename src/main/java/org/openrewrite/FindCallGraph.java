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
import org.jspecify.annotations.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.NameTree;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.Markup;
import org.openrewrite.marker.SourceSet;
import org.openrewrite.table.CallGraph;
import org.openrewrite.table.FactoryEdges;
import org.openrewrite.table.LowConfidenceFiles;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class FindCallGraph extends Recipe {
    transient CallGraph callGraph = new CallGraph(this);
    transient FactoryEdges factoryEdges = new FactoryEdges(this);
    transient LowConfidenceFiles lowConfidenceFiles = new LowConfidenceFiles(this);

    String displayName = "Find call graph";

    String description = "Produces a data table where each row represents a method call.";

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
                    recordLowConfidence(ctx, "class.type");
                    return Markup.warn(classDecl, new IllegalStateException("Class declaration is missing type attribution"));
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            /**
             * Mark the current source file as having an incomplete call graph. Deduped per
             * (sourcePath, reason) within a single recipe run via an ExecutionContext bag,
             * so a heavily-attributed file emits a few rows rather than thousands. Downstream
             * test selection treats any row for a file as "method-level reachability for this
             * file is unreliable -- escalate to module-coarse selection for any test module
             * that depends on this file's module."
             */
            private void recordLowConfidence(ExecutionContext ctx, String reason) {
                SourceFile sf = getCursor().firstEnclosing(SourceFile.class);
                if (sf == null) {
                    return;
                }
                String sourcePath = sf.getSourcePath().toString();
                String dedupeKey = sourcePath + "::" + reason;
                Set<String> recorded = ctx.computeMessageIfAbsent(
                        "LOW_CONFIDENCE_RECORDED", k -> new HashSet<>());
                if (recorded.add(dedupeKey)) {
                    lowConfidenceFiles.insertRow(ctx, new LowConfidenceFiles.Row(sourcePath, reason));
                }
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext ctx) {
                maybeRecordFactoryEdge(newClass, ctx);
                return super.visitNewClass(recordCall(newClass, ctx), ctx);
            }

            /**
             * Emit a {@link FactoryEdges} row if the enclosing method's declared return
             * type is assignable from the class being constructed -- i.e. the enclosing
             * method semantically "produces" instances of the constructed class. Uses
             * {@link TypeUtils#isAssignableTo(String, JavaType)} against the LST so the
             * check handles supertype/interface relationships correctly; a separate
             * consumer (reachability closure) then treats these edges as widening
             * signals without having to replicate the type-hierarchy lookup.
             */
            private void maybeRecordFactoryEdge(J.NewClass newClass, ExecutionContext ctx) {
                JavaType.Method constructor = newClass.getMethodType();
                if (constructor == null) {
                    return;
                }
                JavaType.FullyQualified constructed = constructor.getDeclaringType();
                if (constructed == null) {
                    return;
                }
                J.MethodDeclaration enclosing = getCursor().firstEnclosing(J.MethodDeclaration.class);
                if (enclosing == null) {
                    return;
                }
                JavaType.Method enclosingType = enclosing.getMethodType();
                if (enclosingType == null) {
                    return;
                }
                JavaType.FullyQualified enclosingReturn = extractFullyQualified(enclosingType.getReturnType());
                if (enclosingReturn == null) {
                    return;
                }
                if (!TypeUtils.isAssignableTo(enclosingReturn.getFullyQualifiedName(), constructed)) {
                    return;
                }
                String constructedFqn = constructed.getFullyQualifiedName();
                if (!includeStdLib && isStdLib(constructedFqn)) {
                    return;
                }
                JavaType.FullyQualified enclosingDeclaring = enclosingType.getDeclaringType();
                if (enclosingDeclaring == null) {
                    recordLowConfidence(ctx, "factory.enclosingMethod.declaringType");
                    return;
                }
                factoryEdges.insertRow(ctx, new FactoryEdges.Row(
                        enclosingDeclaring.getFullyQualifiedName(),
                        enclosingType.getName(),
                        constructedFqn));
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                return super.visitMethodInvocation(recordCall(method, ctx), ctx);
            }

            @Override
            public J.MemberReference visitMemberReference(J.MemberReference memberRef, ExecutionContext ctx) {
                return super.visitMemberReference(recordCall(memberRef, ctx), ctx);
            }

            @Override
            public J.Import visitImport(J.Import impoort, ExecutionContext ctx) {
                recordTypeReference(impoort.getQualid().getType(), ctx);
                return super.visitImport(impoort, ctx);
            }

            @Override
            public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, ExecutionContext ctx) {
                recordTypeTree(multiVariable.getTypeExpression(), ctx);
                return super.visitVariableDeclarations(multiVariable, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                // The call is dispatched BEFORE super visits children, so the current cursor is
                // still the enclosing scope for the method -- but we want the reference edge
                // attributed to the method itself. Delegate to super first so traversal walks
                // into child TypeTrees with the method on the cursor, then super's walk of the
                // body + return expression will naturally record the appropriate references
                // through visitVariableDeclarations (parameters) and the TypeTree visitors.
                // The return-type expression is a TypeTree-typed field of the method, so we
                // walk into it with the method on the cursor stack.
                if (method.getReturnTypeExpression() != null) {
                    recordTypeReferenceInScope(method.getReturnTypeExpression().getType(),
                            getCursor(), ctx);
                }
                if (method.getThrows() != null) {
                    for (NameTree thrown : method.getThrows()) {
                        if (thrown instanceof TypeTree) {
                            recordTypeReferenceInScope(thrown.getType(), getCursor(), ctx);
                        }
                    }
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.TypeCast visitTypeCast(J.TypeCast typeCast, ExecutionContext ctx) {
                recordTypeTree(typeCast.getClazz().getTree(), ctx);
                return super.visitTypeCast(typeCast, ctx);
            }

            @Override
            public J.InstanceOf visitInstanceOf(J.InstanceOf instanceOf, ExecutionContext ctx) {
                if (instanceOf.getClazz() instanceof TypeTree) {
                    recordTypeTree((TypeTree) instanceOf.getClazz(), ctx);
                }
                return super.visitInstanceOf(instanceOf, ctx);
            }

            @Override
            public J.NewArray visitNewArray(J.NewArray newArray, ExecutionContext ctx) {
                if (newArray.getTypeExpression() instanceof TypeTree) {
                    recordTypeTree(newArray.getTypeExpression(), ctx);
                }
                return super.visitNewArray(newArray, ctx);
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                // `X.class` literal: target is the type referenced, not the `class` field.
                if ("class".equals(fieldAccess.getSimpleName()) &&
                        fieldAccess.getTarget() instanceof TypeTree) {
                    recordTypeTree((TypeTree) fieldAccess.getTarget(), ctx);
                }
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            private void recordTypeTree(@Nullable TypeTree typeTree, ExecutionContext ctx) {
                if (typeTree == null) {
                    return;
                }
                recordTypeReference(typeTree.getType(), ctx);
            }

            /**
             * Record a type-reference edge attributed to whatever method / class /
             * compilation unit scope encloses the current cursor. Walks up from the
             * parent of the current cursor.
             */
            private void recordTypeReference(@Nullable JavaType type, ExecutionContext ctx) {
                Cursor scope = getCursor().dropParentUntil(it -> it instanceof J.MethodDeclaration ||
                        it instanceof J.ClassDeclaration || it instanceof SourceFile);
                recordTypeReferenceInScope(type, scope, ctx);
            }

            /**
             * Like {@link #recordTypeReference(JavaType, ExecutionContext)} but with an
             * explicit scope cursor. Used when the caller is a visit method for the scope
             * node itself (e.g. {@code visitMethodDeclaration} recording its return type)
             * -- in that case {@code getCursor()} is the scope, not an ancestor.
             */
            private void recordTypeReferenceInScope(@Nullable JavaType type, Cursor scope, ExecutionContext ctx) {
                JavaType.FullyQualified fq = extractFullyQualified(type);
                if (fq == null) {
                    return;
                }
                String fqn = fq.getFullyQualifiedName();
                if (!includeStdLib && isStdLib(fqn)) {
                    return;
                }
                String sourceSet = Optional.ofNullable(scope.firstEnclosing(SourceFile.class))
                        .map(Tree::getMarkers)
                        .flatMap(m -> m.findFirst(SourceSet.class))
                        .map(SourceSet::getName)
                        .orElse("unknown");

                if (scope.getValue() instanceof J.MethodDeclaration) {
                    JavaType.Method fromMethod = ((J.MethodDeclaration) scope.getValue()).getMethodType();
                    if (fromMethod == null) {
                        recordLowConfidence(ctx, "reference.enclosingMethod.type");
                        return;
                    }
                    JavaType.FullyQualified fromMethodDeclaring = fromMethod.getDeclaringType();
                    if (fromMethodDeclaring == null) {
                        recordLowConfidence(ctx, "reference.enclosingMethod.declaringType");
                        return;
                    }
                    if (!recordedReferences(scope).add(fqn)) {
                        return;
                    }
                    callGraph.insertRow(ctx, referenceRow(sourceSet,
                            fromMethodDeclaring.getFullyQualifiedName(),
                            fromMethod.getName(),
                            parameters(fromMethod),
                            resourceType(fromMethod),
                            fqn));
                } else if (scope.getValue() instanceof J.ClassDeclaration) {
                    JavaType.FullyQualified fromType = ((J.ClassDeclaration) scope.getValue()).getType();
                    if (fromType == null) {
                        recordLowConfidence(ctx, "reference.enclosingClass.type");
                        return;
                    }
                    if (!recordedReferences(scope).add(fqn)) {
                        return;
                    }
                    callGraph.insertRow(ctx, referenceRow(sourceSet,
                            fromType.getFullyQualifiedName(),
                            "",
                            "",
                            CallGraph.ResourceType.CLASS,
                            fqn));
                } else if (scope.getValue() instanceof SourceFile) {
                    if (!recordedReferences(scope).add(fqn)) {
                        return;
                    }
                    callGraph.insertRow(ctx, referenceRow(sourceSet,
                            ((SourceFile) scope.getValue()).getSourcePath().toString(),
                            "",
                            "",
                            CallGraph.ResourceType.CLASS,
                            fqn));
                }
            }

            @SuppressWarnings("unchecked")
            private Set<String> recordedReferences(Cursor scope) {
                return scope.computeMessageIfAbsent("TYPE_REFERENCES_IN_SCOPE", k -> new HashSet<String>());
            }

            private CallGraph.Row referenceRow(String sourceSet, String fromClass, String fromName,
                                               String fromArgs, CallGraph.ResourceType fromType, String toClass) {
                return new CallGraph.Row(
                        sourceSet,
                        fromClass,
                        fromName,
                        fromArgs,
                        fromType,
                        CallGraph.ResourceAction.REFERENCE,
                        toClass,
                        "",
                        "",
                        CallGraph.ResourceType.CLASS,
                        ""
                );
            }

            private boolean isStdLib(String fqn) {
                return fqn.startsWith("java.") || fqn.startsWith("groovy.") || fqn.startsWith("kotlin.");
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
                    recordLowConfidence(ctx, "call.methodType");
                    return Markup.warn(j, new IllegalStateException("Method type not found"));
                }
                JavaType.FullyQualified declaringType = method.getDeclaringType();
                if (declaringType == null) {
                    recordLowConfidence(ctx, "call.declaringType");
                    return j;
                }
                String fqn = declaringType.getFullyQualifiedName();
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
                    JavaType.FullyQualified scopeType = ((J.ClassDeclaration) scope.getValue()).getType();
                    if (scopeType == null) {
                        recordLowConfidence(ctx, "call.enclosingClass.type");
                        return j;
                    }
                    boolean isInStaticInitializer = inStaticInitializer();
                    if ((isInStaticInitializer && scope.computeMessageIfAbsent("METHODS_CALLED_IN_STATIC_INITIALIZATION", k -> new HashSet<>()).add(method)) ||
                        (!isInStaticInitializer && scope.computeMessageIfAbsent("METHODS_CALLED_IN_INSTANCE_INITIALIZATION", k -> new HashSet<>()).add(method))) {
                        callGraph.insertRow(ctx, row(sourceSet, scopeType.getFullyQualifiedName(), method));
                    }
                } else if (scope.getValue() instanceof J.MethodDeclaration) {
                    JavaType.Method scopeMethod = ((J.MethodDeclaration) scope.getValue()).getMethodType();
                    if (scopeMethod == null || scopeMethod.getDeclaringType() == null) {
                        recordLowConfidence(ctx, "call.enclosingMethod.type");
                        return j;
                    }
                    Set<JavaType.Method> methodsCalledInScope = scope.computeMessageIfAbsent("METHODS_CALLED_IN_SCOPE", k -> new HashSet<>());
                    if (methodsCalledInScope.add(method)) {
                        callGraph.insertRow(ctx, row(sourceSet, scopeMethod, method));
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
                    }
                    if (it instanceof J.Block) {
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
                        declaringFqn(to),
                        to.getName(),
                        parameters(to),
                        resourceType(to),
                        returnType(to)
                );
            }

            private CallGraph.Row row(String sourceSet, JavaType.Method from, JavaType.Method to) {
                return new CallGraph.Row(
                        sourceSet,
                        declaringFqn(from),
                        from.getName(),
                        parameters(from),
                        resourceType(from),
                        CallGraph.ResourceAction.CALL,
                        declaringFqn(to),
                        to.getName(),
                        parameters(to),
                        resourceType(to),
                        returnType(to)
                );
            }

            /**
             * Declaring-type FQN for a method, or {@code "?"} when type attribution is missing.
             * Callers that have already validated declaring-type non-null still funnel through
             * here so the row helpers stay total functions and the visit code stays linear.
             */
            private String declaringFqn(JavaType.Method method) {
                JavaType.FullyQualified dt = method.getDeclaringType();
                return dt == null ? "?" : dt.getFullyQualifiedName();
            }
        };

    }

    private static String parameters(JavaType.Method method) {
        StringJoiner joiner = new StringJoiner(",");
        List<JavaType> params = method.getParameterTypes();
        if (params != null) {
            for (JavaType javaType : params) {
                joiner.add(javaType == null ? "?" : javaType.toString());
            }
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
        JavaType rt = method.getReturnType();
        return rt == null ? "" : rt.toString();
    }

    /**
     * Unwrap parameterized/array/generic-wrapped types down to the underlying fully
     * qualified class. Delegates to {@link TypeUtils#asFullyQualified(JavaType)} for
     * the common cases; handles array element unwrap explicitly (TypeUtils treats
     * {@code JavaType.Array} as not-fully-qualified).
     */
    static JavaType.@Nullable FullyQualified extractFullyQualified(@Nullable JavaType type) {
        if (type instanceof JavaType.Array) {
            return extractFullyQualified(((JavaType.Array) type).getElemType());
        }
        return TypeUtils.asFullyQualified(type);
    }
}
