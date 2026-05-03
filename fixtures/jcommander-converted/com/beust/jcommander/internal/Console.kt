package com.beust.jcommander.internal

interface Console {

    fun print(msg: CharSequence?)
    
    fun println(msg: CharSequence?)
    
    fun readPassword(echoInput: Boolean): CharArray?
}
