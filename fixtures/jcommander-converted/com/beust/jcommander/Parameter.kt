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
package com.beust.jcommander

import java.lang.annotation.ElementType.FIELD
import java.lang.annotation.ElementType.METHOD
import com.beust.jcommander.converters.CommaParameterSplitter
import com.beust.jcommander.converters.IParameterSplitter
import com.beust.jcommander.converters.NoConverter
import com.beust.jcommander.validators.NoValidator
import com.beust.jcommander.validators.NoValueValidator
import java.lang.annotation.Retention
import java.lang.annotation.Target
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target([FIELD, METHOD]) 
annotation class Parameter ( /**
 * An array of allowed command line parameters (e.g. "-d", "--outputdir", etc...).
 * If this attribute is omitted, the field it's annotating will receive all the
 * unparsed options. There can only be at most one such annotation.
 */
val names: kotlin.Array<String> = [],  /**
 * A description of this option.
 */
val description: String = "",  /**
 * Description of default value.
 */
val defaultValueDescription: String = "",  /**
 * Whether this option is required.
 */
val required: Boolean = false,  /**
 * The key used to find the string in the message bundle.
 */
val descriptionKey: String = "", val arity: Int = com.beust.jcommander.Parameter.Companion.DEFAULT_ARITY,  /**
 * If true, this parameter is a password and it will be prompted on the console
 * (if available).
 */
val password: Boolean = false,  /**
 * The string converter to use for this field. If the field is of type List
 * and not listConverter attribute was specified, JCommander will split
 * the input in individual values and convert each of them separately.
 */
val converter: Class<out IStringConverter<*>?> = NoConverter::class,  /**
 * The list string converter to use for this field. If it's specified, the
 * field has to be of type List and the converter needs to return
 * a List that's compatible with that type.
 */
val listConverter: Class<out IStringConverter<*>?> = NoConverter::class,  /**
 * If true, this parameter won't appear in the usage().
 */
val hidden: Boolean = false,  /**
 * Validate the parameter found on the command line.
 */
val validateWith: kotlin.Array<Class<out IParameterValidator?>> = [NoValidator::class],  /**
 * Validate the value for this parameter.
 */
val validateValueWith: kotlin.Array<Class<out IValueValidator?>> = [NoValueValidator::class],  /**
 * @return true if this parameter has a variable arity. See @{IVariableArity}
 */
val variableArity: Boolean = false,  /**
 * What splitter to use (applicable only on fields of type List). By default,
 * a comma separated splitter will be used.
 */
val splitter: Class<out IParameterSplitter?> = CommaParameterSplitter::class,  /**
 * If true, console will not echo typed input
 * Used in conjunction with password = true
 */
val echoInput: Boolean = false,  /**
 * If true, this parameter is for help. If such a parameter is specified,
 * required parameters are no longer checked for their presence.
 */
val help: Boolean = false,  /**
 * If true, this parameter can not be overwritten through a file or another appearance of the parameter
 * @return nc
 */
val forceNonOverwritable: Boolean = false,  /**
 * If specified, this number will be used to order the description of this parameter when usage() is invoked.
 * @return
 */
val order: Int = -1,  /**
 * If specified, the category name will be used to order the description of this parameter when usage() is invoked before the number order() is used.
 * @return (default or specified) category name
 */
val category: String = "",  /**
 * If specified, the placeholder (e.g. `"<filename>"`) will be shown in the usage() output as a required parameter after the switch (e.g. `-i <filename>`).
 * @return the placeholder
 */
val placeholder: String = "") {

    companion object {
         /**
 * How many parameter values this parameter will consume. For example,
 * an arity of 2 will allow "-pair value1 value2".
 */
        val DEFAULT_ARITY: Int = -1
    }}
