package com.beust.jcommander.converters

import java.time.OffsetTime
import java.time.format.DateTimeFormatter
 /**
 * Converter for [OffsetTime].
 */
class OffsetTimeConverter (optionName: String?) : JavaTimeConverter<OffsetTime?>(optionName, OffsetTime::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_OFFSET_TIME)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): OffsetTime? {
        return OffsetTime.parse(value, formatter)
    }
}
