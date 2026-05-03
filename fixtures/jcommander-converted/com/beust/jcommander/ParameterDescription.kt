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

import com.beust.jcommander.validators.NoValidator
import com.beust.jcommander.validators.NoValueValidator
import com.beust.jcommander.Strings.isStringEmpty
import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.util.ResourceBundle
class ParameterDescription {
    private var `object`: Object? = null
    
    private val wrappedParameter: WrappedParameter?
    private var parameterAnnotation: Parameter? = null
    private var dynamicParameterAnnotation: DynamicParameter? = null
    
     /** The field/method  */
    private var parameterized: Parameterized? = null
     /** Keep track of whether a value was added to flag an error  */
    private var assigned: Boolean = false
    private var bundle: ResourceBundle? = null
    private var description: String? = null
    private var jCommander: JCommander? = null
    private var defaultObject: Object? = null
     /** Longest of the names(), used to present usage() alphabetically  */
    private var longestName: String = ""
    
    constructor(`object`: Object, annotation: DynamicParameter?, 
    parameterized: Parameterized, 
    bundle: ResourceBundle?, jc: JCommander?) {
        if (!Map::class.java.isAssignableFrom(parameterized.getType())) {
            throw ParameterException(("@DynamicParameter " + parameterized.getName()
            + " should be of type "
            + "Map but is " + parameterized.getType().getName()))
        }
        
        dynamicParameterAnnotation = annotation
        wrappedParameter = WrappedParameter(dynamicParameterAnnotation)
        init(`object`, parameterized, bundle, jc)
    }
    
    constructor(`object`: Object, annotation: Parameter?, parameterized: Parameterized, 
    bundle: ResourceBundle?, jc: JCommander?) {
        parameterAnnotation = annotation
        wrappedParameter = WrappedParameter(parameterAnnotation)
        init(`object`, parameterized, bundle, jc)
    }
    
     /**
 * Find the resource bundle in the annotations.
 * @return
 */
    @SuppressWarnings("deprecation") private fun findResourceBundle(o: Object): ResourceBundle? {
        var result: ResourceBundle? = null
        
        val p: Parameters? = o.getClass().getAnnotation(Parameters::class.java)
        if (p != null && !isStringEmpty(p.resourceBundle())) {
            result = ResourceBundle.getBundle(p.resourceBundle(), Locale.getDefault(), o.getClass().getClassLoader())
        } else {
            val a: com.beust.jcommander.ResourceBundle? = o.getClass().getAnnotation(
            com.beust.jcommander.ResourceBundle::class.java)
            if (a != null && !isStringEmpty(a.value())) {
                result = ResourceBundle.getBundle(a.value(), Locale.getDefault(), o.getClass().getClassLoader())
            }
        }
        
        return result
    }
    
    private fun initDescription(description: String?, descriptionKey: String?, names: kotlin.Array<String?>) {
        /*~~wycnoi~~*/this.description = description
        if (!isStringEmpty(descriptionKey)) {
            if (bundle != null) {
                /*~~psvdcx~~*/this.description = bundle.getString(descriptionKey)
            }
        }
        
        for (name: String in names) {
            if (name.length() > longestName.length()) longestName = name
        }
    }
    
     /**
 * Initializes the state of this parameter description. This will set an appropriate bundle if it is null<.
 * If its the description in is empty and it refers to an enum type, then the description will be set to its possible
 * values. It will also attempt to validate the default value of the parameter.
 * 
 * @param object the object defining the command-line arguments
 * @param parameterized the wrapper for the field or method annotated with \@Parameter this represents
 * @param bundle the locale
 * @param jCommander the parent JCommander instance
 * @see .initDescription
 */
    @SuppressWarnings("unchecked") private fun init(`object`: Object, parameterized: Parameterized, bundle: ResourceBundle?, 
    jCommander: JCommander?) {
        /*~~embysy~~*/this.`object` = `object`
        /*~~hpklfj~~*/this.parameterized = parameterized
        /*~~qqaurq~~*/this.bundle = bundle
        if (/*~~ubxruv~~*/this.bundle == null) {
            /*~~xnqmka~~*/this.bundle = findResourceBundle(`object`)
        }
        /*~~mmpajq~~*/this.jCommander = jCommander
        
        if (parameterAnnotation != null) {
            val description: String?
            if (Enum::class.java.isAssignableFrom(parameterized.getType())
            && parameterAnnotation.description().isEmpty()) {
                description = "Options: " + EnumSet.allOf(parameterized.getType() as Class<out Enum?>?)
            } else {
                description = parameterAnnotation.description()
            }
            initDescription(description, parameterAnnotation.descriptionKey(), 
            parameterAnnotation.names())
        } else if (dynamicParameterAnnotation != null) {
            initDescription(dynamicParameterAnnotation.description(), 
            dynamicParameterAnnotation.descriptionKey(), 
            dynamicParameterAnnotation.names())
        } else {
            throw AssertionError("Shound never happen")
        }
        
        try {
            defaultObject = parameterized.get(`object`)
        }catch (e: Exception) {
        }
        
            //
    // Validate default values, if any and if applicable
    //
        if (defaultObject != null) {
            if (parameterAnnotation != null && !parameterAnnotation.required()) {
                validateDefaultValues(parameterAnnotation.names())
            }
        }
    }
    
    private fun validateDefaultValues(names: kotlin.Array<String?>) {
        val name: String? = if (names.size > 0) names.get(0) else ""
        validateValueParameter(name, defaultObject)
    }
    
    fun getLongestName(): String? {
        return longestName
    }
    
    fun getDefault(): Object? {
        return defaultObject
    }
    
     /**
 * @return defaultValueDescription, if description is empty string, return default Object.
 */
    fun getDefaultValueDescription(): Object? {
        return if (parameterAnnotation == null) defaultObject else if (parameterAnnotation.defaultValueDescription().isEmpty()) defaultObject else parameterAnnotation.defaultValueDescription()
    }
    
    fun getDescription(): String? {
        return description
    }
    
    fun getObject(): Object? {
        return `object`
    }
    
    fun getNames(): String? {
        return String.join(", ", wrappedParameter.names())
    }
    
    fun getCategory(): String? {
        return wrappedParameter.category()
    }
    
    fun getParameter(): WrappedParameter? {
        return wrappedParameter
    }
    
    fun getParameterized(): Parameterized? {
        return parameterized
    }
    
    private fun isMultiOption(): Boolean {
        val fieldType: Class<*>? = parameterized.getType()
        return fieldType.equals(List::class.java) || fieldType.equals(Set::class.java)
        || parameterized.isDynamicParameter()
    }
    
    fun addValue(value: String?) {
        addValue(value, false /* not default */)
    }
    
     /**
 * @return true if this parameter received a value during the parsing phase.
 */
    fun isAssigned(): Boolean {
        return assigned
    }
    
    
    fun setAssigned(b: Boolean) {
        assigned = b
    }
    
     /**
 * Add the specified value to the field. First, validate the value if a
 * validator was specified. Then look up any field converter, then any type
 * converter, and if we can't find any, throw an exception.
 */
    fun addValue(value: String?, isDefault: Boolean) {
        addValue(null, value, isDefault, true, -1)
    }
    
    fun addValue(name: String?, value: String?, isDefault: Boolean, validate: Boolean, currentIndex: Int): Object? {
        var name: String? = name
        p(("Adding " + (if (isDefault) "default " else "") + "value:" + value
        + " to parameter:" + parameterized.getName()))
        if (name == null) {
            name = wrappedParameter.names().get(0)
        }
        if (currentIndex == 0 && assigned && !isMultiOption() && !jCommander.isParameterOverwritingAllowed()
        || isNonOverwritableForced()) {
            throw ParameterException("Can only specify option " + name + " once.")
        }
        
        if (validate) {
            validateParameter(name, value)
        }
        
        val type: Class<*>? = parameterized.getType()
        
        val convertedValue: Object? = jCommander.convertValue(getParameterized(), getParameterized().getType(), name, value)
        if (validate) {
            validateValueParameter(name, convertedValue)
        }
        val isCollection: Boolean = Collection::class.java.isAssignableFrom(type)
        
        val finalValue: Object?
        if (isCollection) {
            @SuppressWarnings("unchecked") var l: Collection<Object?>? = parameterized.get(`object`) as Collection<Object?>?
            if (l == null || fieldIsSetForTheFirstTime(isDefault)) {
                l = newCollection(type)
                parameterized.set(`object`, l)
            }
            if (convertedValue is Collection) {
                l.addAll(convertedValue)
            } else {
                l.add(convertedValue)
            }
            finalValue = l
        } else {
      // If the field type is not a collection, see if it's a type that contains @SubParameters annotations
            val subParameters: List<com.beust.jcommander.ParameterDescription.SubParameterIndex?> = findSubParameters(type)
            if (!subParameters.isEmpty()) {
        // @SubParameters found
                finalValue = handleSubParameters(value, currentIndex, type, subParameters)
            } else {
        // No, regular parameter
                wrappedParameter.addValue(parameterized, `object`, convertedValue)
                finalValue = convertedValue
            }
        }
        if (!isDefault) assigned = true
        
        /*~~vmyhem~~*/this.value = finalValue
        
        return finalValue
    }
    
    private var value: Object? = null
    
    fun getValue(): Object? {
        return value
    }
    
    private fun handleSubParameters(value: String?, currentIndex: Int, type: Class<*>, 
    subParameters: List<com.beust.jcommander.ParameterDescription.SubParameterIndex?>): Object? {
        val finalValue: Object? // Yes, assign each following argument to the corresponding field of that object
        var sai: com.beust.jcommander.ParameterDescription.SubParameterIndex? = null
        for (si: com.beust.jcommander.ParameterDescription.SubParameterIndex in subParameters) {
            if (si.order == currentIndex) {
                sai = si
                break
            }
        }
        if (sai != null) {
            var objectValue: Object? = parameterized.get(`object`)
            try {
                if (objectValue == null) {
                    objectValue = type.newInstance()
                    parameterized.set(`object`, objectValue)
                }
                wrappedParameter.addValue(parameterized, objectValue, value, sai.field)
                finalValue = objectValue
            }catch (e: InstantiationException) {
                throw ParameterException("Couldn't instantiate " + type, e)
            }catch (e: IllegalAccessException) {
                throw ParameterException("Couldn't instantiate " + type, e)
            }
        } else {
            throw ParameterException("Couldn't find where to assign parameter " + value + " in " + type)
        }
        return finalValue
    }
    
    fun getParameterAnnotation(): Parameter? {
        return parameterAnnotation
    }
    
    internal inner class SubParameterIndex (order: Int, field: Field?) {
        var order: Int = -1
        var field: Field?
        
        init {
            /*~~jjqotc~~*/this.order = order
            /*~~tzgdlu~~*/this.field = field
        }
    }
    
    private fun findSubParameters(type: Class<*>): List<com.beust.jcommander.ParameterDescription.SubParameterIndex?>? {
        val result: List<com.beust.jcommander.ParameterDescription.SubParameterIndex?> = ArrayList()
        for (field: Field in type.getDeclaredFields()) {
            val subParameter: Annotation? = field.getAnnotation(SubParameter::class.java)
            if (subParameter != null) {
                val sa: SubParameter = subParameter as SubParameter
                result.add(com.beust.jcommander.ParameterDescription.SubParameterIndex(sa.order(), field))
            }
        }
        return result
    }
    
    private fun validateParameter(name: String?, value: String?) {
        val validators: kotlin.Array<Class<out IParameterValidator?>?>? = wrappedParameter.validateWith()
        if (validators != null && validators.size > 0) {
            for (validator: Class<out IParameterValidator?> in validators) {
                validateParameter(validator, name, value)
            }
        }
    }
    
    fun validateValueParameter(name: String?, value: Object?) {
        val validators: kotlin.Array<Class<out IValueValidator?>?>? = wrappedParameter.validateValueWith()
        if (validators != null && validators.size > 0) {
            for (validator: Class<out IValueValidator?> in validators) {
                validateValueParameter(validator, name, value)
            }
        }
    }
    
    fun validateValueParameter(validator: Class<out IValueValidator?>, 
    name: String?, value: Object?) {
        try {
            if (validator !== NoValueValidator::class.java) {
                p("Validating value parameter:" + name + " value:" + value + " validator:" + validator)
            }
            validator.newInstance().validate(name, value)
        }catch (e: InstantiationException) {
            throw ParameterException("Can't instantiate validator:" + e)
        }catch (e: IllegalAccessException) {
            throw ParameterException("Can't instantiate validator:" + e)
        }
    }
    
    fun validateParameter(validator: Class<out IParameterValidator?>, 
    name: String?, value: String?) {
        try {
        
            if (validator !== NoValidator::class.java) {
                p("Validating parameter:" + name + " value:" + value + " validator:" + validator)
            }
            validator.getDeclaredConstructor().newInstance().validate(name, value)
            if (IParameterValidator2::class.java.isAssignableFrom(validator)) {
                val instance: IParameterValidator2? = validator.getDeclaredConstructor().newInstance() as IParameterValidator2?
                instance.validate(name, value, /*~~ombugn~~*/this)
            }
        }catch (e: InstantiationException) {
            throw ParameterException("Can't instantiate validator:" + e)
        }catch (e: IllegalAccessException) {
            throw ParameterException("Can't instantiate validator:" + e)
        }catch (ex: ParameterException) {
            throw ex
        }catch (ex: Exception) {
            throw ParameterException(ex)
        }
    }
    
     /*
   * Creates a new collection for the field's type.
   *
   * Currently only List and Set are supported. Support for
   * Queues and Stacks could be useful.
   */
    @SuppressWarnings("unchecked") private fun newCollection(type: Class<*>): Collection<Object?>? {
        if (SortedSet::class.java.isAssignableFrom(type)) return TreeSet()
        else if (LinkedHashSet::class.java.isAssignableFrom(type)) return LinkedHashSet()
        else if (Set::class.java.isAssignableFrom(type)) return HashSet()
        else if (List::class.java.isAssignableFrom(type)) return ArrayList()
        else {
            throw ParameterException(("Parameters of Collection type '" + type.getSimpleName()
            + "' are not supported. Please use List or Set instead."))
        }
    }
    
     /*
   * Tests if its the first time a non-default value is
   * being added to the field.
   */
    private fun fieldIsSetForTheFirstTime(isDefault: Boolean): Boolean {
        return (!isDefault && !assigned)
    }
    
    private fun p(string: String?) {
        if (System.getProperty(JCommander.DEBUG_PROPERTY) != null) {
            jCommander.getConsole().println("[ParameterDescription] " + string)
        }
    }
    
    @Override fun toString(): String? {
        return "[ParameterDescription " + parameterized.getName() + "]"
    }
    
    fun isDynamicParameter(): Boolean {
        return dynamicParameterAnnotation != null
    }
    
    fun isHelp(): Boolean {
        return wrappedParameter.isHelp()
    }
    
    fun isNonOverwritableForced(): Boolean {
        return wrappedParameter.isNonOverwritableForced()
    }
}
