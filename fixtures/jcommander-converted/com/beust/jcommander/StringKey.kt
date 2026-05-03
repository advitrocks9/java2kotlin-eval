package com.beust.jcommander

import com.beust.jcommander.FuzzyMap.IKey
@kotlin.jvm.JvmRecord 
data class StringKey (name: String?) : IKey {

    @Override fun getName(): String? {
        return name
    }
    
    @Override fun toString(): String? {
        return name
    }
    
    val name: String?
    init {
        this.name = name
    }
}
