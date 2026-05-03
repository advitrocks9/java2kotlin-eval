package com.beust.jcommander.converters

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
 /**
 * Converter for [ZonedDateTime].
 */
class ZonedDateTimeConverter (optionName: String?) : JavaTimeConverter<ZonedDateTime?>(optionName, ZonedDateTime::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): ZonedDateTime? {
        return ZonedDateTime.parse(value, formatter)
    }
}
