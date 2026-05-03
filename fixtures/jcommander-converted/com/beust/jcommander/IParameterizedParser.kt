package com.beust.jcommander

 /**
 * Thin interface allows the Parameterized parsing mechanism, which reflects an object to find the
 * JCommander annotations, to be replaced at runtime for cases where the source code cannot
 * be directly annotated with JCommander annotations, but may have other annotations such as
 * JSON annotations that can be used to reflect as JCommander parameters.
 * 
 * @author Tim Gallagher
 */
interface IParameterizedParser {

     /**
 * Parses the given object for any command line related annotations and returns the list of
 * JCommander Parameterized definitions.
 * 
 * @param annotatedObj the object that contains the annotations.
 * @return non-null List but may be empty
 */
    fun parseArg(annotatedObj: Object?): List<Parameterized?>?
    
}
