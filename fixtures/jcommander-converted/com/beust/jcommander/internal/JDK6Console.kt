package com.beust.jcommander.internal

import com.beust.jcommander.ParameterException
import java.io.PrintWriter
import java.lang.reflect.Method
class JDK6Console (console: Object) : Console {

    private val console: Object?
    
    private val writer: PrintWriter?
    
    init {
        /*~~ljckic~~*/this.console = console
        val writerMethod: Method? = console.getClass().getDeclaredMethod("writer")
        writer = writerMethod.invoke(console) as PrintWriter?
    }
    
    fun print(msg: CharSequence?) {
        writer.print(msg)
    }
    
    fun println(msg: CharSequence?) {
        writer.println(msg)
    }
    
    fun readPassword(echoInput: Boolean): CharArray? {
        try {
            writer.flush()
            val method: Method?
            if (echoInput) {
                method = console.getClass().getDeclaredMethod("readLine")
                return (method.invoke(console) as String).toCharArray()
            } else {
                method = console.getClass().getDeclaredMethod("readPassword")
                return method.invoke(console) as CharArray?
            }
        }
        catch (e: Exception) {
            throw ParameterException(e)
        }
    }
    
}