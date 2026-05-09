/*
 * Copyright 2026 the original author or authors.
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

/**
 * Subset of {@link CallGraph} construction edges where the caller method is a
 * factory for the constructed class -- i.e. the method's declared return type
 * is assignable from the constructed type. Emitted by {@code FindCallGraph}
 * after checking {@code TypeUtils.isAssignableTo} against the LST.
 * <p>
 * A reachability closure consumer uses this to decide whether to propagate
 * the caller's enclosing class when fanning out on the constructed class:
 * a factory edge indicates the caller is a thin wrapper around producing the
 * target, so its enclosing class is a reasonable widening target; a
 * non-factory construction is a "uses" relationship, not "wraps," and should
 * stay narrow.
 */
public class FactoryEdges extends DataTable<FactoryEdges.Row> {

    public FactoryEdges(Recipe recipe) {
        super(recipe,
                "Factory-method construction edges",
                "Construction edges where the caller's declared return type is assignable " +
                        "from the constructed class (the caller semantically produces an instance " +
                        "of the target type).");
    }

    @Value
    public static class Row {
        @Column(displayName = "From class",
                description = "Fully-qualified name of the class containing the factory method.")
        String fromClass;

        @Column(displayName = "From method",
                description = "Simple name of the factory method.")
        String fromName;

        @Column(displayName = "To class",
                description = "Fully-qualified name of the class being constructed.")
        String toClass;
    }
}
