package com.datasphere.migration.project;

import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.metadata.TableRef;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The mapping between one source table and one target table. */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class TablePairMapping {

    private String id;
    private TableRef sourceTable;
    private TableRef targetTable;
    private List<ColumnMapping> columns = new ArrayList<>();

    public TablePairMapping() {
        // for Jackson
    }

    public TablePairMapping(String id, TableRef sourceTable, TableRef targetTable) {
        this.id = id;
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
    }

    public String getId() {
        return id;
    }

    public TableRef getSourceTable() {
        return sourceTable;
    }

    public TableRef getTargetTable() {
        return targetTable;
    }

    public List<ColumnMapping> getColumns() {
        return columns;
    }

    public void setColumns(List<ColumnMapping> columns) {
        this.columns = new ArrayList<>(columns);
    }

    public Optional<ColumnMapping> columnFor(String sourceColumn) {
        return columns.stream().filter(c -> c.getSourceColumn().equals(sourceColumn)).findFirst();
    }
}
