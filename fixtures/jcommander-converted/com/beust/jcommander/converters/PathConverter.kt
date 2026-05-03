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
package com.beust.jcommander.converters

import com.beust.jcommander.ParameterException
import java.nio.file.InvalidPathException
import java.nio.file.Path
 /**
 * Convert a string into a path.
 * 
 * @author samvv
 */
class PathConverter (optionName: String?) : BaseConverter<Path?>(optionName) {

    fun convert(value: String): Path? {
        try {
            return Path.of(value)
        }catch (e: InvalidPathException) {
            val encoded: String? = com.beust.jcommander.converters.PathConverter.Companion.escapeUnprintable(value).toString()
            throw ParameterException(getErrorString(encoded, "a path"))
        }
    }
    
    companion object {
        private fun escapeUnprintable(value: String): CharSequence? {
            val bldr: StringBuilder = StringBuilder()
            for (c: Char in value.toCharArray()) {
                if (c < ' ') {
                    bldr.append("\\u").append("%04X".formatted(c.code))
                } else {
                    bldr.append(c)
                }
            }
            return bldr
        }
    }}
