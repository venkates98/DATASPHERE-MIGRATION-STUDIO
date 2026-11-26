package com.datasphere.migration.engine;

import com.datasphere.migration.db.ConnectionRole;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.db.adapter.DatabaseAdapters;
import com.datasphere.migration.db.dialect.NativeTypeSpec;
import com.datasphere.migration.db.dialect.SqlDialect;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.type.TypeConversionService;
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.project.MigrationProject;
import com.datasphere.migration.project.TablePairMapping;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Builds CREATE TABLE / INSERT text from adapters. Nothing is executed here. */
@Service
public class SchemaSqlService {

    private final TypeConversionService conversions;

    public SchemaSqlService(TypeConversionService conversions) {
        this.conversions = conversions;
    }

    public String insertSql(MigrationProject project, TablePairMapping pair) {
        DatabaseType targetDb = project.connection(ConnectionRole.TARGET).type();
        SqlDialect dialect = DatabaseAdapters.require(targetDb).dialect();
        List<String> cols = mappedTargetColumns(pair);
        return dialect.insert(pair.getTargetTable(), cols);
    }

    public String createTableSql(MigrationProject project, TablePairMapping pair) {
        DatabaseType sourceDb = project.connection(ConnectionRole.SOURCE).type();
        DatabaseType targetDb = project.connection(ConnectionRole.TARGET).type();
        SqlDialect dialect = DatabaseAdapters.require(targetDb).dialect();
        TableMetadata source = project.metadata(ConnectionRole.SOURCE).get(pair.getSourceTable().key());
        List<SqlDialect.ColumnDdl> columns = new ArrayList<>();
        List<String> pk = new ArrayList<>();
        for (ColumnMapping m : pair.getColumns()) {
            if (!m.isMapped()) {
                continue;
            }
            ColumnMetadata src = source.column(m.getSourceColumn()).orElseThrow();
            NativeTypeSpec spec = conversions.propose(TypeNormalizer.normalize(src, sourceDb), sourceDb, targetDb)
                    .targetType();
            if (spec.unsupported()) {
                columns.add(new SqlDialect.ColumnDdl(m.getTargetColumn(), "/* unsupported: " + spec.unsupportedReason()
                        + " */", src.nullable()));
            } else {
                columns.add(new SqlDialect.ColumnDdl(m.getTargetColumn(), spec.ddl(), src.nullable()));
            }
            if (src.primaryKey()) {
                pk.add(m.getTargetColumn());
            }
        }
        return dialect.createTable(pair.getTargetTable(), columns, pk);
    }

    public static List<String> mappedTargetColumns(TablePairMapping pair) {
        List<String> cols = new ArrayList<>();
        for (ColumnMapping m : pair.getColumns()) {
            if (m.isMapped()) {
                cols.add(m.getTargetColumn());
            }
        }
        return cols;
    }

    public static List<String> mappedSourceColumns(TablePairMapping pair) {
        List<String> cols = new ArrayList<>();
        for (ColumnMapping m : pair.getColumns()) {
            if (m.isMapped()) {
                cols.add(m.getSourceColumn());
            }
        }
        return cols;
    }
}
