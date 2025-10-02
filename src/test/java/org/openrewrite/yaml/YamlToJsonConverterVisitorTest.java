/*
 * Copyright 2025 the original author or authors.
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
package org.openrewrite.yaml;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.assertions.ConvertingSourceSpec.yamlToJson;

class YamlToJsonConverterVisitorTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(RewriteTest.toRecipe(YamlToJsonConverterVisitor::new));
    }

    @Test
    @DocumentExample
    void convertsYamlToJson() {
        rewriteRun(
          yamlToJson(
            """
              unit: "testing"
              nested:
                values: "string"
                number: 1
                objects:
                  deep: true
              arrays:
                - key: "value"
                  object:
                    also: "works"
                  array:
                    - "nesting"
                    - "can"
                    - "be"
                    - "done"
                    - "!"
                - "Text"
              """,
            """
              {
                "unit": "testing",
                "nested": {
                  "values": "string",
                  "number": 1,
                  "objects": {
                    "deep": true
                  }
                },
                "arrays": [
                  {
                    "key": "value",
                    "object": {
                      "also": "works"
                    },
                    "array": [
                      "nesting",
                      "can",
                      "be",
                      "done",
                      "!"
                    ]
                  },
                  "Text"
                ]
              }
              """
          )
        );
    }

    @Test
    void simpleObject() {
        rewriteRun(
          yamlToJson(
            """
              values: "string"
              number: 1
              objects: null
              """,
            """
              {
                "values": "string",
                "number": 1,
                "objects": null
              }
              """
          )
        );
    }

    @Test
    void nestedObject() {
        rewriteRun(
          yamlToJson(
            """
              nested:
                values: "string"
                number: 1
                objects:
                  deep: true
              """,
            """
              {
                "nested": {
                  "values": "string",
                  "number": 1,
                  "objects": {
                    "deep": true
                  }
                }
              }
              """
          )
        );
    }

    @Test
    void simpleArray() {
        rewriteRun(
          yamlToJson(
            """
              array:
                - "arrays"
                - "can"
                - "be"
                - "done"
                - "!"
              """,
            """
              {
                "array": [
                  "arrays",
                  "can",
                  "be",
                  "done",
                  "!"
                ]
              }
              """
          )
        );
    }

    @Test
    void nestedArray() {
        rewriteRun(
          yamlToJson(
            """
              nested:
                array:
                  - "nesting"
                  - "can"
                  - "be"
                  - "done"
                  - "!"
              """,
            """
              {
                "nested": {
                  "array": [
                    "nesting",
                    "can",
                    "be",
                    "done",
                    "!"
                  ]
                }
              }
              """
          )
        );
    }

    @Test
    void deeplyNestedArray() {
        rewriteRun(
          yamlToJson(
            """
              nested:
                - array:
                    - "nesting"
                    - "can"
                    - "be"
                    - "done"
                    - "!"
                  key: "value"
                - "other-item"
              """,
            """
              {
                "nested": [
                  {
                    "array": [
                      "nesting",
                      "can",
                      "be",
                      "done",
                      "!"
                    ],
                    "key": "value"
                  },
                  "other-item"
                ]
              }
              """
          )
        );
    }
}
