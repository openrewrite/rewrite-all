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

import org.junit.jupiter.api.Test;
import org.openrewrite.table.CallGraph;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"UnusedAssignment", "DataFlowIssue", "InfiniteRecursion"})
class FindCallGraphTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCallGraph(true));
    }

    @DocumentExample
    @Test
    void missingMethodMarked() {
        rewriteRun(
          spec -> spec.typeValidationOptions(TypeValidation.none()),
          //language=java
          java(
                """
            class A {
                String s = foo();
            }
            """,
            """
            class A {
                String s = /*~~(Method type not found)~~>*/foo();
            }
            """
          )
        );
    }

    @Test
    void findUniqueCallsPerDeclaration() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
                "main",
                "Test",
                "test",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "java.io.PrintStream",
                "println",
                "java.lang.String",
                CallGraph.ResourceType.METHOD,
                "void"
              ),
              new CallGraph.Row(
                "main",
                "Test",
                "test2",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "java.io.PrintStream",
                "println",
                "java.lang.String",
                CallGraph.ResourceType.METHOD,
                "void"
              )
            )
          ),
          mavenProject("project", srcMainJava(
            //language=java
            java("""
              class Test {
                  void test() {
                      System.out.println("Hello");
                      System.out.println("Hello");
                  }

                  void test2() {
                      System.out.println("Hello");
                      System.out.println("Hello");
                  }
              }
              """
            )
          ))
        );
    }

    @Test
    void filterStdLib() {
        rewriteRun(
          spec -> spec
            .recipe(new FindCallGraph(false))
            .dataTable(CallGraph.Row.class, row ->
              assertThat(row).containsExactly(
                new CallGraph.Row(
                  "unknown",
                  "Test",
                  "test",
                  "",
                  CallGraph.ResourceType.METHOD,
                  CallGraph.ResourceAction.CALL,
                  "Test",
                  "test",
                  "",
                  CallGraph.ResourceType.METHOD,
                  "void"
                )
              )),
          //language=java
          java(
                """
              import java.util.List;
              import java.util.ArrayList;
              class Test {
                  void test() {
                      System.out.println("Hello");
                      List<String> s = new ArrayList<>();
                      test();
                  }
              }
              """
          )
        );
    }

    @Test
    void staticInitializer() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
                "unknown",
                "Scratch",
                "<clinit>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "Scratch",
                "bar",
                "",
                CallGraph.ResourceType.METHOD,
                "int"
              ),
              new CallGraph.Row(
                "unknown",
                "Scratch",
                "<clinit>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "Scratch",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "void"
              )
            )
          ),
          //language=java
          java(
                """
            class Scratch {
                static int i = bar();
                static {
                    foo();
                }
                public static void foo() {}
                public static int bar() { return 1; }
            }
            """
          )
        );
    }

    @Test
    void initializer() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
                "unknown",
                "Scratch",
                "<init>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "Scratch",
                "bar",
                "",
                CallGraph.ResourceType.METHOD,
                "int"
              ),
              new CallGraph.Row(
                "unknown",
                "Scratch",
                "<init>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "Scratch",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "int"
              )
            )
          ),
          //language=java
          java(
                """
            class Scratch {
                int i = bar();
                int j;
                {
                    j = foo();
                    j = foo();
                }
                public static int foo() { return 1;}
                public static int bar() { return 1; }
            }
            """
          )
        );
    }

    @Test
    void innerClass() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              // Local variable `C c` emits a REFERENCE edge for the declared type.
              new CallGraph.Row(
                "unknown",
                "A$B",
                "b",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.REFERENCE,
                "A$C",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              ),
              new CallGraph.Row(
                "unknown",
                "A$B",
                "b",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A$C",
                "<constructor>",
                "",
                CallGraph.ResourceType.CONSTRUCTOR,
                "A$C"
              ),
              new CallGraph.Row(
                "unknown",
                "A$B",
                "b",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A$C",
                "c",
                "",
                CallGraph.ResourceType.METHOD,
                "void"
              )
            )
          ),
          //language=java
          java(
                """
            class A {
                class B {
                    void b() {
                        C c = new C();
                        c.c();
                    }
                }
                class C {
                    void c() {
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void anonymousClass() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).contains(
              new CallGraph.Row(
                "unknown",
                "B",
                "call",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A",
                "method",
                "",
                CallGraph.ResourceType.METHOD,
                "void"
              ),
              new CallGraph.Row(
                "unknown",
                "B",
                "<init>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "B$1",
                "<constructor>",
                "",
                CallGraph.ResourceType.CONSTRUCTOR,
                "B$1"
              ),
              new CallGraph.Row(
                "unknown",
                "B$1",
                "method",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "java.io.PrintStream",
                "println",
                "java.lang.String",
                CallGraph.ResourceType.METHOD,
                "void"
              )
            )
          ),
          //language=java
          java(
                """
            class A {
                public void method() {}
            }
            class B {
                private A a = new A() {
                    @Override
                    public void method() {
                        System.out.println("Hello, world!");
                    }
                };
                void call() {
                    a.method();
                }
            }
            """
          )
        );
    }

    @Test
    void companionObject() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              // Parameter type `Array<String>` emits a REFERENCE edge for the
              // array element type attributed to the enclosing method.
              new CallGraph.Row(
                "unknown",
                "A$Companion",
                "main",
                "kotlin.Array<kotlin.String>",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.REFERENCE,
                "kotlin.Array",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              ),
              new CallGraph.Row(
                "unknown",
                "A$Companion",
                "main",
                "kotlin.Array<kotlin.String>",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "kotlin.io.ConsoleKt",
                "println",
                "kotlin.Any",
                CallGraph.ResourceType.METHOD,
                "void"
              )
            )
          ),
          //language=kotlin
          kotlin(
                """
            class A {
                companion object {
                    @JvmStatic
                    fun main(args: Array<String>) {
                        println("Hello, world!")
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void fieldDeclarationInitialization() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              // Field type reference: attributed to the class (field declarations
              // have no enclosing method).
              new CallGraph.Row(
                "unknown",
                "A",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                CallGraph.ResourceAction.REFERENCE,
                "java.lang.String",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              ),
              new CallGraph.Row(
                "unknown",
                "A",
                "<init>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "java.lang.String"
              ),
              new CallGraph.Row(
                "unknown",
                "A",
                "<clinit>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "java.lang.String"
              ),
              // Return type reference on `foo()`.
              new CallGraph.Row(
                "unknown",
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.REFERENCE,
                "java.lang.String",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              )
            )
          ),
          //language=java
          java(
                """
            class A {
                String instanceField = foo();
                static String staticField = foo();
                public static String foo() { return "foo"; }
            }
            """
          )
        );
    }

    @Test
    void classLevelTypeReferences() {
        // Imports, extends-adjacent type uses via field declarations, field types, and
        // method parameter / return types should all emit REFERENCE edges so reachability
        // closures can see "this scope depends on that class" without invoking a specific
        // method on the referenced class.
        rewriteRun(
          spec -> spec
            .recipe(new FindCallGraph(false))
            .dataTable(CallGraph.Row.class, row -> {
                assertThat(row)
                        // Import -> class-level reference from the file to the imported class.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "", "", CallGraph.ResourceType.CLASS,
                    CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        // Field type reference: `Helper helper;` emits a class-level reference.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "", "", CallGraph.ResourceType.CLASS,
                    CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        // Parameter type reference attributed to the enclosing method.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "takesHelper", "other.Helper",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        // Return type reference attributed to the enclosing method.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "makesHelper", "",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                );
            }),
          //language=java
          java(
                """
            package other;
            public class Helper {}
            """
          ),
          //language=java
          java(
                """
            import other.Helper;
            class Test {
                Helper helper;
                void takesHelper(Helper h) {}
                Helper makesHelper() { return new Helper(); }
            }
            """
          )
        );
    }

    @Test
    void castAndInstanceOfAndClassLiteral() {
        // A cast, an instanceof check, and a class literal all emit REFERENCE edges
        // attributed to the enclosing method.
        rewriteRun(
          spec -> spec
            .recipe(new FindCallGraph(false))
            .dataTable(CallGraph.Row.class, row -> {
                assertThat(row).contains(
                  new CallGraph.Row(
                    "unknown", "Test", "check", "java.lang.Object",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                );
            }),
          //language=java
          java(
                """
            package other;
            public class Helper {}
            """
          ),
          //language=java
          java(
                """
            import other.Helper;
            class Test {
                void check(Object o) {
                    if (o instanceof Helper) {
                        Helper h = (Helper) o;
                        Class<?> c = Helper.class;
                    }
                }
            }
            """
          )
        );
    }

    @Test
    void annotationClassValueReferences() {
        // @MyAnnotation(Helper.class) on a class or method must emit a REFERENCE edge
        // from the annotated declaration to Helper, so reachability closures can see
        // class-value annotation arguments as dependencies (e.g. @RunWith, @ExtendWith,
        // custom validators that take a rule class).
        rewriteRun(
          spec -> spec
            .recipe(new FindCallGraph(false))
            .dataTable(CallGraph.Row.class, row -> {
                assertThat(row)
                        // Class-level annotation: edge attributed to the class.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "", "", CallGraph.ResourceType.CLASS,
                    CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        // Method-level annotation: edge attributed to the method.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "annotated", "",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Other", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        // Array-valued annotation argument: edges for each element.
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "manyAnnotated", "",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Helper", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                )
                        .contains(
                  new CallGraph.Row(
                    "unknown", "Test", "manyAnnotated", "",
                    CallGraph.ResourceType.METHOD, CallGraph.ResourceAction.REFERENCE,
                    "other.Other", "", "", CallGraph.ResourceType.CLASS, ""
                  )
                );
            }),
          //language=java
          java(
                """
            package other;
            public class Helper {}
            """
          ),
          //language=java
          java(
                """
            package other;
            public class Other {}
            """
          ),
          //language=java
          java(
                """
            package x;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface One {
                Class<?> value();
            }
            """
          ),
          //language=java
          java(
                """
            package x;
            import java.lang.annotation.*;
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Many {
                Class<?>[] value();
            }
            """
          ),
          //language=java
          java(
                """
            import other.Helper;
            import other.Other;
            import x.One;
            import x.Many;
            @One(Helper.class)
            class Test {
                @One(Other.class)
                void annotated() {}

                @Many({Helper.class, Other.class})
                void manyAnnotated() {}
            }
            """
          )
        );
    }

    @Test
    void initializerBlocks() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
                "unknown",
                "A",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                CallGraph.ResourceAction.REFERENCE,
                "java.lang.String",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              ),
              new CallGraph.Row(
                "unknown",
                "A",
                "<init>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "java.lang.String"
              ),
              new CallGraph.Row(
                "unknown",
                "A",
                "<clinit>",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                "java.lang.String"
              ),
              new CallGraph.Row(
                "unknown",
                "A",
                "foo",
                "",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.REFERENCE,
                "java.lang.String",
                "",
                "",
                CallGraph.ResourceType.CLASS,
                ""
              )
            )
          ),
          //language=java
          java(
                """
            class A {
                String instanceField;
                {
                    instanceField = foo();
                }
                static String staticField;
                static {
                    staticField = foo();
                }
                public static String foo() { return "foo"; }
            }
            """
          )
        );
    }
}
