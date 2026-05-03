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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
 /**
 * Converts a String to a Date.
 * TODO Modify to work with all valid ISO 8601 date formats (currently only works with yyyy-MM-dd).
 * 
 * @author Angus Smithson
 */
class ISO8601DateConverter (optionName: String?) : BaseConverter<Date?>(optionName) {

    fun convert(value: String?): Date? {
        try {
            return com.beust.jcommander.converters.ISO8601DateConverter.Companion.DATE_FORMAT.parse(value)
        }catch (pe: ParseException) {
            throw ParameterException(getErrorString(value, "an ISO-8601 formatted date (%s)".formatted(com.beust.jcommander.converters.ISO8601DateConverter.Companion.DATE_FORMAT.toPattern())))
        }
    }
    companion object {
        private val DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")
        
    }}
