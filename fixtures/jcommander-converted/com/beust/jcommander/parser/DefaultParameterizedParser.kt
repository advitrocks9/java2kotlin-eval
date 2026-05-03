package com.beust.jcommander.parser

import com.beust.jcommander.IParameterizedParser
import com.beust.jcommander.Parameterized
 /**
 * Pulled from the JCommander where is reflects the object to determine the Parameter annotations.
 * 
 * @author Tim Gallagher
 */
class DefaultParameterizedParser : IParameterizedParser {

     /**
 * Wraps the default parser.
 * 
 * @param annotatedObj an instance of the object with Parameter related annotations.
 * 
 * @author Tim Gallagher
 */
    @Override fun parseArg(annotatedObj: Object?): List<Parameterized?>? {
        return Parameterized.parseArg(annotatedObj)
    }
    
}
