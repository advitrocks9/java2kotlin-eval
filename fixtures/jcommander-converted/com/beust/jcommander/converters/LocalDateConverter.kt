package com.beust.jcommander.converters

import java.time.LocalDate
import java.time.format.DateTimeFormatter
 /**
 * Converter to [LocalDate].
 */
class LocalDateConverter (optionName: String?) : JavaTimeConverter<LocalDate?>(optionName, LocalDate::class.java) {

    @Override protected fun supportedFormats(): Set<DateTimeFormatter?>? {
        return Set.of(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    }
    
    @Override protected fun parse(value: String?, formatter: DateTimeFormatter?): LocalDate? {
        return LocalDate.parse(value, formatter)
    }
}
