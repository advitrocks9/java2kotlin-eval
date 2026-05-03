package com.beust.jcommander.converters

 /**
 * Convert a string representing several parameters (e.g. "a,b,c" or "d/e/f") into a
 * list of arguments ([a,b,c] and [d,e,f]).
 */
interface IParameterSplitter {
    fun split(value: String?): List<String?>?
}
