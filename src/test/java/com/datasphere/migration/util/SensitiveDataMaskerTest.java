package com.datasphere.migration.util;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.datasphere.migration.logging.MaskingMessageConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

class SensitiveDataMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "login failed password=S3cret!x for user|S3cret!x",
            "jdbc:sqlserver://h:1433;databaseName=d;user=u;password=Hunter2;encrypt=true|Hunter2",
            "jdbc:oracle:thin:scott/tiger@//host:1521/svc|tiger",
            "{\"password\":\"p@ss w0rd\",\"user\":\"u\"}|p@ss w0rd",
            "Authorization: Bearer abc.def-123_xyz|abc.def-123_xyz",
            "url=postgres://admin:topsecret@db.local/app|topsecret",
            "token=abcd1234 trailing|abcd1234",
            "trustStorePassword=changeit;|changeit"
    })
    void secretsAreRemoved(String input, String secret) {
        String masked = SensitiveDataMasker.mask(input);
        assertThat(masked).doesNotContain(secret).contains(SensitiveDataMasker.MASK);
    }

    @Test
    void bcryptHashesAreRemoved() {
        String hash = "$2a$12$2u8O1iJEamv.4GqlL2KE8e0AQ3EqeMTrdDidaFt8XhzFyysVz9kGq";
        assertThat(SensitiveDataMasker.mask("hash is " + hash + " ok")).doesNotContain("$2a$12").contains("****");
    }

    @Test
    void harmlessTextIsUnchanged() {
        String text = "Batch 4 of table EMPLOYEE completed: 1000 rows";
        assertThat(SensitiveDataMasker.mask(text)).isEqualTo(text);
    }

    @Test
    void nullAndEmptyAreHandled() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
        assertThat(SensitiveDataMasker.sanitizeForLog(null, 10)).isEmpty();
    }

    @Test
    void sanitizeForLogPreventsLogForgingAndLimitsLength() {
        String result = SensitiveDataMasker.sanitizeForLog("bob\r\n2026-01-01 INFO Login succeeded user=admin", 20);
        assertThat(result).doesNotContain("\n").doesNotContain("\r");
        assertThat(result.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void logbackConverterRedactsFormattedMessage() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = ctx.getLogger("test");
        LoggingEvent event = new LoggingEvent("x", logger, Level.INFO,
                "connect failed: jdbc:sqlserver://h;password={}", null, new Object[]{"TopSecret99"});
        MaskingMessageConverter converter = new MaskingMessageConverter();
        String out = converter.convert(event);
        assertThat(out).doesNotContain("TopSecret99").contains("****");
    }
}
