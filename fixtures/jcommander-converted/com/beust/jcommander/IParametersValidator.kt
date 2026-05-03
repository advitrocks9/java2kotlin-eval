package com.beust.jcommander

interface IParametersValidator {

     /**
 * Validate all parameters.
 * 
 * @param parameters
 * Name-value-pairs of all parameters (e.g. "-host":"localhost").
 * 
 * @throws ParameterException
 * Thrown if validation of the parameters fails.
 */
    @Throws(ParameterException::class) fun validate(parameters: Map<String?, Object?>?)
    
}
