package com.datasphere.migration.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.engine.AbbreviationDictionary;
import com.datasphere.migration.mapping.engine.EvidenceTier;
import com.datasphere.migration.mapping.engine.MappingEngine;
import com.datasphere.migration.mapping.model.ColumnMapping;
import com.datasphere.migration.mapping.model.MappingStatus;
import com.datasphere.migration.mapping.type.CompatibilityLevel;
import com.datasphere.migration.metadata.TableMetadata;
import com.datasphere.migration.support.TestSchemas;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MappingEngineTest {

    private final MappingEngine engine = new MappingEngine(MappingProperties.defaults(),
            AbbreviationDictionary.load(null));

    private Map<String, ColumnMapping> suggest(TableMetadata source, TableMetadata target) {
        return engine.suggest(source, target, DatabaseType.SQLSERVER, DatabaseType.ORACLE).stream()
                .collect(Collectors.toMap(ColumnMapping::getSourceColumn, Function.identity()));
    }

    @Test
    void identicalNamesAreMatchedRegardlessOfCaseAndSeparators() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("customerName", "varchar", Types.VARCHAR, 50, null, true)
                .col("Order-Date", "datetime", Types.TIMESTAMP, 23, 3, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("CUSTOMER_NAME", "VARCHAR2", Types.VARCHAR, 50, null, true)
                .col("ORDER_DATE", "TIMESTAMP(6)", Types.TIMESTAMP, 11, 6, true).build();

        Map<String, ColumnMapping> result = suggest(source, target);

        assertThat(result.get("customerName").getTargetColumn()).isEqualTo("CUSTOMER_NAME");
        assertThat(result.get("customerName").getEvidence()).isEqualTo(EvidenceTier.EXACT);
        assertThat(result.get("customerName").getScore()).isGreaterThanOrEqualTo(90);
        assertThat(result.get("Order-Date").getTargetColumn()).isEqualTo("ORDER_DATE");
    }

    @Test
    void abbreviationsAreExpandedAndExplained() {
        Map<String, ColumnMapping> result = suggest(TestSchemas.sqlServerEmployee(), TestSchemas.oracleEmployee());

        ColumnMapping name = result.get("EMP_NM");
        assertThat(name.getTargetColumn()).isEqualTo("EMPLOYEE_NAME");
        assertThat(name.getStatus()).isEqualTo(MappingStatus.SUGGESTED);
        assertThat(name.getEvidence()).isEqualTo(EvidenceTier.ABBREVIATION_OR_SYNONYM);
        assertThat(String.join(" ", name.getReasons())).containsIgnoringCase("EMP").containsIgnoringCase("EMPLOYEE");

        assertThat(result.get("EMP_ID").getTargetColumn()).isEqualTo("EMPLOYEE_ID");
        assertThat(result.get("DOB").getTargetColumn()).isEqualTo("DATE_OF_BIRTH");
        assertThat(result.get("DEPT_CD").getTargetColumn()).isEqualTo("DEPARTMENT_CODE");
        assertThat(result.get("HIRE_DT").getTargetColumn()).isEqualTo("HIRE_DATE");
        assertThat(result.get("EMAIL").getTargetColumn()).isEqualTo("EMAIL");
    }

    @Test
    void partialNameMatchNeedsReviewInsteadOfBeingTrusted() {
        Map<String, ColumnMapping> result = suggest(TestSchemas.sqlServerEmployee(), TestSchemas.oracleEmployee());

        ColumnMapping salary = result.get("SAL");
        assertThat(salary.getTargetColumn()).isEqualTo("MONTHLY_SALARY");
        assertThat(salary.getStatus()).isEqualTo(MappingStatus.NEEDS_REVIEW);
        assertThat(salary.getScore()).isLessThan(90);
    }

    @Test
    void synonymsAreAcceptedAsEvidence() {
        Map<String, ColumnMapping> result = suggest(TestSchemas.sqlServerCustomer(), TestSchemas.oracleCustomer());

        assertThat(result.get("CUST_ID").getTargetColumn()).isEqualTo("CUSTOMER_ID");
        assertThat(result.get("CUST_NM").getTargetColumn()).isEqualTo("CUSTOMER_NAME");
        assertThat(result.get("ADDR").getTargetColumn()).isEqualTo("ADDRESS");
        assertThat(result.get("STS").getTargetColumn()).isEqualTo("STATUS");
    }

    @Test
    void similarSpellingAloneIsNeverEnoughToSuggest() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("CUSTMER", "varchar", Types.VARCHAR, 50, null, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("CUSTOMER", "VARCHAR2", Types.VARCHAR, 50, null, true).build();

        ColumnMapping m = suggest(source, target).get("CUSTMER");

        assertThat(m.getTargetColumn()).isNull();
        assertThat(m.getStatus()).isEqualTo(MappingStatus.UNMAPPED);
    }

    @Test
    void fullBankingSchemasMapWithoutInventingColumns() {
        List<TableMetadata[]> pairs = List.of(
                new TableMetadata[]{TestSchemas.sqlServerEmployee(), TestSchemas.oracleEmployee()},
                new TableMetadata[]{TestSchemas.sqlServerCustomer(), TestSchemas.oracleCustomer()},
                new TableMetadata[]{TestSchemas.sqlServerAccount(), TestSchemas.oracleAccount()},
                new TableMetadata[]{TestSchemas.sqlServerTransaction(), TestSchemas.oracleTransaction()});
        for (TableMetadata[] pair : pairs) {
            List<ColumnMapping> mappings = engine.suggest(pair[0], pair[1], DatabaseType.SQLSERVER,
                    DatabaseType.ORACLE);
            assertThat(mappings).hasSize(pair[0].columns().size());
            for (ColumnMapping m : mappings) {
                assertThat(pair[0].column(m.getSourceColumn())).isPresent();
                if (m.getTargetColumn() != null) {
                    assertThat(pair[1].column(m.getTargetColumn())).isPresent();
                }
            }
            long distinctTargets = mappings.stream().map(ColumnMapping::getTargetColumn)
                    .filter(t -> t != null).distinct().count();
            long mapped = mappings.stream().filter(m -> m.getTargetColumn() != null).count();
            assertThat(distinctTargets).as("one-to-one for " + pair[0].name()).isEqualTo(mapped);
        }

        Map<String, ColumnMapping> account = suggest(TestSchemas.sqlServerAccount(), TestSchemas.oracleAccount());
        assertThat(account.get("ACC_NO").getTargetColumn()).isEqualTo("ACCOUNT_NUMBER");
        assertThat(account.get("BAL_AMT").getTargetColumn()).isEqualTo("BALANCE_AMOUNT");
        assertThat(account.get("CUST_ID").getTargetColumn()).isEqualTo("CUSTOMER_ID");

        Map<String, ColumnMapping> txn = suggest(TestSchemas.sqlServerTransaction(), TestSchemas.oracleTransaction());
        assertThat(txn.get("TXN_ID").getTargetColumn()).isEqualTo("TRANSACTION_ID");
        assertThat(txn.get("AMT").getTargetColumn()).isEqualTo("AMOUNT");
        assertThat(txn.get("DESCRIPTION").getTargetColumn()).isEqualTo("DESCRIPTION");
    }

    @Test
    void closeCandidatesAreReportedAsAmbiguousAndNothingIsSelected() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("PHONE", "varchar", Types.VARCHAR, 20, null, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("PHONE_HOME", "VARCHAR2", Types.VARCHAR, 20, null, true)
                .col("PHONE_WORK", "VARCHAR2", Types.VARCHAR, 20, null, true).build();

        ColumnMapping m = suggest(source, target).get("PHONE");

        assertThat(m.getStatus()).isEqualTo(MappingStatus.AMBIGUOUS);
        assertThat(m.getTargetColumn()).isNull();
        assertThat(String.join(" ", m.getReasons())).contains("PHONE_HOME").contains("PHONE_WORK");
    }

    @Test
    void aTargetColumnIsNeverGivenToTwoSourceColumns() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("CUSTOMER_NAME", "varchar", Types.VARCHAR, 50, null, true)
                .col("CUSTOMER_NM", "varchar", Types.VARCHAR, 50, null, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("CUSTOMER_NAME", "VARCHAR2", Types.VARCHAR, 50, null, true).build();

        List<ColumnMapping> result = engine.suggest(source, target, DatabaseType.SQLSERVER, DatabaseType.ORACLE);

        assertThat(result.stream().filter(m -> "CUSTOMER_NAME".equals(m.getTargetColumn())).count())
                .isLessThanOrEqualTo(1);
        assertThat(result.stream().filter(m -> m.getTargetColumn() == null).count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void sameNameButIncompatibleTypeIsNotAutoSelected() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("BALANCE", "varbinary", Types.VARBINARY, 16, null, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("BALANCE", "DATE", Types.TIMESTAMP, 7, 0, true).build();

        ColumnMapping m = suggest(source, target).get("BALANCE");

        assertThat(m.getTargetColumn()).isNull();
    }

    @Test
    void unmappedSourceColumnsAreListedWithAnExplanation() {
        TableMetadata source = TestSchemas.table("S", "T")
                .col("EMP_ID", "int", Types.INTEGER, 10, 0, false)
                .col("LEGACY_FLAG_X", "varchar", Types.VARCHAR, 5, null, true).build();
        TableMetadata target = TestSchemas.table("S", "T")
                .col("EMPLOYEE_ID", "NUMBER", Types.NUMERIC, 10, 0, false).build();

        Map<String, ColumnMapping> result = suggest(source, target);

        assertThat(result.get("EMP_ID").getTargetColumn()).isEqualTo("EMPLOYEE_ID");
        ColumnMapping legacy = result.get("LEGACY_FLAG_X");
        assertThat(legacy.getTargetColumn()).isNull();
        assertThat(legacy.getStatus()).isEqualTo(MappingStatus.UNMAPPED);
        assertThat(legacy.getReasons()).isNotEmpty();
    }

    @Test
    void identicalInputGivesIdenticalOutput() {
        List<ColumnMapping> first = engine.suggest(TestSchemas.sqlServerEmployee(), TestSchemas.oracleEmployee(),
                DatabaseType.SQLSERVER, DatabaseType.ORACLE);
        for (int i = 0; i < 5; i++) {
            List<ColumnMapping> again = engine.suggest(TestSchemas.sqlServerEmployee(), TestSchemas.oracleEmployee(),
                    DatabaseType.SQLSERVER, DatabaseType.ORACLE);
            for (int k = 0; k < first.size(); k++) {
                assertThat(again.get(k).getTargetColumn()).isEqualTo(first.get(k).getTargetColumn());
                assertThat(again.get(k).getScore()).isEqualTo(first.get(k).getScore());
                assertThat(again.get(k).getStatus()).isEqualTo(first.get(k).getStatus());
            }
        }
    }

    @Test
    void worksBetweenOtherDatabaseProducts() {
        TableMetadata pg = TestSchemas.table("public", "employee")
                .col("employee_name", "varchar", Types.VARCHAR, 100, null, false)
                .col("hire_date", "timestamp", Types.TIMESTAMP, 29, 6, true).build();
        TableMetadata my = TestSchemas.table("hr", "employee")
                .col("EmployeeName", "varchar", Types.VARCHAR, 100, null, false)
                .col("HireDate", "datetime", Types.TIMESTAMP, 19, 0, true).build();

        List<ColumnMapping> result = engine.suggest(pg, my, DatabaseType.POSTGRESQL, DatabaseType.MYSQL);

        assertThat(result.get(0).getTargetColumn()).isEqualTo("EmployeeName");
        assertThat(result.get(1).getTargetColumn()).isEqualTo("HireDate");
        assertThat(result.get(0).getCompatibility()).isIn(CompatibilityLevel.IDENTICAL, CompatibilityLevel.COMPATIBLE);
    }
}
