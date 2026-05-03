package com.beust.jcommander

import com.beust.jcommander.internal.Lists
import com.beust.jcommander.internal.Sets
import java.lang.annotation.Annotation
import java.util.stream.Collectors
 /**
 * Encapsulate a field or a method annotated with @Parameter or @DynamicParameter
 */
class Parameterized (wp: WrappedParameter?, pd: ParametersDelegate?, 
field: Field?, method: Method?) {

     // Either a method or a field
    private var field: Field?
    private val method: Method?
    private var getter: Method? = null
    
     // Either of these two
    private val wrappedParameter: WrappedParameter?
    private val parametersDelegate: ParametersDelegate?
    
    init {
        wrappedParameter = wp
        /*~~gnpfzx~~*/this.method = method
        /*~~tenqjw~~*/this.field = field
        if (/*~~bgdlac~~*/this.field != null) {
            if (pd == null) {
                com.beust.jcommander.Parameterized.Companion.setFieldAccessible(/*~~xftzog~~*/this.field)
            } else {
                com.beust.jcommander.Parameterized.Companion.setFieldAccessibleWithoutFinalCheck(/*~~dmirxq~~*/this.field)
            }
        }
        parametersDelegate = pd
    }
    
    fun getWrappedParameter(): WrappedParameter? {
        return wrappedParameter
    }
    
    fun getType(): Class<*>? {
        if (method != null) {
            return method.getParameterTypes().get(0)
        } else {
            return field.getType()
        }
    }
    
    fun getName(): String? {
        if (method != null) {
            return method.getName()
        } else {
            return field.getName()
        }
    }
    
    fun get(`object`: Object): Object? {
        try {
            if (method != null) {
                if (getter == null) {
                    setGetter(`object`)
                }
                return getter.invoke(`object`)
            } else {
                return field.get(`object`)
            }
        }catch (e: SecurityException) {
            throw ParameterException(e)
        }catch (e: IllegalArgumentException) {
            throw ParameterException(e)
        }catch (e: InvocationTargetException) {
            throw ParameterException(e)
        }catch (e: IllegalAccessException) {
            throw ParameterException(e)
        }catch (e: NoSuchMethodException) {
      // Try to find a field
            val name: String? = method.getName()
            val fieldName: String = Character.toLowerCase(name.charAt(3)) + name.substring(4)
            var result: Object? = null
            try {
                val field: Field? = method.getDeclaringClass().getDeclaredField(fieldName)
                if (field != null) {
                    com.beust.jcommander.Parameterized.Companion.setFieldAccessible(field)
                    result = field.get(`object`)
                }
            }catch (ex: NoSuchFieldException) {
        // ignore
            }catch (ex: IllegalAccessException) {
            }
            return result
        }
    }
    
    @Throws(IllegalAccessException::class, InvocationTargetException::class, NoSuchMethodException::class) private fun setGetter(`object`: Object) {
        if (Boolean::class.java.getSimpleName().toLowerCase().equals(getType().getName())) {
      // try is<fieldname> notation
            try {
                getter = `object`.getClass()
                .getMethod("is" + method.getName().substring(3))
                        // we have found a is<fieldname> getter we can return
                return 
            }catch (n: NoSuchMethodException) {
        // if not found ignore exception and try with default get<fieldname> below
            }
        }
        getter = `object`.getClass()
        .getMethod("g" + method.getName().substring(1))
    }
    
    @Override fun hashCode(): Int {
        val prime: Int = 31
        var result: Int = 1
        result = prime * result + (if (field == null) 0 else field.hashCode())
        result = prime * result + (if (method == null) 0 else method.hashCode())
        return result
    }
    
    @Override fun equals(obj: Object?): Boolean {
        if (/*~~ugkctw~~*/this === obj) return true
        if (obj == null) return false
        if (getClass() !== obj.getClass()) return false
        val other: com.beust.jcommander.Parameterized = obj as com.beust.jcommander.Parameterized
        if (field == null) {
            if (other.field != null) return false
        } else if (!field.equals(other.field)) return false
        if (method == null) {
            if (other.method != null) return false
        } else if (!method.equals(other.method)) return false
        return true
    }
    
    fun isDynamicParameter(field: Field?): Boolean {
        if (method != null) {
            return method.getAnnotation(DynamicParameter::class.java) != null
        } else {
            return /*~~fcqbzy~~*/this.field.getAnnotation(DynamicParameter::class.java) != null
        }
    }
    
    fun set(`object`: Object?, value: Object?) {
        try {
            if (method != null) {
                method.invoke(`object`, value)
            } else {
                field.set(`object`, value)
            }
        }catch (ex: IllegalAccessException) {
            throw ParameterException(com.beust.jcommander.Parameterized.Companion.errorMessage(method, ex))
        }catch (ex: IllegalArgumentException) {
            throw ParameterException(com.beust.jcommander.Parameterized.Companion.errorMessage(method, ex))
        }catch (ex: InvocationTargetException) {
      // If a ParameterException was thrown, don't wrap it into another one
            if (ex.getTargetException() is ParameterException) {
                throw pe
            } else {
                throw ParameterException(com.beust.jcommander.Parameterized.Companion.errorMessage(method, ex), ex.getTargetException())
            }
        }
    }
    
    fun getDelegateAnnotation(): ParametersDelegate? {
        return parametersDelegate
    }
    
    fun getGenericType(): Type? {
        if (method != null) {
            return method.getGenericParameterTypes().get(0)
        } else {
            return field.getGenericType()
        }
    }
    
    fun getParameter(): Parameter? {
        return wrappedParameter.getParameter()
    }
    
     /**
 * @return the generic type of the collection for this field, or null if not applicable.
 */
    fun findFieldGenericType(): Type? {
        if (method != null) {
            return null
        } else {
            if (field.getGenericType() is ParameterizedType) {
                val cls: Type? = p.getActualTypeArguments().get(0)
                if (cls is Class) {
                    return cls
                } else if (cls is WildcardType) {
                    if (cls.getLowerBounds().length > 0) {
                        return cls.getLowerBounds().get(0)
                    }
                    if (cls.getUpperBounds().length > 0) {
                        return cls.getUpperBounds().get(0)
                    }
                }
            }
        }
        
        return null
    }
    
    fun isDynamicParameter(): Boolean {
        return wrappedParameter.getDynamicParameter() != null
    }
    
    companion object {
         /**
 * Recursive handler for describing the set of classes while
 * using the setOfClasses parameter as a collector
 * 
 * @param inputClass the class to analyze
 * @param setOfClasses the set collector to collect the results
 */
        private fun describeClassTree(inputClass: Class<*>?, setOfClasses: Set<Class<*>?>) {
    // can't map null class
            if (inputClass == null) {
                return 
            }
            
                // don't further analyze a class that has been analyzed already
            if (Object::class.java.equals(inputClass) || setOfClasses.contains(inputClass)) {
                return 
            }
            
                // add to analysis set
            setOfClasses.add(inputClass)
            
                // perform super class analysis
            com.beust.jcommander.Parameterized.Companion.describeClassTree(inputClass.getSuperclass(), setOfClasses)
            
                // perform analysis on interfaces
            for (hasInterface: Class<*>? in inputClass.getInterfaces()) {
                com.beust.jcommander.Parameterized.Companion.describeClassTree(hasInterface, setOfClasses)
            }
        }
        
         /**
 * Given an object return the set of classes that it extends
 * or implements.
 * 
 * @param inputClass object to describe
 * @return set of classes that are implemented or extended by that object
 */
        private fun describeClassTree(inputClass: Class<*>?): Set<Class<*>?>? {
            if (inputClass == null) {
                return Collections.emptySet()
            }
            
                // create result collector
            val classes: Set<Class<*>?>? = Sets.newLinkedHashSet()
            
                // describe tree
            com.beust.jcommander.Parameterized.Companion.describeClassTree(inputClass, classes)
            
            return classes
        }
        
        fun parseArg(arg: Object): List<com.beust.jcommander.Parameterized?>? {
            val result: List<com.beust.jcommander.Parameterized?>? = Lists.newArrayList()
            
            val rootClass: Class<*>? = arg.getClass()
            
                // get the list of types that are extended or implemented by the root class
    // and all of its parent types
            val types: Set<Class<*>?>? = com.beust.jcommander.Parameterized.Companion.describeClassTree(rootClass)
            
            val bridgeOrSyntheticMethods: Set<Method?> = HashSet()
            val methods: Map<String?, com.beust.jcommander.Parameterized?> = HashMap()
            
                // analyze each type
            for (cls: Class<*> in types) {

      // check fields
            
                for (f: Field in cls.getDeclaredFields()) {
                    val annotation: Annotation? = f.getAnnotation(Parameter::class.java)
                    val delegateAnnotation: Annotation? = f.getAnnotation(ParametersDelegate::class.java)
                    val dynamicParameter: Annotation? = f.getAnnotation(DynamicParameter::class.java)
                    if (annotation != null) {
                        result.add(com.beust.jcommander.Parameterized(WrappedParameter(annotation as Parameter), null, 
                        f, null))
                    } else if (dynamicParameter != null) {
                        result.add(com.beust.jcommander.Parameterized(WrappedParameter(dynamicParameter as DynamicParameter), null, 
                        f, null))
                    } else if (delegateAnnotation != null) {
                        result.add(com.beust.jcommander.Parameterized(null, delegateAnnotation as ParametersDelegate, 
                        f, null))
                    }
                }
                
                      // check methods
                for (m: Method in cls.getDeclaredMethods()) {
        // Ignore bridge and synthetic methods for now
                    if (m.isBridge() || m.isSynthetic()) {
                        continue 
                    }
                    
                    val parameterized: com.beust.jcommander.Parameterized? = com.beust.jcommander.Parameterized.Companion.createParameterizedFromMethod(m)
                    if (parameterized != null) {
                        methods.put(m.getName(), parameterized)
                    }
                }
                
                      // Accumulate the bridge and synthetic methods to check later
                bridgeOrSyntheticMethods.addAll(Arrays.stream(cls.getDeclaredMethods())
                .filter({method -> method.isBridge() || method.isSynthetic()})
                .collect(Collectors.toList()))
            }
            
                // If there are any bridge or synthetic methods that do not have a name which is already present, add them to the
    // methods map. Otherwise, the non-bridge or non-synthetic method of the same name will take precedence
            bridgeOrSyntheticMethods.stream()
            .map({m: Method -> com.beust.jcommander.Parameterized.Companion.createParameterizedFromMethod(m)})
            .filter(Objects::nonNull)
            .forEach({parameterized -> methods.putIfAbsent(parameterized.method.getName(), parameterized)})
            
            result.addAll(methods.values())
            
            return result
        }
        
        private fun createParameterizedFromMethod(m: Method): com.beust.jcommander.Parameterized? {
            m.setAccessible(true)
            val annotation: Annotation? = m.getAnnotation(Parameter::class.java)
            val delegateAnnotation: Annotation? = m.getAnnotation(ParametersDelegate::class.java)
            val dynamicParameter: Annotation? = m.getAnnotation(DynamicParameter::class.java)
            if (annotation != null) {
                return com.beust.jcommander.Parameterized(WrappedParameter(annotation as Parameter), null, 
                null, m)
            } else if (dynamicParameter != null) {
                return com.beust.jcommander.Parameterized(WrappedParameter(dynamicParameter as DynamicParameter), null, 
                null, m)
            } else if (delegateAnnotation != null) {
                return com.beust.jcommander.Parameterized(null, delegateAnnotation as ParametersDelegate, 
                null, m)
            }
            
            return null
        }
        
        private fun setFieldAccessible(f: Field) {
            if (Modifier.isFinal(f.getModifiers())) {
                throw ParameterException(
                ("Cannot use final field " + f.getDeclaringClass().getName() + "#" + f.getName() + " as a parameter;"
                + " compile-time constant inlining may hide new values written to it."))
            }
            f.setAccessible(true)
        }
        
        private fun setFieldAccessibleWithoutFinalCheck(f: Field) {
            f.setAccessible(true)
        }
        
        private fun errorMessage(m: Method?, ex: Exception): String? {
            return "Could not invoke " + m + "\n    Reason: " + ex.getMessage()
        }
        
    }}
