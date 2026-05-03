package com.beust.jcommander.converters

import java.time.Instant
import java.time.format.DateTimeFormatter
 /**
 * Converter to [Instant].
 */
class InstantConverter (optionName: String?) : JavaTimeConverter<Instant?>(optionName, Instant::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_INSTANT)
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter): Instant? {
        try {
            val ms: Long = Long.parseLong(value)
            return Instant.ofEpochMilli(ms)
        }catch (e: NumberFormatException) {
            return formatter.parse(value, Instant::from)
        }
    }
}
