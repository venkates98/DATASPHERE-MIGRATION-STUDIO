package com.datasphere.migration.support;

import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.metadata.TableMetadata;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Realistic but entirely invented banking-style structures (EMPLOYEE, CUSTOMER, ACCOUNT, TRANSACTION) in
 * SQL Server and Oracle flavours, built in memory as discovered metadata. No real or sample data is used.
 */
public final class TestSchemas {

    private TestSchemas() {
    }

    /** Fluent builder for a table's discovered columns. */
    public static final class Table {
        private final String schema;
        private final String name;
        private final List<ColumnMetadata> columns = new ArrayList<>();

        private Table(String schema, String name) {
            this.schema = schema;
            this.name = name;
        }

        public Table col(String column, String typeName, int jdbcType, Integer size, Integer scale, boolean nullable) {
            columns.add(new ColumnMetadata(column, columns.size() + 1, typeName, jdbcType, size, scale, nullable,
                    false, false, false, false, false, false, null, null));
            return this;
        }

        public Table pk(String column) {
            return modify(column, c -> new ColumnMetadata(c.name(), c.ordinal(), c.typeName(), c.jdbcType(), c.size(),
                    c.scale(), false, c.autoIncrement(), c.generated(), c.hasDefault(), true, c.unique(),
                    c.foreignKey(), c.referencedTable(), c.referencedColumn()));
        }

        public Table fk(String column, String refTable, String refColumn) {
            return modify(column, c -> new ColumnMetadata(c.name(), c.ordinal(), c.typeName(), c.jdbcType(), c.size(),
                    c.scale(), c.nullable(), c.autoIncrement(), c.generated(), c.hasDefault(), c.primaryKey(),
                    c.unique(), true, refTable, refColumn));
        }

        public Table unique(String column) {
            return modify(column, c -> new ColumnMetadata(c.name(), c.ordinal(), c.typeName(), c.jdbcType(), c.size(),
                    c.scale(), c.nullable(), c.autoIncrement(), c.generated(), c.hasDefault(), c.primaryKey(), true,
                    c.foreignKey(), c.referencedTable(), c.referencedColumn()));
        }

        public Table identity(String column) {
            return modify(column, c -> new ColumnMetadata(c.name(), c.ordinal(), c.typeName(), c.jdbcType(), c.size(),
                    c.scale(), c.nullable(), true, c.generated(), c.hasDefault(), c.primaryKey(), c.unique(),
                    c.foreignKey(), c.referencedTable(), c.referencedColumn()));
        }

        private Table modify(String column, java.util.function.UnaryOperator<ColumnMetadata> change) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).name().equals(column)) {
                    columns.set(i, change.apply(columns.get(i)));
                    return this;
                }
            }
            throw new IllegalArgumentException("No column " + column);
        }

        public TableMetadata build() {
            return new TableMetadata(schema, name, "TABLE", List.copyOf(columns));
        }
    }

    public static Table table(String schema, String name) {
        return new Table(schema, name);
    }

    // ------------------------------------------------------------------ SQL Server flavour (source)

    public static TableMetadata sqlServerEmployee() {
        return table("BANK", "EMPLOYEE")
                .col("EMP_ID", "int", Types.INTEGER, 10, 0, false)
                .col("EMP_NM", "varchar", Types.VARCHAR, 100, null, false)
                .col("DOB", "date", Types.DATE, 10, null, true)
                .col("SAL", "decimal", Types.DECIMAL, 12, 2, true)
                .col("DEPT_CD", "varchar", Types.VARCHAR, 10, null, true)
                .col("HIRE_DT", "datetime", Types.TIMESTAMP, 23, 3, true)
                .col("EMAIL", "nvarchar", Types.NVARCHAR, 200, null, true)
                .pk("EMP_ID").build();
    }

    public static TableMetadata sqlServerCustomer() {
        return table("BANK", "CUSTOMER")
                .col("CUST_ID", "bigint", Types.BIGINT, 19, 0, false)
                .col("CUST_NM", "varchar", Types.VARCHAR, 120, null, false)
                .col("ADDR", "varchar", Types.VARCHAR, 250, null, true)
                .col("PH", "varchar", Types.VARCHAR, 20, null, true)
                .col("MOB", "varchar", Types.VARCHAR, 20, null, true)
                .col("STS", "char", Types.CHAR, 1, null, true)
                .pk("CUST_ID").build();
    }

    public static TableMetadata sqlServerAccount() {
        return table("BANK", "ACCOUNT")
                .col("ACC_NO", "varchar", Types.VARCHAR, 20, null, false)
                .col("CUST_ID", "bigint", Types.BIGINT, 19, 0, false)
                .col("BAL_AMT", "decimal", Types.DECIMAL, 18, 2, true)
                .col("OPEN_DT", "datetime", Types.TIMESTAMP, 23, 3, true)
                .col("STS", "char", Types.CHAR, 1, null, true)
                .pk("ACC_NO").fk("CUST_ID", "BANK.CUSTOMER", "CUST_ID").build();
    }

    public static TableMetadata sqlServerTransaction() {
        return table("BANK", "TXN")
                .col("TXN_ID", "bigint", Types.BIGINT, 19, 0, false)
                .col("ACC_NO", "varchar", Types.VARCHAR, 20, null, false)
                .col("AMT", "decimal", Types.DECIMAL, 18, 2, false)
                .col("TXN_DT", "datetime", Types.TIMESTAMP, 23, 3, true)
                .col("DESCRIPTION", "varchar", Types.VARCHAR, 200, null, true)
                .pk("TXN_ID").build();
    }

    // ------------------------------------------------------------------ Oracle flavour (target)

    public static TableMetadata oracleEmployee() {
        return table("APP", "EMPLOYEE")
                .col("EMPLOYEE_ID", "NUMBER", Types.NUMERIC, 10, 0, false)
                .col("EMPLOYEE_NAME", "VARCHAR2", Types.VARCHAR, 100, null, false)
                .col("DATE_OF_BIRTH", "DATE", Types.TIMESTAMP, 7, 0, true)
                .col("MONTHLY_SALARY", "NUMBER", Types.NUMERIC, 12, 2, true)
                .col("DEPARTMENT_CODE", "VARCHAR2", Types.VARCHAR, 10, null, true)
                .col("HIRE_DATE", "TIMESTAMP(6)", Types.TIMESTAMP, 11, 6, true)
                .col("EMAIL", "NVARCHAR2", Types.NVARCHAR, 200, null, true)
                .pk("EMPLOYEE_ID").build();
    }

    public static TableMetadata oracleCustomer() {
        return table("APP", "CUSTOMER")
                .col("CUSTOMER_ID", "NUMBER", Types.NUMERIC, 19, 0, false)
                .col("CUSTOMER_NAME", "VARCHAR2", Types.VARCHAR, 120, null, false)
                .col("ADDRESS", "VARCHAR2", Types.VARCHAR, 250, null, true)
                .col("PHONE", "VARCHAR2", Types.VARCHAR, 20, null, true)
                .col("MOBILE", "VARCHAR2", Types.VARCHAR, 20, null, true)
                .col("STATUS", "CHAR", Types.CHAR, 1, null, true)
                .pk("CUSTOMER_ID").build();
    }

    public static TableMetadata oracleAccount() {
        return table("APP", "ACCOUNT")
                .col("ACCOUNT_NUMBER", "VARCHAR2", Types.VARCHAR, 20, null, false)
                .col("CUSTOMER_ID", "NUMBER", Types.NUMERIC, 19, 0, false)
                .col("BALANCE_AMOUNT", "NUMBER", Types.NUMERIC, 18, 2, true)
                .col("OPEN_DATE", "TIMESTAMP(6)", Types.TIMESTAMP, 11, 6, true)
                .col("STATUS", "CHAR", Types.CHAR, 1, null, true)
                .pk("ACCOUNT_NUMBER").fk("CUSTOMER_ID", "APP.CUSTOMER", "CUSTOMER_ID").build();
    }

    public static TableMetadata oracleTransaction() {
        return table("APP", "TRANSACTION")
                .col("TRANSACTION_ID", "NUMBER", Types.NUMERIC, 19, 0, false)
                .col("ACCOUNT_NUMBER", "VARCHAR2", Types.VARCHAR, 20, null, false)
                .col("AMOUNT", "NUMBER", Types.NUMERIC, 18, 2, false)
                .col("TRANSACTION_DATE", "TIMESTAMP(6)", Types.TIMESTAMP, 11, 6, true)
                .col("DESCRIPTION", "VARCHAR2", Types.VARCHAR, 200, null, true)
                .pk("TRANSACTION_ID").build();
    }
}
