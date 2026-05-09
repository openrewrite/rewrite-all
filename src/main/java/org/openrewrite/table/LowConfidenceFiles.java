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
 * Source files where {@code FindCallGraph} encountered missing type attribution
 * during its visit and therefore had to skip an edge it would otherwise have
 * recorded. The presence of any row for a file means the call graph for that
 * file is incomplete.
 * <p>
 * Downstream test-selection consumers should treat the recorded files (and by
 * extension the modules they belong to) as low-confidence: method-level
 * reachability cannot be trusted, so the conservative behavior is to escalate
 * to module-level test selection for those modules that actually depend on the
 * changed code.
 */
public class LowConfidenceFiles extends DataTable<LowConfidenceFiles.Row> {

    public LowConfidenceFiles(Recipe recipe) {
        super(recipe,
                "Files with incomplete call-graph extraction",
                "Source files where call-graph construction skipped an edge because the " +
                        "underlying LST had a null type. Used as a confidence signal during " +
                        "test selection: any row for a file means that file's outbound edges " +
                        "may be undercounted.");
    }

    @Value
    public static class Row {
        @Column(displayName = "Source path",
                description = "Path of the source file as recorded on the LST.")
        String sourcePath;

        @Column(displayName = "Reason",
                description = "Short label for the kind of type-attribution gap encountered " +
                        "(e.g. \"call.declaringType\", \"call.returnType\", \"reference.scope\").")
        String reason;
    }
}
