package com.beust.jcommander.converters

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.ParameterException
import java.util.EnumSet
 /**
 * A converter to parse enums
 * @param <T> the enum type
 * @author simon04
</T> */
class EnumConverter<T : Enum<T?>?> (optionName: String?, clazz: Class<T?>?) : IStringConverter<T?> {

    private val optionName: String?
    private val clazz: Class<T?>?
    
     /**
 * Constructs a new converter.
 * @param optionName the option name for error reporting
 * @param clazz the enum class
 */
    init {
        /*~~zpites~~*/this.optionName = optionName
        /*~~xhtxyf~~*/this.clazz = clazz
    }
    
    @Override fun convert(value: String): T? {
        for (constant: T? in EnumSet.allOf(clazz)) {
      // the toString method may be overridden, causing what is printed (or what user types) is different from it's declared name
            if (constant.name().equals(value) || constant.name().equals(value.toUpperCase())
            || constant.toString().equals(value) || constant.toString().equals(value.toUpperCase())) {
                return constant
            }
        }
        throw ParameterException("Invalid value for " + optionName + " parameter. Allowed values:" + 
        EnumSet.allOf(clazz))
    }
}
