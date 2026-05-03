package com.beust.jcommander.converters

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
 /**
 * Converter for [OffsetDateTime].
 */
class OffsetDateTimeConverter (optionName: String?) : JavaTimeConverter<OffsetDateTime?>(optionName, OffsetDateTime::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): OffsetDateTime? {
        return OffsetDateTime.parse(value, formatter)
    }
}
