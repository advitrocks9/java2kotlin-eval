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
import java.math.BigDecimal
 /**
 * Converts a String to a BigDecimal.
 * 
 * @author Angus Smithson
 */
class BigDecimalConverter (optionName: String?) : BaseConverter<BigDecimal?>(optionName) {

    fun convert(value: String?): BigDecimal? {
        try {
            return BigDecimal(value)
        }catch (nfe: NumberFormatException) {
            throw ParameterException(getErrorString(value, "a BigDecimal"))
        }
    }
}
