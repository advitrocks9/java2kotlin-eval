package com.beust.jcommander.converters

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
 /**
 * Converter for [LocalDateTime].
 */
class LocalDateTimeConverter (optionName: String?) : JavaTimeConverter<LocalDateTime?>(optionName, LocalDateTime::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): LocalDateTime? {
        return LocalDateTime.parse(value, formatter)
    }
}
