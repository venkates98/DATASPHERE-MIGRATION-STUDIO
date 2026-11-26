package com.datasphere.migration.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import com.datasphere.migration.db.DatabaseType;
import com.datasphere.migration.mapping.type.CompatibilityLevel;
import com.datasphere.migration.mapping.type.Transformation;
import com.datasphere.migration.mapping.type.TypeCategory;
import com.datasphere.migration.mapping.type.TypeCompatibility;
import com.datasphere.migration.mapping.type.TypeCompatibilityChecker;
import com.datasphere.migration.mapping.type.TypeNormalizer;
import com.datasphere.migration.metadata.ColumnMetadata;
import com.datasphere.migration.support.TestSchemas;
import java.sql.Types;
import org.junit.jupiter.api.Test;

class TypeCompatibilityTest {

    private static ColumnMetadata column(String type, int jdbc, Integer size, Integer scale) {
        return TestSchemas.table("S", "T").col("C", type, jdbc, size, scale, true).build().columns().get(0);
    }

    private static TypeCompatibility check(ColumnMetadata source, DatabaseType sourceDb, ColumnMetadata target,
                                           DatabaseType targetDb) {
        return TypeCompatibilityChecker.check(TypeNormalizer.normalize(source, sourceDb),
                TypeNormalizer.normalize(target, targetDb));
    }

    private static TypeCompatibility sqlServerToOracle(ColumnMetadata source, ColumnMetadata target) {
        return check(source, DatabaseType.SQLSERVER, target, DatabaseType.ORACLE);
    }

    @Test
    void vendorTypesAreReducedToNeutralCategories() {
        assertThat(TypeNormalizer.normalize(column("int", Types.INTEGER, 10, 0), DatabaseType.SQLSERVER).category())
                .isEqualTo(TypeCategory.INTEGER);
        assertThat(TypeNormalizer.normalize(column("nvarchar", Types.NVARCHAR, 50, null), DatabaseType.SQLSERVER)
                .category()).isEqualTo(TypeCategory.STRING);
        assertThat(TypeNormalizer.normalize(column("NUMBER", Types.NUMERIC, 12, 2), DatabaseType.ORACLE).category())
                .isEqualTo(TypeCategory.DECIMAL);
        assertThat(TypeNormalizer.normalize(column("VARCHAR2", Types.VARCHAR, 50, null), DatabaseType.ORACLE)
                .category()).isEqualTo(TypeCategory.STRING);
        assertThat(TypeNormalizer.normalize(column("datetime", Types.TIMESTAMP, 23, 3), DatabaseType.SQLSERVER)
                .category()).isEqualTo(TypeCategory.TIMESTAMP);
        assertThat(TypeNormalizer.normalize(column("binary", Types.BINARY, 16, null), DatabaseType.SQLSERVER)
                .category()).isEqualTo(TypeCategory.BINARY);
    }

    @Test
    void intToNumberIsCompatible() {
        TypeCompatibility c = sqlServerToOracle(column("int", Types.INTEGER, 10, 0),
                column("NUMBER", Types.NUMERIC, 10, 0));
        assertThat(c.level()).isIn(CompatibilityLevel.IDENTICAL, CompatibilityLevel.COMPATIBLE);
        assertThat(c.sizeFactor()).isEqualTo(1.0);
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    void bigintFitsOnlyInANumberWithEnoughDigits() {
        ColumnMetadata bigint = column("bigint", Types.BIGINT, 19, 0);

        TypeCompatibility roomy = sqlServerToOracle(bigint, column("NUMBER", Types.NUMERIC, 19, 0));
        assertThat(roomy.sizeFactor()).isEqualTo(1.0);
        assertThat(roomy.warnings()).isEmpty();

        TypeCompatibility tight = sqlServerToOracle(bigint, column("NUMBER", Types.NUMERIC, 10, 0));
        assertThat(tight.sizeFactor()).isLessThan(1.0);
        assertThat(String.join(" ", tight.warnings())).isNotBlank();
    }

    @Test
    void varcharToVarchar2ComparesLength() {
        TypeCompatibility fits = sqlServerToOracle(column("varchar", Types.VARCHAR, 100, null),
                column("VARCHAR2", Types.VARCHAR, 100, null));
        assertThat(fits.sizeFactor()).isEqualTo(1.0);
        assertThat(fits.warnings()).isEmpty();

        TypeCompatibility shorter = sqlServerToOracle(column("varchar", Types.VARCHAR, 200, null),
                column("VARCHAR2", Types.VARCHAR, 100, null));
        assertThat(shorter.sizeFactor()).isLessThan(1.0);
        assertThat(shorter.warnings()).isNotEmpty();
    }

    @Test
    void nvarcharToNvarchar2ComparesLengthAndKeepsUnicode() {
        TypeCompatibility c = sqlServerToOracle(column("nvarchar", Types.NVARCHAR, 200, null),
                column("NVARCHAR2", Types.NVARCHAR, 200, null));
        assertThat(c.sizeFactor()).isEqualTo(1.0);
        assertThat(c.warnings()).isEmpty();

        TypeCompatibility narrower = sqlServerToOracle(column("nvarchar", Types.NVARCHAR, 200, null),
                column("NVARCHAR2", Types.NVARCHAR, 50, null));
        assertThat(narrower.sizeFactor()).isLessThan(1.0);
    }

    @Test
    void unicodeSourceIntoNonUnicodeTargetIsWarned() {
        TypeCompatibility c = sqlServerToOracle(column("nvarchar", Types.NVARCHAR, 100, null),
                column("VARCHAR2", Types.VARCHAR, 100, null));
        assertThat(String.join(" ", c.warnings()).toLowerCase()).containsAnyOf("unicode", "character");
    }

    @Test
    void sqlServerTimestampIsARowVersionOnlyWhenTheDriverReportsItAsBinary() {
        assertThat(TypeNormalizer.normalize(column("timestamp", Types.BINARY, 8, null), DatabaseType.SQLSERVER)
                .category()).isEqualTo(TypeCategory.BINARY);
        assertThat(TypeNormalizer.normalize(column("timestamp", Types.TIMESTAMP, 26, 6), DatabaseType.SQLSERVER)
                .category()).isEqualTo(TypeCategory.TIMESTAMP);
    }

    @Test
    void datetimeToTimestampIsCompatible() {
        TypeCompatibility c = sqlServerToOracle(column("datetime", Types.TIMESTAMP, 23, 3),
                column("TIMESTAMP(6)", Types.TIMESTAMP, 11, 6));
        assertThat(c.level()).isIn(CompatibilityLevel.IDENTICAL, CompatibilityLevel.COMPATIBLE);
        assertThat(c.warnings()).isEmpty();
    }

    @Test
    void decimalPrecisionAndScaleAreChecked() {
        ColumnMetadata decimal = column("decimal", Types.DECIMAL, 12, 2);

        TypeCompatibility same = sqlServerToOracle(decimal, column("NUMBER", Types.NUMERIC, 12, 2));
        assertThat(same.sizeFactor()).isEqualTo(1.0);
        assertThat(same.warnings()).isEmpty();

        TypeCompatibility lessPrecision = sqlServerToOracle(decimal, column("NUMBER", Types.NUMERIC, 8, 2));
        assertThat(lessPrecision.sizeFactor()).isLessThan(1.0);
        assertThat(lessPrecision.warnings()).isNotEmpty();

        TypeCompatibility lessScale = sqlServerToOracle(decimal, column("NUMBER", Types.NUMERIC, 12, 0));
        assertThat(lessScale.sizeFactor()).isLessThan(1.0);
        assertThat(String.join(" ", lessScale.warnings()).toLowerCase()).containsAnyOf("decimal", "scale", "rounded");
    }

    @Test
    void binaryToBlobOrRawIsConditional() {
        ColumnMetadata varbinary = column("varbinary", Types.VARBINARY, 2000, null);

        TypeCompatibility raw = sqlServerToOracle(varbinary, column("RAW", Types.VARBINARY, 2000, null));
        assertThat(raw.level()).isNotEqualTo(CompatibilityLevel.INCOMPATIBLE);

        TypeCompatibility blob = sqlServerToOracle(varbinary, column("BLOB", Types.BLOB, null, null));
        assertThat(blob.level()).isNotEqualTo(CompatibilityLevel.INCOMPATIBLE);

        TypeCompatibility smallRaw = sqlServerToOracle(column("varbinary", Types.VARBINARY, 8000, null),
                column("RAW", Types.VARBINARY, 100, null));
        assertThat(smallRaw.sizeFactor()).isLessThan(1.0);
    }

    @Test
    void incompatibleTypesAreFlaggedAndSuggestAConversionWhereOneExists() {
        TypeCompatibility toDate = sqlServerToOracle(column("int", Types.INTEGER, 10, 0),
                column("DATE", Types.TIMESTAMP, 7, 0));
        assertThat(toDate.level()).isEqualTo(CompatibilityLevel.INCOMPATIBLE);
        assertThat(toDate.warnings()).isNotEmpty();

        TypeCompatibility textToNumber = sqlServerToOracle(column("varchar", Types.VARCHAR, 20, null),
                column("NUMBER", Types.NUMERIC, 10, 0));
        assertThat(textToNumber.level()).isNotEqualTo(CompatibilityLevel.IDENTICAL);
        if (textToNumber.isIncompatible()) {
            assertThat(textToNumber.suggestedTransformation()).isEqualTo(Transformation.PARSE_NUMBER);
        }
    }

    @Test
    void aConfiguredConversionResolvesTheIncompatibility() {
        var source = TypeNormalizer.normalize(column("varchar", Types.VARCHAR, 20, null), DatabaseType.SQLSERVER);
        var target = TypeNormalizer.normalize(column("NUMBER", Types.NUMERIC, 10, 0), DatabaseType.ORACLE);

        TypeCompatibility withConversion = TypeCompatibilityChecker.check(source, target,
                Transformation.PARSE_NUMBER);

        assertThat(withConversion.isIncompatible()).isFalse();
    }

    @Test
    void conversionsOnlyApplyToTypesTheyMakeSenseFor() {
        assertThat(Transformation.PARSE_NUMBER.isApplicable(TypeCategory.STRING, TypeCategory.INTEGER)).isTrue();
        assertThat(Transformation.PARSE_NUMBER.isApplicable(TypeCategory.DATE, TypeCategory.INTEGER)).isFalse();
        assertThat(Transformation.TRIM.isApplicable(TypeCategory.STRING, TypeCategory.STRING)).isTrue();
        assertThat(Transformation.TRIM.isApplicable(TypeCategory.INTEGER, TypeCategory.STRING)).isFalse();
        assertThat(Transformation.NONE.isApplicable(TypeCategory.DATE, TypeCategory.INTEGER)).isTrue();
    }
}
