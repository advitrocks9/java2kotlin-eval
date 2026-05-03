package com.beust.jcommander

import java.lang.annotation.ElementType.FIELD
import com.beust.jcommander.validators.NoValidator
import com.beust.jcommander.validators.NoValueValidator
import java.lang.annotation.Retention
import java.lang.annotation.Target
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target([FIELD]) 
annotation class DynamicParameter ( /**
 * An array of allowed command line parameters (e.g. "-D", "--define", etc...).
 */
val names: kotlin.Array<String> = [],  /**
 * Whether this option is required.
 */
val required: Boolean = false,  /**
 * A description of this option.
 */
val description: String = "",  /**
 * The key used to find the string in the message bundle.
 */
val descriptionKey: String = "",  /**
 * If true, this parameter won't appear in the usage().
 */
val hidden: Boolean = false,  /**
 * The validation classes to use.
 */
val validateWith: kotlin.Array<Class<out IParameterValidator?>> = [NoValidator::class],  /**
 * The character(s) used to assign the values.
 */
val assignment: String = "=", val validateValueWith: kotlin.Array<Class<out IValueValidator?>> = [NoValueValidator::class],  /**
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
val placeholder: String = "")
