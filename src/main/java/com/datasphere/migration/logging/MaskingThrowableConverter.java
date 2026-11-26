package com.datasphere.migration.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.datasphere.migration.util.SensitiveDataMasker;

/**
 * Logback converter ({@code %maskedEx}) that redacts credentials from stack traces, because JDBC driver
 * exception messages occasionally echo connection strings.
 */
public class MaskingThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(super.convert(event));
    }
}
