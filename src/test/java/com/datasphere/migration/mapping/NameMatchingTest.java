package com.datasphere.migration.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.mapping.engine.AbbreviationDictionary;
import com.datasphere.migration.mapping.engine.EvidenceTier;
import com.datasphere.migration.mapping.engine.NameEvidence;
import com.datasphere.migration.mapping.engine.NameMatcher;
import com.datasphere.migration.mapping.engine.NameNormalizer;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NameMatchingTest {

    private final AbbreviationDictionary dictionary = AbbreviationDictionary.load(null);
    private final NameMatcher matcher = new NameMatcher(dictionary);

    private NameEvidence compare(String a, String b) {
        return matcher.compare(matcher.profile(a), matcher.profile(b));
    }

    @ParameterizedTest
    @CsvSource({"EMP_NM,EMPLOYEE_NAME", "CUST_ID,CUSTOMER_ID", "DEPT_CD,DEPARTMENT_CODE", "ACC_NO,ACCOUNT_NUMBER",
            "BAL_AMT,BALANCE_AMOUNT", "TXN_DT,TRANSACTION_DATE", "DOB,DATE_OF_BIRTH", "ADDR,ADDRESS",
            "QTY,QUANTITY", "STS,STATUS", "MOB_NO,MOBILE_NUMBER"})
    void configuredAbbreviationsMatchTheirExpansions(String source, String target) {
        NameEvidence e = compare(source, target);
        assertThat(e.tier().isFullyResolved()).as(source + " vs " + target).isTrue();
        assertThat(e.reasons()).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({"EmployeeName,EMPLOYEE_NAME", "employee-name,EMPLOYEE_NAME", "Employee Name,employee_name",
            "EMPLOYEE_NAME,EMPLOYEE_NAME"})
    void exactMatchIgnoresCaseAndSeparators(String a, String b) {
        assertThat(compare(a, b).tier()).isEqualTo(EvidenceTier.EXACT);
    }

    @Test
    void reasonsAreNotRepeated() {
        List<String> reasons = compare("CUST_ID", "CUSTOMER_ID").reasons();
        assertThat(reasons).doesNotHaveDuplicates();
    }

    @Test
    void ambiguousAbbreviationsAreNotExpandedBlindly() {
        // A lone ID, CD or NO is a word in its own right; it must not be expanded (empty = left as it is).
        assertThat(dictionary.expand("ID", 0, 1)).isEmpty();
        assertThat(dictionary.expand("ID", 1, 2)).containsExactly("IDENTIFIER");
        assertThat(dictionary.expand("CD", 0, 1)).isEmpty();
        assertThat(dictionary.expand("CD", 1, 2)).containsExactly("CODE");
        assertThat(dictionary.expand("NO", 0, 2)).isEmpty();
        assertThat(dictionary.expand("NO", 1, 2)).containsExactly("NUMBER");
    }

    @Test
    void unrelatedNamesDoNotMatch() {
        assertThat(compare("EMP_NM", "ACCOUNT_BALANCE").tier().canSuggest()).isFalse();
        assertThat(compare("HIRE_DT", "EMPLOYEE_NAME").tier().canSuggest()).isFalse();
    }

    @Test
    void similarityAloneIsSupportingEvidenceNotASuggestion() {
        NameEvidence e = compare("CUSTMER", "CUSTOMER");
        assertThat(e.tier()).isEqualTo(EvidenceTier.SIMILARITY_ONLY);
        assertThat(e.tier().canSuggest()).isFalse();
        assertThat(e.similarity()).isBetween(0.7, 1.0);
    }

    @Test
    void strongSynonymsMatchButWeakOnesAreOnlyPartial() {
        assertThat(compare("TELEPHONE", "PHONE").tier().isFullyResolved()).isTrue();
        NameEvidence weak = compare("CUSTOMER_CODE", "CUSTOMER_NUMBER");
        assertThat(weak.tier().isFullyResolved()).isFalse();
    }

    @Test
    void dictionaryCanBeExtendedWithoutCodeChanges() {
        Properties extra = new Properties();
        extra.setProperty("abbr.XYZ", "WIDGET");
        extra.setProperty("synonym.strong.99", "GADGET,WIDGET");
        AbbreviationDictionary extended = AbbreviationDictionary.fromProperties(extra);
        NameMatcher custom = new NameMatcher(extended);

        NameEvidence e = custom.compare(custom.profile("XYZ_COUNT"), custom.profile("WIDGET_COUNT"));

        assertThat(e.tier().isFullyResolved()).isTrue();
        assertThat(extended.areStrongSynonyms("GADGET", "WIDGET")).isTrue();
    }

    @Test
    void namesAreNormalisedPredictably() {
        assertThat(NameNormalizer.tokens("employeeName")).containsExactly("EMPLOYEE", "NAME");
        assertThat(NameNormalizer.tokens("EMP_NM")).containsExactly("EMP", "NM");
        assertThat(NameNormalizer.tokens("Order-Date 2")).containsExactly("ORDER", "DATE", "2");
        assertThat(NameNormalizer.key("Employee_Name")).isEqualTo(NameNormalizer.key("employeeName"));
    }

    @Test
    void scoringWeightsMustAddUpToOneHundred() {
        MappingProperties defaults = MappingProperties.defaults();
        var w = defaults.weights();
        assertThat(w.exact() + w.synonym() + w.similarity() + w.dataType() + w.length() + w.constraint())
                .isEqualTo(100.0);
        assertThat(w.exact()).isEqualTo(40.0);
        assertThat(w.synonym()).isEqualTo(25.0);
        assertThat(w.similarity()).isEqualTo(15.0);
        assertThat(w.dataType()).isEqualTo(10.0);
        assertThat(w.length()).isEqualTo(5.0);
        assertThat(w.constraint()).isEqualTo(5.0);

        assertThatThrownBy(() -> new MappingProperties(new MappingProperties.Weights(50, 25, 15, 10, 5, 5), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exactMatchEarnsTheFullNameBlock() {
        MappingProperties p = MappingProperties.defaults();
        NameEvidence exact = compare("EMPLOYEE_NAME", "EMPLOYEE_NAME");
        assertThat(exact.points(p.weights(), p.thresholds()).total()).isEqualTo(80.0);
        // an abbreviation match never reaches the exact tier's points
        NameEvidence abbreviated = compare("EMP_NM", "EMPLOYEE_NAME");
        assertThat(abbreviated.points(p.weights(), p.thresholds()).total()).isLessThan(80.0);
        assertThat(List.of(exact.tier(), abbreviated.tier())).doesNotHaveDuplicates();
    }
}
