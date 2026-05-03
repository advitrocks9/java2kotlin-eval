package com.beust.jcommander.converters

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.internal.Lists
 /**
 * A converter to obtain a list of elements.
 * @param <T> the element type
 * @author simon04
</T> */
class DefaultListConverter<T> (splitter: IParameterSplitter?, converter: IStringConverter<T?>?) : IStringConverter<List<T?>?> {

    private val splitter: IParameterSplitter?
    private val converter: IStringConverter<T?>?
    
     /**
 * Constructs a new converter.
 * @param splitter to split value into list of arguments
 * @param converter to convert list of arguments to target element type
 */
    init {
        /*~~wqwvjb~~*/this.splitter = splitter
        /*~~kupmup~~*/this.converter = converter
    }
    
    @Override fun convert(value: String?): List<T?>? {
        val result: List<T?>? = Lists.newArrayList()
        for (param: String? in splitter.split(value)) {
            result.add(converter.convert(param))
        }
        return result
    }
}
