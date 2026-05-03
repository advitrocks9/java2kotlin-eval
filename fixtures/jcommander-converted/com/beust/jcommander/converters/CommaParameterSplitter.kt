package com.beust.jcommander.converters

class CommaParameterSplitter : IParameterSplitter {

    fun split(value: String): List<String?>? {
        return if (value.isEmpty()) List.of() else List.of(value.split(","))
    }
}
