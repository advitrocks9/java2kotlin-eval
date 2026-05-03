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
package com.beust.jcommander.internal

import com.beust.jcommander.IStringConverter
import com.beust.jcommander.IStringConverterFactory
import com.beust.jcommander.converters.BigDecimalConverter
import com.beust.jcommander.converters.BooleanConverter
import com.beust.jcommander.converters.DoubleConverter
import com.beust.jcommander.converters.FileConverter
import com.beust.jcommander.converters.FloatConverter
import com.beust.jcommander.converters.ISO8601DateConverter
import com.beust.jcommander.converters.InstantConverter
import com.beust.jcommander.converters.IntegerConverter
import com.beust.jcommander.converters.LocalDateConverter
import com.beust.jcommander.converters.LocalDateTimeConverter
import com.beust.jcommander.converters.LocalTimeConverter
import com.beust.jcommander.converters.LongConverter
import com.beust.jcommander.converters.OffsetDateTimeConverter
import com.beust.jcommander.converters.OffsetTimeConverter
import com.beust.jcommander.converters.StringConverter
import com.beust.jcommander.converters.PathConverter
import com.beust.jcommander.converters.URIConverter
import com.beust.jcommander.converters.URLConverter
import com.beust.jcommander.converters.ZonedDateTimeConverter
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.util.Date
import java.net.URI
import java.net.URL
import java.nio.file.Path
class DefaultConverterFactory : IStringConverterFactory {
    fun getConverter(forType: Class?): Class<out IStringConverter<*>?>? {
        return com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.get(forType)
    }
    
    companion object {
         /**
 * A map of converters per class.
 */
        private val classConverters: Map<Class?, Class<out IStringConverter<*>?>?>?
        
        init {
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters = Maps.newHashMap()
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(String::class.java, StringConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Integer::class.java, IntegerConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Int::class.javaPrimitiveType, IntegerConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Long::class.java, LongConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Long::class.javaPrimitiveType, LongConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Float::class.java, FloatConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Float::class.javaPrimitiveType, FloatConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Double::class.java, DoubleConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Double::class.javaPrimitiveType, DoubleConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Boolean::class.java, BooleanConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Boolean::class.javaPrimitiveType, BooleanConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(File::class.java, FileConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(BigDecimal::class.java, BigDecimalConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Date::class.java, ISO8601DateConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(URI::class.java, URIConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(URL::class.java, URLConverter::class.java)
            
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Instant::class.java, InstantConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(LocalDate::class.java, LocalDateConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(LocalDateTime::class.java, LocalDateTimeConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(LocalTime::class.java, LocalTimeConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(OffsetDateTime::class.java, OffsetDateTimeConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(OffsetTime::class.java, OffsetTimeConverter::class.java)
            com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(ZonedDateTime::class.java, ZonedDateTimeConverter::class.java)
            
            try {
                com.beust.jcommander.internal.DefaultConverterFactory.Companion.classConverters.put(Path::class.java, PathConverter::class.java)
            }catch (ex: NoClassDefFoundError) {
      // skip if class is not present (e.g. on Android)
            }
        }
        
    }}
