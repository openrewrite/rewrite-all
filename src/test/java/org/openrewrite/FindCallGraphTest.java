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
import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.kotlin.Assertions.kotlin;

@SuppressWarnings({"UnusedAssignment", "DataFlowIssue", "InfiniteRecursion"})
class FindCallGraphTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new FindCallGraph(true));
    }

    @Test
    void findUniqueCallsPerDeclaration() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
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
          //language=java
          java(
                """
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
            """)
        );
    }

    @Test
    void initializer() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
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
            """)
        );
    }

    @Test
    void innerClass() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
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
            """)
        );
    }

    @Test
    void anonymousClass() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).contains(
              new CallGraph.Row(
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
            """)
        );
    }

    @Test
    void companionObject() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
                "A$Companion",
                "main",
                "kotlin.Array<kotlin.String>",
                CallGraph.ResourceType.METHOD,
                CallGraph.ResourceAction.CALL,
                "kotlin.io.ConsoleKt",
                "println",
                "kotlin.Any",
                CallGraph.ResourceType.METHOD,
                "kotlin.Unit"
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
            """)
        );
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
            """)
        );
    }

    @Test
    void fieldDeclarationInitialization() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
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
            """)
        );
    }

    @Test
    void initializerBlocks() {
        rewriteRun(
          spec -> spec.dataTable(CallGraph.Row.class, row ->
            assertThat(row).containsExactly(
              new CallGraph.Row(
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
            """)
        );
    }
}
