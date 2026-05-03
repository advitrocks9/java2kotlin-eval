 /**
 * Copyright (C) 2010 the original author or authors.
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
import com.beust.jcommander.ParameterException
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function
import java.util.Properties
 /**
 * A default provider that reads its default values from a property file.
 * 
 * @author cbeust
 */
class PropertyFileDefaultProvider : IDefaultProvider {
    private val properties: Properties = Properties()
    private val optionNameTransformer: Function<String?, String?>?
    
    constructor() {
        init(com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider.Companion.DEFAULT_FILE_NAME)
        optionNameTransformer = com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider.Companion.DEFAULT_OPTION_NAME_TRANSFORMER
    }
    
    constructor(fileName: String?) {
        init(fileName)
        optionNameTransformer = com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider.Companion.DEFAULT_OPTION_NAME_TRANSFORMER
    }
    
    constructor(fileName: String?, optionNameTransformer: Function<String?, String?>?) {
        init(fileName)
        /*~~ympwpb~~*/this.optionNameTransformer = optionNameTransformer
    }
    
    constructor(path: Path?) : this(path, com.beust.jcommander.defaultprovider.PropertyFileDefaultProvider.Companion.DEFAULT_OPTION_NAME_TRANSFORMER)
    
    constructor(path: Path?, optionNameTransformer: Function<String?, String?>?) {
        try {
            Files.newInputStream(path).use {inputStream -> properties.load(inputStream)
            } }catch (e: IOException) {
            throw ParameterException("Could not load properties from path: " + path)
        }
        /*~~gwmkuc~~*/this.optionNameTransformer = optionNameTransformer
    }
    
    private fun init(fileName: String?) {
        try {
            val url: URL? = ClassLoader.getSystemResource(fileName)
            if (url != null) {
                properties.load(url.openStream())
            } else {
                throw ParameterException(("Could not find property file: " + fileName
                + " on the class path"))
            }
        }
        catch (e: IOException) {
            throw ParameterException("Could not open property file: " + fileName)
        }
    }
    
    fun getDefaultValueFor(optionName: String): String? {
        return properties.getProperty(optionName.transform(optionNameTransformer))
    }
    
    companion object {
        val DEFAULT_FILE_NAME: String = "jcommander.properties"
        private val DEFAULT_OPTION_NAME_TRANSFORMER: Function<String?, String?> = Function<String?, String?> {
        optionName -> var index: Int = 0
        while (index < optionName.length() && !Character.isLetterOrDigit(optionName.charAt(index))) index++
        optionName.substring(index)
        }
    }}
