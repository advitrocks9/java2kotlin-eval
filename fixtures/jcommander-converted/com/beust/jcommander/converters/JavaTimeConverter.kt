package com.beust.jcommander.converters

import com.beust.jcommander.ParameterException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Objects
 /**
 * Base class for all [java.time] converters.
 * 
 * @param <T> concrete type to parse into
</T> */
abstract class JavaTimeConverter<T : TemporalAccessor?> protected constructor(optionName: String?, toClass: Class<T?>?) : BaseConverter<T?>(optionName) {

    private val toClass: Class<T?>?
    
     /**
 * Inheritor constructors should have only 1 parameter - optionName.
 * 
 * @param optionName name of the option
 * @param toClass    type to parse into
 */
    init {
        /*~~hqztxi~~*/this.toClass = toClass
    }
    
    @Override fun convert(value: String?): T? {
        return supportedFormats().stream()
        .map({formatter -> tryConvert(value, formatter)})
        .filter(Objects::nonNull)
        .findFirst()
        .orElseThrow({ParameterException(errorMessage(value))})
    }
    
     /**
 * Supported formats for this type, e.g. `HH:mm:ss`
 * 
 * @return a set of supported formats
 */
    protected abstract fun supportedFormats(): Set<DateTimeFormatter?>?
    
     /**
 * Parse the value using the specified formatter.
 * 
 * @param value value to parse
 * @param formatter formatter specifying supported format
 * @return parsed value
 */
    protected abstract fun parse(value: String?, formatter: DateTimeFormatter?): T?
    
    private fun tryConvert(value: String?, formatter: DateTimeFormatter?): T? {
        try {
            return parse(value, formatter)
        }catch (exc: DateTimeParseException) {
            return null
        }catch (exc: Exception) {
            throw ParameterException(errorMessage(value), exc)
        }
    }
    
    private fun errorMessage(value: String?): String? {
        return getErrorString(value, "a " + toClass.getSimpleName())
    }
}
