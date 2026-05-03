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

import com.beust.jcommander.parser.DefaultParameterizedParser
import com.beust.jcommander.FuzzyMap.IKey
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.ResourceBundle
import java.util.concurrent.CopyOnWriteArrayList
 /**
 * The main class for JCommander. It's responsible for parsing the object that contains
 * all the annotated fields, parse the command line and assign the fields with the correct
 * values and a few other helper methods, such as usage().
 * 
 * The object(s) you pass in the constructor are expected to have one or more
 * \@Parameter annotations on them. You can pass either a single object, an array of objects
 * or an instance of Iterable. In the case of an array or Iterable, JCommander will collect
 * the \@Parameter annotations from all the objects passed in parameter.
 * 
 * @author Cedric Beust &lt;cedric@beust.com&gt;
 */
class JCommander private constructor(options: com.beust.jcommander.JCommander.Options) {
    protected var parameterizedParser: IParameterizedParser? = DefaultParameterizedParser()
    
     /**
 * A map to look up parameter description per option name.
 */
    private var descriptions: Map<IKey?, ParameterDescription?>? = null
    
     /**
 * The objects that contain fields annotated with @Parameter.
 */
    private val objects: List<Object?>? = Lists.newArrayList()
    
     /**
 * Description of a main parameter, which can be either a list of string or a single field. Both
 * are subject to converters before being returned to the user.
 */
    internal class MainParameter : IMainParameter {
         /**
 * This field/method will contain whatever command line parameter is not an option.
 */
        var parameterized: Parameterized? = null
        
         /**
 * The object on which we found the main parameter field.
 */
        var `object`: Object? = null
        
         /**
 * The annotation found on the main parameter field.
 */
        private var annotation: Parameter? = null
        
        private var description: ParameterDescription? = null
         /**
 * Non null if the main parameter is a List<String>.
</String> */
        private var multipleValue: List<Object?>? = null
        
         /**
 * The value of the single field, if it's not a List<String>.
</String> */
        private var singleValue: Object? = null
        
        private var firstTimeMainParameter: Boolean = true
        
        @Override fun getDescription(): ParameterDescription? {
            return description
        }
        
        fun addValue(convertedValue: Object?) {
            if (multipleValue != null) {
                multipleValue.add(convertedValue)
            } else if (singleValue != null) {
                throw ParameterException(("Only one main parameter allowed but found several: "
                + "\"" + singleValue + "\" and \"" + convertedValue + "\""))
            } else {
                singleValue = convertedValue
                parameterized.set(`object`, convertedValue)
            }
        }
    }
    
     /**
 * The usage formatter to use in [.usage].
 */
    private var usageFormatter: IUsageFormatter = DefaultUsageFormatter(/*~~uhrwkf~~*/this)
    
    private var mainParameter: com.beust.jcommander.JCommander.MainParameter? = null
    
     /**
 * A set of all the parameterizeds that are required. During the reflection phase,
 * this field receives all the fields that are annotated with required=true
 * and during the parsing phase, all the fields that are assigned a value
 * are removed from it. At the end of the parsing phase, if it's not empty,
 * then some required fields did not receive a value and an exception is
 * thrown.
 */
    private val requiredFields: Map<Parameterized?, ParameterDescription?>? = Maps.newHashMap()
    
     /**
 * A set of all the parameters validators to be applied.
 */
    private val parametersValidators: Set<IParametersValidator?>? = Sets.newHashSet()
    
     /**
 * A map of all the parameterized fields/methods.
 */
    private val fields: Map<Parameterized?, ParameterDescription?>? = Maps.newHashMap()
    
     /**
 * List of commands and their instance.
 */
    private val commands: Map<com.beust.jcommander.JCommander.ProgramName?, com.beust.jcommander.JCommander?>? = Maps.newLinkedHashMap()
    
     /**
 * Alias database for reverse lookup
 */
    private val aliasMap: Map<IKey?, com.beust.jcommander.JCommander.ProgramName?>? = Maps.newLinkedHashMap()
    
     /**
 * The name of the command after the parsing has run.
 */
    private var parsedCommand: String? = null
    
     /**
 * The name of command or alias as it was passed to the
 * command line
 */
    private var parsedAlias: String? = null
    
    private var programName: com.beust.jcommander.JCommander.ProgramName? = null
    
    private var helpWasSpecified: Boolean = false
    
    private val unknownArgs: List<String?>? = Lists.newArrayList()
    
    private var console: Console? = null
    
    private val options: com.beust.jcommander.JCommander.Options?
    
     /**
 * Options shared with sub commands
 */
    private class Options {
    
        private var bundle: ResourceBundle? = null
        
         /**
 * A default provider returns default values for the parameters.
 */
        private var defaultProvider: IDefaultProvider? = null
        
        private var parameterDescriptionComparator
        : Comparator<in ParameterDescription?>? = Comparator<ParameterDescription?> {
        p0, p1 -> val a0: WrappedParameter? = p0.getParameter()
        val a1: WrappedParameter? = p1.getParameter()
        if (a0 != null && a1 != null && !a0.category().equals(a1.category())) {
            return@Comparator a0.category().compareTo(a1.category())
        }
        if (a0 != null && a0.order() !== -1 && a1 != null && a1.order() !== -1) {
            val comp: Int = Integer.compare(a0.order(), a1.order())
            return@Comparator if (comp != 0) comp else p0.getLongestName().compareTo(p1.getLongestName())
        } else if (a0 != null && a0.order() !== -1) {
            return@Comparator -1
        } else if (a1 != null && a1.order() !== -1) {
            return@Comparator 1
        } else {
            return@Comparator p0.getLongestName().compareTo(p1.getLongestName())
        }
        
        } as Comparator<ParameterDescription?>
        private var columnSize: Int = 79
        private var acceptUnknownOptions: Boolean = false
        private var allowParameterOverwriting: Boolean = false
        private var expandAtSign: Boolean = true
        private var verbose: Int = 0
        private var caseSensitiveOptions: Boolean = true
        private var allowAbbreviatedOptions: Boolean = false
         /**
 * The factories used to look up string converters.
 */
        private val converterInstanceFactories: List<IStringConverterInstanceFactory?> = CopyOnWriteArrayList()
        private var atFileCharset: Charset? = Charset.defaultCharset()
    }
    
     /**
 * Creates a new un-configured JCommander object.
 */
    constructor() : this(com.beust.jcommander.JCommander.Options())
    
     /**
 * @param object The arg object expected to contain [Parameter] annotations.
 */
    constructor(`object`: Object) : this(`object`, null as ResourceBundle?)
    
     /**
 * @param object The arg object expected to contain [Parameter] annotations.
 * @param bundle The bundle to use for the descriptions. Can be null.
 */
    constructor(`object`: Object, @Nullable bundle: ResourceBundle?) : this(`object`, bundle, *null as kotlin.Array<String?>?)
    
     /**
 * @param object The arg object expected to contain [Parameter] annotations.
 * @param bundle The bundle to use for the descriptions. Can be null.
 * @param args The arguments to parse (optional).
 */
    constructor(`object`: Object, @Nullable bundle: ResourceBundle?, vararg args: String?) : this() {
        addObject(`object`)
        if (bundle != null) {
            setDescriptionsBundle(bundle)
        }
        createDescriptions()
        if (args != null) {
            parse(*args)
        }
    }
    
     /**
 * @param object The arg object expected to contain [Parameter] annotations.
 * @param args The arguments to parse (optional).
 * 
 */
    @Deprecated @kotlin.Deprecated("Construct a JCommander instance first and then call parse() on it.") 
    constructor(`object`: Object, vararg args: String?) : this(`object`) {
        parse(*args)
    }
    
    fun setParameterizedParser(parameterizedParser: IParameterizedParser?) {
        /*~~hzcdvx~~*/this.parameterizedParser = parameterizedParser
    }
    
     /**
 * Disables expanding `@file`.
 * 
 * JCommander supports the `@file` syntax, which allows you to put all your options
 * into a file and pass this file as parameter @param expandAtSign whether to expand `@file`.
 */
    fun setExpandAtSign(expandAtSign: Boolean) {
        options.expandAtSign = expandAtSign
    }
    
    fun setConsole(console: Console?) {
        /*~~gyujdx~~*/this.console = console}
    
     /**
 * @return a wrapper for a [java.io.PrintStream], typically [System.out].
 */
    @kotlin.jvm.Synchronized fun getConsole(): Console? {
        if (console == null) {
            try {
                val consoleMethod: Method? = System::class.java.getDeclaredMethod("console")
                val console: Object? = consoleMethod.invoke(null)
                /*~~miyfxu~~*/this.console = JDK6Console(console)
            }catch (t: Throwable) {
                console = DefaultConsole()
            }
        }
        return console
    }
    
     /**
 * Adds the provided arg object to the set of objects that this commander
 * will parse arguments into.
 * 
 * @param object The arg object expected to contain [Parameter]
 * annotations. If `object` is an array or is [Iterable],
 * the child objects will be added instead.
 */
 // declared final since this is invoked from constructors
    fun addObject(`object`: Object) {
        if (`object` is Iterable<*>) {
            // Iterable
            `object`.forEach(objects::add)
        } else if (`object`.getClass().isArray()) {
            // Array
            for (o: Object? in `object` as kotlin.Array<Object?>) {
                objects.add(o)
            }
        } else {
            // Single object
            objects.add(`object`)
        }
    }
    
     /**
 * Sets the [ResourceBundle] to use for looking up descriptions.
 * Set this to `null` to use description text directly.
 */
 // declared final since this is invoked from constructors
    fun setDescriptionsBundle(bundle: ResourceBundle?) {
        options.bundle = bundle
    }
    
     /**
 * Parse and validate the command line parameters.
 */
    fun parse(vararg args: String?) {
        try {
            parse(true,  /* validate */*args)
        }catch (ex: ParameterException) {
            ex.setJCommander(/*~~lcgyqh~~*/this)
            throw ex
        }
    }
    
     /**
 * Parse the command line parameters without validating them.
 */
    fun parseWithoutValidation(vararg args: String?) {
        parse(false,  /* no validation */*args)
    }
    
    private fun parse(validate: Boolean, vararg args: String?) {
        p(("Parsing \""
        + String.join(" ", args) + "\"\n  with:" + Strings.join(" ", objects.toArray())))
        
        if (descriptions == null) createDescriptions()
        initializeDefaultValues()
        parseValues(expandArgs(args), validate)
        if (validate) validateOptions()
    }
    
    private fun initializeDefaultValues() {
        if (options.defaultProvider != null) {
            descriptions.values().forEach({pd: ParameterDescription -> /*~~wjbbvb~~*/this.initializeDefaultValue(pd)})
            
            commands.forEach({key, value -> value.initializeDefaultValues()})
        }
    }
    
     /**
 * Make sure that all the required parameters have received a value and that
 * all provided parameters have a value compliant to all given rules.
 */
    private fun validateOptions() {
        // No validation if we found a help parameter
        if (helpWasSpecified) {
            return 
        }
        
        if (!requiredFields.isEmpty()) {
            val missingFields: List<String?> = ArrayList()
            requiredFields.values().forEach({pd -> missingFields.add("[" + String.join(" | ", pd.getParameter().names()) + "]")}
            )
            val message: String? = String.join(", ", missingFields)
            throw ParameterException(("The following "
            + com.beust.jcommander.JCommander.Companion.pluralize(requiredFields.size(), "option is required: ", "options are required: ")
            + message))
        }
        
        if (mainParameter != null && mainParameter.description != null) {
            val mainParameterDescription: ParameterDescription = mainParameter.description
                        // Make sure we have a main parameter if it was required
            if (mainParameterDescription.getParameter().required() && 
            !mainParameterDescription.isAssigned()) {
                throw ParameterException(("Main parameters are required (\""
                + mainParameterDescription.getDescription() + "\")"))
            }
            
                        // If the main parameter has an arity, make sure the correct number of parameters was passed
            val arity: Int = mainParameterDescription.getParameter().arity()
            if (arity != Parameter.DEFAULT_ARITY) {
                val value: Object? = mainParameterDescription.getParameterized().get(mainParameter.`object`)
                if (List::class.java.isAssignableFrom(value.getClass())) {
                    val size: Int = (value as List<*>).size()
                    if (size != arity) {
                        throw ParameterException(("There should be exactly " + arity + " main parameters but "
                        + size + " were found"))
                    }
                }
                
            }
        }
        
        val nameValuePairs: Map<String?, Object?>? = Maps.newHashMap()
        fields.values().forEach({pd -> nameValuePairs.put(pd.getLongestName(), pd.getValue())}
        )
        
        parametersValidators.forEach({parametersValidator -> parametersValidator.validate(nameValuePairs)}
        )
    }
    
     /**
 * Expand the command line parameters to take @ parameters into account.
 * When @ is encountered, the content of the file that follows is inserted
 * in the command line.
 * 
 * @param originalArgv the original command line parameters
 * @return the new and enriched command line parameters
 */
    private fun expandArgs(originalArgv: kotlin.Array<String?>): kotlin.Array<String?>? {
        val vResult1: List<String?>? = Lists.newArrayList()
        
                //
        // Expand dynamic args
        //
        for (arg: String in originalArgv) {
            val expanded: List<String?>? = expandDynamicArg(arg)
            vResult1.addAll(expanded)
        }
        
                // Expand separators
        //
        val vResult2: List<String?>? = Lists.newArrayList()
        vResult1.forEach({arg -> if (isOption(arg)) {
            val sep: String? = getSeparatorFor(arg)
            if (!" ".equals(sep)) {
                val sp: kotlin.Array<String?>? = arg.split("[" + sep + "]", 2)
                for (ssp: String? in sp) {
                    vResult2.add(ssp)
                }
            } else {
                vResult2.add(arg)
            }
        } else {
            vResult2.add(arg)
        }
        })
        
        return vResult2.toArray(arrayOfNulls<String>(vResult2.size()))
    }
    
    private fun expandDynamicArg(arg: String): List<String?>? {
        for (pd: ParameterDescription in descriptions.values()) {
            if (pd.isDynamicParameter()) {
                for (name: String in pd.getParameter().names()) {
                    if (arg.startsWith(name) && !arg.equals(name) && arg.contains(pd.getParameter().getAssignment())) {
                        return List.of(name, arg.substring(name.length()))
                    }
                }
            }
        }
        
        return List.of(arg)
    }
    
    private fun matchArg(arg: String, key: IKey): Boolean {
        val kn: String? = if (options.caseSensitiveOptions) 
        key.getName()
        else 
        key.getName().toLowerCase()
        if (options.allowAbbreviatedOptions) {
            if (kn.startsWith(arg)) return true
        } else {
            val pd: ParameterDescription? = descriptions.get(key)
            if (pd != null) {
                // It's an option. If the option has a separator (e.g. -author==foo) then
                // we only do a beginsWith match
                val separator: String? = getSeparatorFor(arg)
                if (!" ".equals(separator)) {
                    if (arg.startsWith(kn)) return true
                } else {
                    if (kn.equals(arg)) return true
                }
            } else {
                // It's a command do a strict equality check
                if (kn.equals(arg)) return true
            }
        }
        return false
    }
    
    private fun isOption(passedArg: String): Boolean {
        return options.acceptUnknownOptions || isNamedOption(passedArg)
    }
    
    private fun isNamedOption(passedArg: String): Boolean {
    
        val arg: String? = if (options.caseSensitiveOptions) passedArg else passedArg.toLowerCase()
        
        for (key: IKey in descriptions.keySet()) {
            if (matchArg(arg, key)) return true
        }
        for (key: IKey in commands.keySet()) {
            if (matchArg(arg, key)) return true
        }
        for (key: IKey in aliasMap.keySet()) {
            if (matchArg(arg, key)) return true
        }
        
        return false
    }
    
    private fun getPrefixDescriptionFor(arg: String?): ParameterDescription? {
        for (es: Map.Entry<IKey?, ParameterDescription?> in descriptions.entrySet()) {
            if (Strings.startsWith(arg, es.getKey().getName(), options.caseSensitiveOptions)) return es.getValue()
        }
        
        return null
    }
    
     /**
 * If arg is an option, we can look it up directly, but if it's a value,
 * we need to find the description for the option that precedes it.
 */
    private fun getDescriptionFor(arg: String?): ParameterDescription? {
        return getPrefixDescriptionFor(arg)
    }
    
    private fun getSeparatorFor(arg: String?): String? {
        val pd: ParameterDescription? = getDescriptionFor(arg)
        
                // Could be null if only main parameters were passed
        if (pd != null) {
            val p: Parameters? = pd.getObject().getClass().getAnnotation(Parameters::class.java)
            if (p != null) return p.separators()
        }
        
        return " "
    }
    
     /**
 * Reads the file specified by filename and returns the file content as a string.
 * End of lines are replaced by a space.
 * 
 * @param fileName the command line filename
 * @return the file content as a string.
 */
    private fun readFile(fileName: String?): List<String?>? {
        val result: List<String?>? = Lists.newArrayList()
        
        try {
            Files.newBufferedReader(Path.of(fileName), options.atFileCharset).use {
            bufRead -> var line: String?
                        // Read through file one line at time. Print line # and line
            while ((bufRead.readLine().also { line = it }) != null) {
                // Allow empty lines and # comments in these at files
                if (!line.isEmpty() && !line.trim().startsWith("#")) {
                    result.addAll(List.of(line.split("\\s", 2)))
                }
            }
            
            } }catch (e: IOException) {
            throw ParameterException("Could not read file " + fileName + ": " + e)
        }
        
        return result
    }
    
     /**
 * Create the ParameterDescriptions for all the \@Parameter found.
 */
    fun createDescriptions() {
        descriptions = Maps.newHashMap()
        
        objects.forEach({`object`: Object -> /*~~ijoahh~~*/this.addDescription(`object`)})
    }
    
    private fun addDescription(`object`: Object) {
        val cls: Class<*>? = `object`.getClass()
        
        val parameters: Parameters? = cls.getAnnotation(Parameters::class.java)
        if (parameters != null) {
            val parametersValidatorClasses: kotlin.Array<Class<out IParametersValidator?>?>? = parameters.parametersValidators()
            for (parametersValidatorClass: Class<out IParametersValidator?> in parametersValidatorClasses) {
                try {
                    val parametersValidator: IParametersValidator? = parametersValidatorClass.getDeclaredConstructor().newInstance()
                    parametersValidators.add(parametersValidator)
                }catch (e: ReflectiveOperationException) {
                    throw ParameterException("Cannot instantiate rule: " + parametersValidatorClass, e)
                }
            }
        }
        
        val parameterizeds: List<Parameterized?>? = parameterizedParser.parseArg(`object`)
        for (parameterized: Parameterized in parameterizeds) {
            val wp: WrappedParameter? = parameterized.getWrappedParameter()
            if (wp != null && wp.getParameter() != null) {
                val annotation: Parameter? = wp.getParameter()
                                //
                // @Parameter
                //
                val p: Parameter? = annotation
                if (p.names().length === 0) {
                    p("Found main parameter:" + parameterized)
                    if (mainParameter != null) {
                        throw ParameterException(("Only one @Parameter with no names attribute is"
                        + " allowed, found:" + mainParameter + " and " + parameterized))
                    }
                    mainParameter = com.beust.jcommander.JCommander.MainParameter()
                    mainParameter.parameterized = parameterized
                    mainParameter.`object` = `object`
                    mainParameter.annotation = p
                    mainParameter.description = 
                    ParameterDescription(`object`, p, parameterized, options.bundle, /*~~vlorom~~*/this)
                } else {
                    val pd: ParameterDescription = 
                    ParameterDescription(`object`, p, parameterized, options.bundle, /*~~nuemjj~~*/this)
                    for (name: String? in p.names()) {
                        if (descriptions.containsKey(StringKey(name))) {
                            throw ParameterException("Found the option " + name + " multiple times")
                        }
                        p("Adding description for " + name)
                        fields.put(parameterized, pd)
                        descriptions.put(StringKey(name), pd)
                        
                        if (p.required()) requiredFields.put(parameterized, pd)
                    }
                }
            } else if (parameterized.getDelegateAnnotation() != null) {
                //
                // @ParametersDelegate
                //
                val delegateObject: Object? = parameterized.get(`object`)
                if (delegateObject == null) {
                    throw ParameterException(("Delegate field '" + parameterized.getName()
                    + "' cannot be null."))
                }
                addDescription(delegateObject)
            } else if (wp != null && wp.getDynamicParameter() != null) {
                //
                // @DynamicParameter
                //
                val dp: DynamicParameter? = wp.getDynamicParameter()
                for (name: String? in dp.names()) {
                    if (descriptions.containsKey(StringKey(name))) {
                        throw ParameterException("Found the option " + name + " multiple times")
                    }
                    p("Adding description for " + name)
                    val pd: ParameterDescription = 
                    ParameterDescription(`object`, dp, parameterized, options.bundle, /*~~mslbys~~*/this)
                    fields.put(parameterized, pd)
                    descriptions.put(StringKey(name), pd)
                    
                    if (dp.required()) requiredFields.put(parameterized, pd)
                }
            }
        }
    }
    
    private fun initializeDefaultValue(pd: ParameterDescription) {
        for (optionName: String? in pd.getParameter().names()) {
            val def: String? = options.defaultProvider.getDefaultValueFor(optionName)
            if (def != null) {
                p("Initializing " + optionName + " with default value:" + def)
                pd.addValue(def, true /* default */)
                                // remove the parameter from the list of fields to be required
                requiredFields.remove(pd.getParameterized())
                return 
            }
        }
    }
    
     /**
 * Main method that parses the values and initializes the fields accordingly.
 */
    private fun parseValues(args: kotlin.Array<String?>, validate: Boolean) {
        // This boolean becomes true if we encounter a command, which indicates we need
        // to stop parsing (the parsing of the command will be done in a sub JCommander
        // object)
        var args: kotlin.Array<String?> = args
        var commandParsed: Boolean = false
        var i: Int = 0
        var isDashDash: Boolean = false // once we encounter --, everything goes into the main parameter
        while (i < args.size && !commandParsed) {
            val arg: String? = args.get(i)
            
                        // 
            // Expand @
            // 
            if (arg.startsWith("@") && options.expandAtSign) {
                val fileName: String? = arg.substring(1)
                val fileArgs: List<String?>? = readFile(fileName)
                
                                
                // Create a new array to hold the expanded arguments
                val newArgs: kotlin.Array<String?> = arrayOfNulls<String>(args.size + fileArgs.size() - 1)
                
                                // Copy the existing arguments before the '@' argument
                System.arraycopy(args, 0, newArgs, 0, i)
                
                                // Copy the arguments from the file
                System.arraycopy(fileArgs.toArray(), 0, newArgs, i, fileArgs.size())
                
                                // Copy the remaining arguments after the '@' argument
                System.arraycopy(args, i + 1, newArgs, i + fileArgs.size(), args.size - i - 1)
                
                args = newArgs
                continue 
            }
            
            val a: String? = com.beust.jcommander.JCommander.Companion.trim(arg)
            args.get(i) = a
            p("Parsing arg: " + a)
            
            val jc: com.beust.jcommander.JCommander? = findCommandByAlias(arg)
            var increment: Int = 1
            if (!isDashDash && !"--".equals(a) && isOption(a) && jc == null) {
                //
                // Option
                //
                val pd: ParameterDescription? = findParameterDescription(a)
                
                if (pd != null) {
                    if (pd.getParameter().password()) {
                        increment = processPassword(args, i, pd, validate)
                    } else {
                        if (pd.getParameter().variableArity()) {
                            //
                            // Variable arity?
                            //
                            increment = processVariableArity(args, i, pd, validate)
                        } else {
                            //
                            // Regular option
                            //
                            val fieldType: Class<*>? = pd.getParameterized().getType()
                            
                                                        // Boolean, set to true as soon as we see it, unless it specified
                            // an arity of 1, in which case we need to read the next value
                            if (pd.getParameter().arity() === -1 && isBooleanType(fieldType)) {
                                handleBooleanOption(pd, fieldType)
                            } else {
                                increment = processFixedArity(args, i, pd, validate, fieldType)
                            }
                                                        // If it's a help option, remember for later
                            if (pd.isHelp()) {
                                helpWasSpecified = true
                            }
                        }
                    }
                } else {
                    if (options.acceptUnknownOptions) {
                        unknownArgs.add(arg)
                        i++
                        while (i < args.size && !isOption(args.get(i))) {
                            unknownArgs.add(args.get(i++))
                        }
                        increment = 0
                    } else {
                        throw ParameterException("Unknown option: " + arg)
                    }
                }
            } else {
                //
                // Main parameter
                //
                if ("--".equals(arg) && !isDashDash) {
                    isDashDash = true
                }
                else if (commands.isEmpty()) {
                    //
                    // Regular (non-command) parsing
                    //
                    initMainParameterValue(arg)
                    val value: String? = a // If there's a non-quoted version, prefer that one
                    
                    for (validator: Class<out IParameterValidator?>? in mainParameter.annotation.validateWith()
                    ) {
                        mainParameter.description.validateParameter(validator, 
                        "Default", value)
                    }
                    
                    var convertedValue: Object? = value
                    
                                        // Fix
                    // Main parameter doesn't support Converter
                    // https://github.com/cbeust/jcommander/issues/380
                    if (mainParameter.annotation.converter() != null && mainParameter.annotation.converter() !== NoConverter::class.java) {
                        convertedValue = convertValue(mainParameter.parameterized, mainParameter.parameterized.getType(), null, value)
                    }
                    
                    val genericType: Type? = mainParameter.parameterized.getGenericType()
                    if (genericType is ParameterizedType) {
                        val cls: Type? = genericType.getActualTypeArguments().get(0)
                        if (cls is Class) {
                            convertedValue = convertValue(mainParameter.parameterized, cls, null, value)
                        }
                    }
                    
                    
                    mainParameter.description.setAssigned(true)
                    mainParameter.addValue(convertedValue)
                } else {
                    //
                    // Command parsing
                    //
                    if (jc == null && validate) {
                        throw MissingCommandException("Expected a command, got " + arg, arg)
                    } else if (jc != null) {
                        parsedCommand = jc.programName.name
                        parsedAlias = arg //preserve the original form
                        
                                                // Found a valid command, ask it to parse the remainder of the arguments.
                        // Setting the boolean commandParsed to true will force the current
                        // loop to end.
                        jc.parse(validate, *com.beust.jcommander.JCommander.Companion.subArray(args, i + 1))
                        commandParsed = true
                    }
                }
            }
            i += increment
        }
        
                // Mark the parameter descriptions held in fields as assigned
        descriptions.values().forEach({
        parameterDescription -> if (parameterDescription.isAssigned()) {
            fields.get(parameterDescription.getParameterized()).setAssigned(true)
        }
        
                    // if the parameter has a default value (not the one assigned by DefaultProvider
            // but the one assigned on the variable initialization), make it as assigned and
            // remove it from the list of parameters to be required
        if (parameterDescription.getDefault() != null && !parameterDescription.getParameterized().getType().isPrimitive()) {
            requiredFields.remove(parameterDescription.getParameterized())
        }
        
        })
        
    }
    
    private fun isBooleanType(fieldType: Class<*>?): Boolean {
        return Boolean::class.java.isAssignableFrom(fieldType) || Boolean::class.javaPrimitiveType.isAssignableFrom(fieldType)
    }
    
    private fun handleBooleanOption(pd: ParameterDescription, fieldType: Class<*>) {
      // Flip the value this boolean was initialized with
        val value: Boolean? = pd.getParameterized().get(pd.getObject()) as Boolean?
        if (value != null) {
            pd.addValue(if (value) "false" else "true")
        } else if (!fieldType.isPrimitive()) {
            pd.addValue("true")
        }
        requiredFields.remove(pd.getParameterized())
    }
    
    private inner class DefaultVariableArity : IVariableArity {
    
        @Override fun processVariableArity(optionName: String?, options: kotlin.Array<String?>): Int {
            var i: Int = 0
                        // For variableArity we consume everything until we hit a known parameter
            while (i < options.size && !isNamedOption(options.get(i))) {
                i++
            }
            return i
        }
    }
    
    private val DEFAULT_VARIABLE_ARITY: IVariableArity = com.beust.jcommander.JCommander.DefaultVariableArity()
    
    init {
        Objects.requireNonNull(options, "options")
        /*~~frtxlf~~*/this.options = options
        if (options.converterInstanceFactories.isEmpty()) {
            addConverterFactory(DefaultConverterFactory())
        }
    }
    
    private fun determineArity(args: kotlin.Array<String?>, index: Int, pd: ParameterDescription, va: IVariableArity): Int {
        val currentArgs: List<String?>? = Lists.newArrayList()
        for (j in index + 1 ..< args.size) {
            currentArgs.add(args.get(j))
        }
        return va.processVariableArity(pd.getParameter().names().get(0), 
        currentArgs.toArray(arrayOfNulls<String>(0)))
    }
    
     /**
 * @return the number of options that were processed.
 */
    private fun processPassword(args: kotlin.Array<String?>, index: Int, pd: ParameterDescription, validate: Boolean): Int {
        val passwordArity: Int = determineArity(args, index, pd, DEFAULT_VARIABLE_ARITY)
        if (passwordArity == 0) {
            // password option with password not specified, use the Console to retrieve the password
            val password: CharArray? = readPassword(pd.getDescription(), pd.getParameter().echoInput())
            pd.addValue(String(password))
            requiredFields.remove(pd.getParameterized())
            return 1
        } else if (passwordArity == 1) {
            // password option with password specified
            return processFixedArity(args, index, pd, validate, List::class.java, 1)
        } else {
            throw ParameterException("Password parameter must have at most 1 argument.")
        }
    }
    
     /**
 * @return the number of options that were processed.
 */
    private fun processVariableArity(args: kotlin.Array<String?>, index: Int, pd: ParameterDescription, validate: Boolean): Int {
        val arg: Object? = pd.getObject()
        val va: IVariableArity?
        if (arg !is IVariableArity) {
            va = DEFAULT_VARIABLE_ARITY
        } else {
            va = arg
        }
        
        val arity: Int = determineArity(args, index, pd, va)
        val result: Int = processFixedArity(args, index, pd, validate, List::class.java, arity)
        return result
    }
    
    private fun processFixedArity(args: kotlin.Array<String?>, index: Int, pd: ParameterDescription, validate: Boolean, 
    fieldType: Class<*>): Int {
        // Regular parameter, use the arity to tell use how many values
        // we need to consume
        val arity: Int = pd.getParameter().arity()
        val n: Int = (if (arity != -1) arity else 1)
        
        return processFixedArity(args, index, pd, validate, fieldType, n)
    }
    
    private fun processFixedArity(args: kotlin.Array<String?>, originalIndex: Int, pd: ParameterDescription, validate: Boolean, 
    fieldType: Class<*>, arity: Int): Int {
        var index: Int = originalIndex
        val arg: String? = args.get(index)
                // Special case for boolean parameters of arity 0
        if (arity == 0 && isBooleanType(fieldType)) {
            handleBooleanOption(pd, fieldType)
        } else if (arity == 0) {
            throw ParameterException("Expected a value after parameter " + arg)
        } else if (index < args.size - 1) {
            val offset: Int = if ("--".equals(args.get(index + 1))) 1 else 0
            
            var finalValue: Object? = null
            if (index + arity < args.size) {
                for (j in 1 .. arity) {
                    val value: String? = args.get(index + j + offset)
                    finalValue = pd.addValue(arg, value, false, validate, j - 1)
                    requiredFields.remove(pd.getParameterized())
                }
                
                if (finalValue != null && validate) {
                    pd.validateValueParameter(arg, finalValue)
                }
                index += arity + offset
            } else {
                throw ParameterException("Expected " + arity + " values after " + arg)
            }
        } else {
            throw ParameterException("Expected a value after parameter " + arg)
        }
        
        return arity + 1
    }
    
     /**
 * Invoke Console.readPassword through reflection to avoid depending
 * on Java 6.
 */
    private fun readPassword(description: String?, echoInput: Boolean): CharArray? {
        getConsole().print(description.toString() + ": ")
        return getConsole().readPassword(echoInput)
    }
    
     /**
 * Init the main parameter with the given arg. Note that the main parameter can be either a List<String>
 * or a single value.
</String> */
    private fun initMainParameterValue(arg: String?) {
        if (mainParameter == null) {
            throw ParameterException(
            "Was passed main parameter '" + arg + "' but no main parameter was defined in your arg class")
        }
        
        val `object`: Object? = mainParameter.parameterized.get(mainParameter.`object`)
        val type: Class<*>? = mainParameter.parameterized.getType()
        
                // If it's a List<String>, we might need to create that list and then add the value to it.
        if (List::class.java.isAssignableFrom(type)) {
            val result: List<Object?>?
            if (`object` == null) {
                result = Lists.newArrayList()
            } else {
                result = `object` as List
            }
            
            if (mainParameter.firstTimeMainParameter) {
                result.clear()
                mainParameter.firstTimeMainParameter = false
            }
            
            mainParameter.multipleValue = result
            mainParameter.parameterized.set(mainParameter.`object`, result)
        }
        
    }
    
    fun getMainParameterDescription(): String? {
        if (descriptions == null) createDescriptions()
        return if (mainParameter == null) 
        null
        else 
        if (mainParameter.annotation != null) mainParameter.annotation.description() else null
    }
    
     /**
 * Set the program name (used only in the usage).
 */
    fun setProgramName(name: String?) {
        setProgramName(name, *arrayOfNulls<String>(0))
    }
    
     /**
 * Get the program name (used only in the usage).
 */
    fun getProgramName(): String? {
        return if (programName == null) null else programName.getName()
    }
    
     /**
 * Get the program display name (used only in the usage).
 */
    fun getProgramDisplayName(): String? {
        return if (programName == null) null else programName.getDisplayName()
    }
    
     /**
 * Set the program name
 * 
 * @param name    program name
 * @param aliases aliases to the program name
 */
    fun setProgramName(name: String?, vararg aliases: String?) {
        programName = com.beust.jcommander.JCommander.ProgramName(name, List.of(aliases))
    }
    
     /**
 * Prints the usage on [.getConsole] using the underlying [.usageFormatter].
 */
    fun usage() {
        val sb: StringBuilder = StringBuilder()
        usageFormatter.usage(sb)
        getConsole().print(sb)
    }
    
     /**
 * Display the usage for this command.
 */
    fun usage(commandName: String?) {
        val sb: StringBuilder = StringBuilder()
        usageFormatter.usage(commandName, sb)
        getConsole().print(sb)
    }
    
     /**
 * Store the help for the command in the passed string builder.
 */
    fun usage(commandName: String?, out: StringBuilder?) {
        usageFormatter.usage(commandName, out, "")
    }
    
     /**
 * Store the help for the command in the passed string builder, indenting
 * every line with "indent".
 */
    fun usage(commandName: String?, out: StringBuilder?, indent: String?) {
        usageFormatter.usage(commandName, out, indent)
    }
    
     /**
 * Store the help in the passed string builder.
 */
    fun usage(out: StringBuilder?) {
        usageFormatter.usage(out, "")
    }
    
    fun usage(out: StringBuilder?, indent: String?) {
        usageFormatter.usage(out, indent)
    }
    
     /**
 * Sets the usage formatter.
 * 
 * @param usageFormatter the usage formatter
 * @throws IllegalArgumentException if the argument is null
 */
    fun setUsageFormatter(usageFormatter: IUsageFormatter) {
        requireNotNull(usageFormatter) {"Argument UsageFormatter must not be null"} 
        /*~~hdxvvc~~*/this.usageFormatter = usageFormatter
    }
    
     /**
 * Returns the usage formatter.
 * 
 * @return the usage formatter
 */
    fun getUsageFormatter(): IUsageFormatter? {
        return usageFormatter
    }
    
    fun getOptions(): com.beust.jcommander.JCommander.Options? {
        return options
    }
    
    fun getDescriptions(): Map<IKey?, ParameterDescription?>? {
        return descriptions
    }
    
    fun getMainParameter(): IMainParameter? {
        return mainParameter
    }
    
    class Builder {
        private val jCommander: com.beust.jcommander.JCommander = com.beust.jcommander.JCommander()
        private var args: kotlin.Array<String?>? = null
        
         /**
 * Adds the provided arg object to the set of objects that this commander
 * will parse arguments into.
 * 
 * @param o The arg object expected to contain [Parameter]
 * annotations. If `object` is an array or is [Iterable],
 * the child objects will be added instead.
 */
        fun addObject(o: Object): com.beust.jcommander.JCommander.Builder {
            jCommander.addObject(o)
            return /*~~tnpyyl~~*/this
        }
        
         /**
 * Sets the [ResourceBundle] to use for looking up descriptions.
 * Set this to `null` to use description text directly.
 */
        fun resourceBundle(bundle: ResourceBundle?): com.beust.jcommander.JCommander.Builder {
            jCommander.setDescriptionsBundle(bundle)
            return /*~~zbrgre~~*/this
        }
        
        fun args(args: kotlin.Array<String?>?): com.beust.jcommander.JCommander.Builder {
            /*~~iiispb~~*/this.args = args
            return /*~~gnkbbo~~*/this
        }
        
        fun console(console: Console?): com.beust.jcommander.JCommander.Builder {
            jCommander.setConsole(console)
            return /*~~kmione~~*/this
        }
        
         /**
 * Disables expanding `@file`.
 * 
 * JCommander supports the `@file` syntax, which allows you to put all your options
 * into a file and pass this file as parameter @param expandAtSign whether to expand `@file`.
 */
        fun expandAtSign(expand: Boolean?): com.beust.jcommander.JCommander.Builder {
            jCommander.setExpandAtSign(expand)
            return /*~~ernsht~~*/this
        }
        
         /**
 * Set the program name (used only in the usage).
 */
        fun programName(name: String?): com.beust.jcommander.JCommander.Builder {
            jCommander.setProgramName(name)
            return /*~~owscxs~~*/this
        }
        
        fun columnSize(columnSize: Int): com.beust.jcommander.JCommander.Builder {
            jCommander.setColumnSize(columnSize)
            return /*~~mbrzwn~~*/this
        }
        
         /**
 * Define the default provider for this instance.
 */
        fun defaultProvider(provider: IDefaultProvider?): com.beust.jcommander.JCommander.Builder {
            jCommander.setDefaultProvider(provider)
            return /*~~povgdt~~*/this
        }
        
         /**
 * Adds a factory to lookup string converters. The added factory is used prior to previously added factories.
 * @param factory the factory determining string converters
 */
        fun addConverterFactory(factory: IStringConverterFactory): com.beust.jcommander.JCommander.Builder {
            jCommander.addConverterFactory(factory)
            return /*~~ybccxx~~*/this
        }
        
        fun verbose(verbose: Int): com.beust.jcommander.JCommander.Builder {
            jCommander.setVerbose(verbose)
            return /*~~tswofg~~*/this
        }
        
        fun allowAbbreviatedOptions(b: Boolean): com.beust.jcommander.JCommander.Builder {
            jCommander.setAllowAbbreviatedOptions(b)
            return /*~~jjuuzg~~*/this
        }
        
        fun acceptUnknownOptions(b: Boolean): com.beust.jcommander.JCommander.Builder {
            jCommander.setAcceptUnknownOptions(b)
            return /*~~cicnbd~~*/this
        }
        
        fun allowParameterOverwriting(b: Boolean): com.beust.jcommander.JCommander.Builder {
            jCommander.setAllowParameterOverwriting(b)
            return /*~~ivkcee~~*/this
        }
        
        fun atFileCharset(charset: Charset?): com.beust.jcommander.JCommander.Builder {
            jCommander.setAtFileCharset(charset)
            return /*~~hjgrtf~~*/this
        }
        
        fun addConverterInstanceFactory(factory: IStringConverterInstanceFactory?): com.beust.jcommander.JCommander.Builder {
            jCommander.addConverterInstanceFactory(factory)
            return /*~~bbnpuw~~*/this
        }
        
        fun addCommand(command: Object): com.beust.jcommander.JCommander.Builder {
            jCommander.addCommand(command)
            return /*~~lciuus~~*/this
        }
        
        fun addCommand(name: String?, command: Object, vararg aliases: String?): com.beust.jcommander.JCommander.Builder {
            jCommander.addCommand(name, command, *aliases)
            return /*~~azrgxs~~*/this
        }
        
        fun usageFormatter(usageFormatter: IUsageFormatter): com.beust.jcommander.JCommander.Builder {
            jCommander.setUsageFormatter(usageFormatter)
            return /*~~zbvbuv~~*/this
        }
        
        fun build(): com.beust.jcommander.JCommander? {
            if (args != null) {
                jCommander.parse(*args)
            }
            return jCommander
        }
    }
    
    fun getFields(): Map<Parameterized?, ParameterDescription?>? {
        return fields
    }
    
    fun getParameterDescriptionComparator(): Comparator<in ParameterDescription?>? {
        return options.parameterDescriptionComparator
    }
    
    fun setParameterDescriptionComparator(c: Comparator<in ParameterDescription?>?) {
        options.parameterDescriptionComparator = c
    }
    
    fun setColumnSize(columnSize: Int) {
        options.columnSize = columnSize
    }
    
    fun getColumnSize(): Int {
        return options.columnSize
    }
    
    fun getBundle(): ResourceBundle? {
        return options.bundle
    }
    
     /**
 * @return a Collection of all the \@Parameter annotations found on the
 * target class. This can be used to display the usage() in a different
 * format (e.g. HTML).
 */
    fun getParameters(): List<ParameterDescription?>? {
        return ArrayList(fields.values())
    }
    
     /**
 * @return the main parameter description or null if none is defined.
 */
    fun getMainParameterValue(): ParameterDescription? {
        return if (mainParameter == null) null else mainParameter.description
    }
    
    private fun p(string: String?) {
        if (options.verbose > 0 || System.getProperty(com.beust.jcommander.JCommander.Companion.DEBUG_PROPERTY) != null) {
            getConsole().println("[JCommander] " + string)
        }
    }
    
     /**
 * Define the default provider for this instance.
 */
    fun setDefaultProvider(defaultProvider: IDefaultProvider?) {
        options.defaultProvider = defaultProvider
    }
    
     /**
 * Adds a factory to lookup string converters. The added factory is used prior to previously added factories.
 * @param converterFactory the factory determining string converters
 */
    fun addConverterFactory(converterFactory: IStringConverterFactory) {
        addConverterInstanceFactory(IStringConverterInstanceFactory {
        parameter, forType, optionName -> var optionName = optionName
        val converterClass: Class<out IStringConverter<*>?>? = converterFactory.getConverter(forType)
        try {
            if (optionName == null) {
                optionName = if (parameter.names().length > 0) parameter.names().get(0) else "[Main class]"
            }
            return@addConverterInstanceFactory if (converterClass != null) com.beust.jcommander.JCommander.Companion.instantiateConverter<T?>(optionName, converterClass) else null
        }catch (e: InstantiationException) {
            throw ParameterException(e)
        }catch (e: IllegalAccessException) {
            throw ParameterException(e)
        }catch (e: InvocationTargetException) {
            throw ParameterException(e)
        }
        
        })
    }
    
     /**
 * Adds a factory to lookup string converters. The added factory is used prior to previously added factories.
 * @param converterInstanceFactory the factory generating string converter instances
 */
    fun addConverterInstanceFactory(converterInstanceFactory: IStringConverterInstanceFactory?) {
        options.converterInstanceFactories.addFirst(converterInstanceFactory)
    }
    
    private fun findConverterInstance(parameter: Parameter?, forType: Class<*>?, optionName: String?): IStringConverter<*>? {
        for (f: IStringConverterInstanceFactory in options.converterInstanceFactories) {
            val result: IStringConverter<*>? = f.getConverterInstance(parameter, forType, optionName)
            if (result != null) return result
        }
        
        return null
    }
    
     /**
 * @param type The type of the actual parameter
 * @param optionName
 * @param value The value to convert
 */
    fun convertValue(parameterized: Parameterized, type: Class, optionName: String?, value: String?): Object? {
        var optionName: String? = optionName
        val annotation: Parameter? = parameterized.getParameter()
        
                // Do nothing if it's a @DynamicParameter
        if (annotation == null) return value
        
        if (optionName == null) {
            optionName = if (annotation.names().length > 0) annotation.names().get(0) else "[Main class]"
        }
        
        var converter: IStringConverter<*>? = null
        if (type.isAssignableFrom(List::class.java) || type.isAssignableFrom(Set::class.java)) {
            // If a list converter was specified, pass the value to it for direct conversion
            converter = com.beust.jcommander.JCommander.Companion.tryInstantiateConverter<T?>(optionName, annotation.listConverter())
        }
        if ((type.isAssignableFrom(List::class.java) || type.isAssignableFrom(Set::class.java)) && converter == null) {
            // No list converter: use the single value converter and pass each parsed value to it individually
            val splitter: IParameterSplitter? = com.beust.jcommander.JCommander.Companion.tryInstantiateConverter<T?>(null, annotation.splitter())
            converter = DefaultListConverter(splitter, {
            value1 -> val genericType: Type? = parameterized.findFieldGenericType()
            convertValue(parameterized, if (genericType is Class) genericType else String::class.java, null, value1)
            })
        }
        
        if (converter == null) {
            converter = com.beust.jcommander.JCommander.Companion.tryInstantiateConverter<T?>(optionName, annotation.converter())
        }
        if (converter == null) {
            converter = findConverterInstance(annotation, type, optionName)
        }
        if (converter == null && type.isEnum()) {
            converter = EnumConverter(optionName, type)
        }
        if (converter == null) {
            converter = StringConverter()
        }
        return converter.convert(value)
    }
    
     /**
 * Add a command object.
 */
    fun addCommand(name: String?, `object`: Object) {
        addCommand(name, `object`, *arrayOfNulls<String>(0))
    }
    
    fun addCommand(`object`: Object) {
        val p: Parameters? = `object`.getClass().getAnnotation(Parameters::class.java)
        if (p != null && p.commandNames().length > 0) {
            for (commandName: String? in p.commandNames()) {
                addCommand(commandName, `object`)
            }
        } else {
            throw ParameterException(("Trying to add command " + `object`.getClass().getName()
            + " without specifying its names in @Parameters"))
        }
    }
    
     /**
 * Add a command object and its aliases.
 */
    fun addCommand(name: String?, `object`: Object, vararg aliases: String?) {
        val jc: com.beust.jcommander.JCommander = com.beust.jcommander.JCommander(options)
        jc.addObject(`object`)
        jc.createDescriptions()
        jc.setProgramName(name, *aliases)
        val progName: com.beust.jcommander.JCommander.ProgramName? = jc.programName
        commands.put(progName, jc)
        
            /*
    * Register aliases
    */
        //register command name as an alias of itself for reverse lookup
        //Note: Name clash check is intentionally omitted to resemble the
        //     original behaviour of clashing commands.
        //     Aliases are, however, are strictly checked for name clashes.
        aliasMap.put(StringKey(name), progName)
        for (a: String? in aliases) {
            val alias: IKey = StringKey(a)
                        //omit pointless aliases to avoid name clash exception
            if (!alias.equals(name)) {
                val mappedName: com.beust.jcommander.JCommander.ProgramName? = aliasMap.get(alias)
                if (mappedName != null && !mappedName.equals(progName)) {
                    throw ParameterException(("Cannot set alias " + alias
                    + " for " + name
                    + " command because it has already been defined for "
                    + mappedName.name + " command"))
                }
                aliasMap.put(alias, progName)
            }
        }
    }
    
    fun getCommands(): Map<String?, com.beust.jcommander.JCommander?>? {
        val res: Map<String?, com.beust.jcommander.JCommander?>? = Maps.newLinkedHashMap()
        
        commands.forEach({key, value -> res.put(key.name, value)})
        return res
    }
    
    fun getRawCommands(): Map<com.beust.jcommander.JCommander.ProgramName?, com.beust.jcommander.JCommander?>? {
        return LinkedHashMap(commands)
    }
    
    fun getParsedCommand(): String? {
        return parsedCommand
    }
    
     /**
 * The name of the command or the alias in the form it was
 * passed to the command line. `null` if no
 * command or alias was specified.
 * 
 * @return Name of command or alias passed to command line. If none passed: `null`.
 */
    fun getParsedAlias(): String? {
        return parsedAlias
    }
    
     /**
 * @return n spaces
 */
    private fun s(count: Int): String? {
        return " ".repeat(count)
    }
    
     /**
 * @return the objects that JCommander will fill with the result of
 * parsing the command line.
 */
    fun getObjects(): List<Object?>? {
        return objects
    }
    
    private fun findParameterDescription(arg: String?): ParameterDescription? {
        return FuzzyMap.findInMap(descriptions, StringKey(arg), 
        options.caseSensitiveOptions, options.allowAbbreviatedOptions)
    }
    
    private fun findCommand(name: com.beust.jcommander.JCommander.ProgramName?): com.beust.jcommander.JCommander? {
        return FuzzyMap.findInMap(commands, name, 
        options.caseSensitiveOptions, options.allowAbbreviatedOptions)
    }
    
    private fun findProgramName(name: String?): com.beust.jcommander.JCommander.ProgramName? {
        return FuzzyMap.findInMap(aliasMap, StringKey(name), 
        options.caseSensitiveOptions, options.allowAbbreviatedOptions)
    }
    
     /*
    * Reverse lookup JCommand object by command's name or its alias
    */
    fun findCommandByAlias(commandOrAlias: String?): com.beust.jcommander.JCommander? {
        val progName: com.beust.jcommander.JCommander.ProgramName? = findProgramName(commandOrAlias)
        if (progName == null) {
            return null
        }
        val jc: com.beust.jcommander.JCommander? = findCommand(progName)
        checkNotNull(jc) {"There appears to be inconsistency in the internal command database. " + 
        " This is likely a bug. Please report."} 
        return jc
    }
    
     /**
 * Encapsulation of either a main application or an individual command.
 */
    class ProgramName internal constructor(name: String?, aliases: List<String?>?) : IKey {
        private val name: String?
        private val aliases: List<String?>?
        
        init {
            /*~~gpnebl~~*/this.name = name
            /*~~pvrupm~~*/this.aliases = aliases
        }
        
        @Override fun getName(): String? {
            return name
        }
        
        fun getDisplayName(): String? {
            val sb: StringBuilder = StringBuilder()
            sb.append(name)
            if (!aliases.isEmpty()) {
                sb.append('(')
                val aliasesIt: Iterator<String?>? = aliases.iterator()
                while (aliasesIt.hasNext()) {
                    sb.append(aliasesIt.next())
                    if (aliasesIt.hasNext()) {
                        sb.append(',')
                    }
                }
                sb.append(')')
            }
            return sb.toString()
        }
        
        @Override fun hashCode(): Int {
            return Objects.hashCode(name)
        }
        
        @Override fun equals(obj: Object?): Boolean {
            return /*~~xkhdoj~~*/this === obj || obj is com.beust.jcommander.JCommander.ProgramName && Objects.equals(name, obj.name)
        }
        
         /*
         * Important: ProgramName#toString() is used by longestName(Collection) function
         * to format usage output.
         */
        @Override fun toString(): String? {
            return getDisplayName()
            
        }
    }
    
    fun setVerbose(verbose: Int) {
        options.verbose = verbose
    }
    
    fun setCaseSensitiveOptions(b: Boolean) {
        options.caseSensitiveOptions = b
    }
    
    fun setAllowAbbreviatedOptions(b: Boolean) {
        options.allowAbbreviatedOptions = b
    }
    
    fun setAcceptUnknownOptions(b: Boolean) {
        options.acceptUnknownOptions = b
    }
    
    fun getUnknownOptions(): List<String?>? {
        return unknownArgs
    }
    
    fun setAllowParameterOverwriting(b: Boolean) {
        options.allowParameterOverwriting = b
    }
    
    fun isParameterOverwritingAllowed(): Boolean {
        return options.allowParameterOverwriting
    }
    
     /**
 * Sets the charset used to expand `@files`.
 * @param charset the charset
 */
    fun setAtFileCharset(charset: Charset?) {
        options.atFileCharset = charset
    }
    
    companion object {
        val DEBUG_PROPERTY: String = "jcommander.debug"
        
        private fun pluralize(quantity: Int, singular: String?, plural: String?): String? {
            return if (quantity == 1) singular else plural
        }
        
         /**
 * Remove spaces at both ends and handle double quotes.
 */
        private fun trim(string: String): String? {
            var result: String? = string.trim()
            if (result.startsWith("\"") && result.endsWith("\"") && result.length() > 1) {
                result = result.substring(1, result.length() - 1)
            }
            return result
        }
        
        private fun subArray(args: kotlin.Array<String?>, index: Int): kotlin.Array<String?>? {
            return Arrays.copyOfRange(args, index, args.size)
        }
        
        fun newBuilder(): com.beust.jcommander.JCommander.Builder {
            return com.beust.jcommander.JCommander.Builder()
        }
        
        private fun <T>tryInstantiateConverter(optionName: String?, converterClass: Class<T?>?): T? {
            if (converterClass === NoConverter::class.java || converterClass == null) {
                return null
            }
            try {
                return com.beust.jcommander.JCommander.Companion.instantiateConverter<T?>(optionName, converterClass)
            }catch (ignore: InstantiationException) {
                return null
            }catch (ignore: IllegalAccessException) {
                return null
            }catch (ignore: InvocationTargetException) {
                return null
            }
        }
        
        @Throws(InstantiationException::class, IllegalAccessException::class, InvocationTargetException::class) private fun <T>instantiateConverter(optionName: String?, converterClass: Class<out T?>): T? {
            var ctor: Constructor<T?>? = null
            var stringCtor: Constructor<T?>? = null
            for (c: Constructor<T?> in converterClass.getDeclaredConstructors() as kotlin.Array<Constructor<T?>?>?) {
                c.setAccessible(true)
                val types: kotlin.Array<Class<*>?>? = c.getParameterTypes()
                if (types.size == 1 && types.get(0).equals(String::class.java)) {
                    stringCtor = c
                } else if (types.size == 0) {
                    ctor = c
                }
            }
            
            return if (stringCtor != null) 
            stringCtor.newInstance(optionName)
            else 
            if (ctor != null) 
            ctor.newInstance()
            else 
            null
        }
        
    }}
