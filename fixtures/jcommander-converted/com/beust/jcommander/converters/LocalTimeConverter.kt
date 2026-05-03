package com.beust.jcommander.converters

import java.time.LocalTime
import java.time.format.DateTimeFormatter
 /**
 * Converter for [LocalTime].
 */
class LocalTimeConverter (optionName: String?) : JavaTimeConverter<LocalTime?>(optionName, LocalTime::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_LOCAL_TIME)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): LocalTime? {
        return LocalTime.parse(value, formatter)
    }
}
