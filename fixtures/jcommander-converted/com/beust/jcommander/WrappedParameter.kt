package com.beust.jcommander

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
 /**
 * Encapsulates the operations common to @Parameter and @DynamicParameter
 */
class WrappedParameter {
    private var parameter: Parameter? = null
    private var dynamicParameter: DynamicParameter? = null
    
    constructor(parameter: Parameter?) {
        /*~~ttvmwb~~*/this.parameter = parameter
    }
    
    constructor(p: DynamicParameter?) {
        dynamicParameter = p
    }
    
    fun getParameter(): Parameter? {
        return parameter
    }
    
    fun getDynamicParameter(): DynamicParameter? {
        return dynamicParameter
    }
    
    fun arity(): Int {
        return if (parameter != null) parameter.arity() else 1
    }
    
    fun hidden(): Boolean {
        return if (parameter != null) parameter.hidden() else dynamicParameter.hidden()
    }
    
    fun required(): Boolean {
        return if (parameter != null) parameter.required() else dynamicParameter.required()
    }
    
    fun password(): Boolean {
        return if (parameter != null) parameter.password() else false
    }
    
    fun names(): kotlin.Array<String?>? {
        return if (parameter != null) parameter.names() else dynamicParameter.names()
    }
    
    fun variableArity(): Boolean {
        return if (parameter != null) parameter.variableArity() else false
    }
    
    fun order(): Int {
        return if (parameter != null) parameter.order() else dynamicParameter.order()
    }
    
    fun category(): String? {
        return if (parameter != null) parameter.category() else dynamicParameter.category()
    }
    
    fun placeholder(): String? {
        return if (parameter != null) parameter.placeholder() else dynamicParameter.placeholder()
    }
    
    fun validateWith(): kotlin.Array<Class<out IParameterValidator?>?>? {
        return if (parameter != null) parameter.validateWith() else dynamicParameter.validateWith()
    }
    
    fun validateValueWith(): kotlin.Array<Class<out IValueValidator?>?>? {
        return if (parameter != null) 
        parameter.validateValueWith()
        else 
        dynamicParameter.validateValueWith()
    }
    
    fun echoInput(): Boolean {
        return if (parameter != null) parameter.echoInput() else false
    }
    
    fun addValue(parameterized: Parameterized, `object`: Object?, value: Object) {
        try {
            addValue(parameterized, `object`, value, null)
        }catch (e: IllegalAccessException) {
            throw ParameterException("Couldn't set " + `object` + " to " + value, e)
        }
    }
    
    @Throws(IllegalAccessException::class) fun addValue(parameterized: Parameterized, `object`: Object?, value: Object, field: Field?) {
        if (parameter != null) {
            if (field != null) {
                field.set(`object`, value)
            } else {
                parameterized.set(`object`, value)
            }
        } else {
            val a: String? = dynamicParameter.assignment()
            val sv: String? = value.toString()
            
            val aInd: Int = sv.indexOf(a)
            if (aInd == -1) {
                throw ParameterException(
                ("Dynamic parameter expected a value of the form a" + a + "b"
                + " but got:" + sv))
            }
            callPut(`object`, parameterized, sv.substring(0, aInd), sv.substring(aInd + 1))
        }
    }
    
    private fun callPut(`object`: Object?, parameterized: Parameterized, key: String?, value: String?) {
        try {
            val m: Method?
            m = findPut(parameterized.getType())
            m.invoke(parameterized.get(`object`), key, value)
        }catch (e: SecurityException) {
            e.printStackTrace()
        }catch (e: IllegalAccessException) {
            e.printStackTrace()
        }catch (e: NoSuchMethodException) {
            e.printStackTrace()
        }catch (e: InvocationTargetException) {
            e.printStackTrace()
        }
    }
    
    @Throws(SecurityException::class, NoSuchMethodException::class) private fun findPut(cls: Class<*>): Method? {
        return cls.getMethod("put", Object::class.java, Object::class.java)
    }
    
    fun getAssignment(): String? {
        return if (dynamicParameter != null) dynamicParameter.assignment() else ""
    }
    
    fun isHelp(): Boolean {
        return parameter != null && parameter.help()
    }
    
    fun isNonOverwritableForced(): Boolean {
        return parameter != null && parameter.forceNonOverwritable()
    }
}
