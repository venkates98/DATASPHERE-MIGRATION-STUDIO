package com.datasphere.migration.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.datasphere.migration.util.SensitiveDataMasker;

/**
 * Logback converter ({@code %maskedMsg}) that redacts credentials from the formatted log message.
 */
public class MaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(event.getFormattedMessage());
    }
}
