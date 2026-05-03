 /**
 * Copyright (C) 2019 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.beust.jcommander.defaultprovider

import com.beust.jcommander.IDefaultProvider
import java.util.function.Function
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.Objects.requireNonNull
 /**
 * A default provider that reads its default values from an environment
 * variable.
 * 
 * A prefix pattern can be provided to indicate how options are identified.
 * The default pattern `-/` mandates that options MUST start with either a dash or a slash.
 * Options can have values separated by whitespace.
 * Values can contain whitespace as long as they are single-quoted or double-quoted.
 * Otherwhise whitespace identifies the end of a value.
 * 
 * @author Markus KARG (markus@headcrashing.eu)
 */
class EnvironmentVariableDefaultProvider internal constructor(environmentVariableName: String?, optionPrefixes: String?, resolver: Function<String?, String?>) : IDefaultProvider {

    private val environmentVariableValue: String?
    
    private val optionPrefixesPattern: String?
    
     /**
 * Creates a default provider reading the specified environment variable using the specified prefixes pattern.
 * 
 * @param environmentVariableName
 * The name of the environment variable to read (e. g. `"JCOMMANDER_OPTS"`). Must not be `null`.
 * @param optionPrefixes
 * A set of characters used to indicate the start of an option (e. g. `"-/"` if option names may start with either dash or slash). Must not be `null`.
 */
 /**
 * Creates a default provider reading the environment variable `JCOMMANDER_OPTS` using the prefixes pattern `-/`.
 */
    @kotlin.jvm.JvmOverloads 
    constructor(environmentVariableName: String? = com.beust.jcommander.defaultprovider.EnvironmentVariableDefaultProvider.Companion.DEFAULT_VARIABLE_NAME, optionPrefixes: String? = com.beust.jcommander.defaultprovider.EnvironmentVariableDefaultProvider.Companion.DEFAULT_PREFIXES_PATTERN) : this(requireNonNull(environmentVariableName), requireNonNull(optionPrefixes), System::getenv)
    
     /**
 * For Unit Tests Only: Allows to mock the resolver, as Java cannot set environment variables.
 * 
 * @param environmentVariableName
 * The name of the environment variable to read. May be `null` if the passed resolver doesn't use it (e. g. Unit Test).
 * @param optionPrefixes
 * A set of characters used to indicate the start of an option (e. g. `"-/"` if option names may start with either dash or slash). Must not be `null`.
 * @param resolver
 * Reads the value from the environment variable (e. g. `System::getenv`). Must not be `null`.
 */
    init {
        /*~~odrkad~~*/this.environmentVariableValue = resolver.apply(environmentVariableName)
        /*~~tweprp~~*/this.optionPrefixesPattern = requireNonNull(optionPrefixes)
    }
    
    @Override fun getDefaultValueFor(optionName: String?): String? {
        if (/*~~mxyrpk~~*/this.environmentVariableValue == null) return null
        val matcher: Matcher? = Pattern
        .compile("(?:(?:.*\\s+)|(?:^))(" + Pattern.quote(optionName) + ")\\s*((?:'[^']*(?='))|(?:\"[^\"]*(?=\"))|(?:[^" + /*~~prihtv~~*/this.optionPrefixesPattern + "\\s]+))?.*")
        .matcher(/*~~zobzqz~~*/this.environmentVariableValue)
        if (!matcher.matches()) return null
        var value: String? = matcher.group(2)
        if (value == null) return "true"
        val firstCharacter: Char = value.charAt(0)
        if (firstCharacter == '\'' || firstCharacter == '"') value = value.substring(1)
        return value
    }
    
    companion object {
        private val DEFAULT_VARIABLE_NAME: String = "JCOMMANDER_OPTS"
        
        private val DEFAULT_PREFIXES_PATTERN: String = "-/"
        
    }}
