package com.datasphere.migration.db.dialect;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.metadata.TableRef;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SqlDialectTest {

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void insertUsesPlaceholdersAndQuotedIdentifiers(DatabaseType type) {
        SqlDialect d = type.adapter().dialect();
        String sql = d.insert(new TableRef("HR", "EMP"), List.of("ID", "NAME"));
        assertThat(sql).contains("?").doesNotContain("${").doesNotContain("'HR'");
        assertThat(sql).contains(d.quote("EMP"));
    }

    @ParameterizedTest
    @EnumSource(DatabaseType.class)
    void createTableQuotesColumnsAndDoesNotEmbedValues(DatabaseType type) {
        SqlDialect d = type.adapter().dialect();
        String sql = d.createTable(new TableRef("APP", "T"),
                List.of(new SqlDialect.ColumnDdl("ID", "INT", false), new SqlDialect.ColumnDdl("NM", "VARCHAR(40)", true)),
                List.of("ID"));
        assertThat(sql).startsWith("CREATE TABLE").contains("PRIMARY KEY").contains(d.quote("ID"));
        assertThat(sql).doesNotContain("DROP").doesNotContain("TRUNCATE");
    }
}
