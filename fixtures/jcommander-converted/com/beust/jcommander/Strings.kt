package com.beust.jcommander

import java.util.Arrays
import java.util.stream.Collectors
object Strings {
    fun isStringEmpty(s: String?): Boolean {
        return s == null || s.isEmpty()
    }
    
    fun startsWith(s: String, with: String, isCaseSensitive: Boolean): Boolean {
        return if (isCaseSensitive) s.startsWith(with) else s.toLowerCase().startsWith(with.toLowerCase())
    }
    
    fun join(delimiter: String?, args: kotlin.Array<Object?>?): String? {
        return Arrays.stream(args).map(String::valueOf).collect(Collectors.joining(delimiter))
    }
}
