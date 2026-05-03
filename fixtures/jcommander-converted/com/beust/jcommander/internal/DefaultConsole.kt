package com.beust.jcommander.internal

import com.beust.jcommander.ParameterException
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintStream
class DefaultConsole : Console {
    private val target: PrintStream?
    
    constructor(target: PrintStream?) {
        /*~~aybgey~~*/this.target = target
    }
    
    constructor() {
        /*~~emwzpc~~*/this.target = System.out
    }
    
    fun print(msg: CharSequence?) {
        target.print(msg)
    }
    
    fun println(msg: CharSequence?) {
        target.println(msg)
    }
    
    fun readPassword(echoInput: Boolean): CharArray? {
        try {
      // Do not close the readers since System.in should not be closed
            val isr: InputStreamReader = InputStreamReader(System.`in`)
            val `in`: BufferedReader = BufferedReader(isr)
            val result: String? = `in`.readLine()
            return result.toCharArray()
        }
        catch (e: IOException) {
            throw ParameterException(e)
        }
    }
    
}
