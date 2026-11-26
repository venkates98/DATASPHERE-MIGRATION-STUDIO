package com.datasphere.migration.project;

import com.datasphere.migration.metadata.TableRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Request body listing the table pairs chosen for migration. */
public record TablePairRequest(@NotNull @Valid List<Pair> pairs) {

    public record Pair(@NotNull @Valid TableRef source, @NotNull @Valid TableRef target) {
    }
}
