package com.beust.jcommander

interface IValueValidator<T> {
     /**
 * Validate the parameter.
 * 
 * @param name The name of the parameter (e.g. "-host").
 * @param value The value of the parameter that we need to validate
 * 
 * @throws ParameterException Thrown if the value of the parameter is invalid.
 */
    @Throws(ParameterException::class) fun validate(name: String?, value: T?)
    
}
