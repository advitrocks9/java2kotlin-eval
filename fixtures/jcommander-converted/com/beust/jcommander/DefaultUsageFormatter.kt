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

import com.beust.jcommander.internal.Lists
import java.util.ResourceBundle
 /**
 * The default usage formatter.
 */
class DefaultUsageFormatter (commander: JCommander?) : IUsageFormatter {

    private val commander: JCommander?
    
    init {
        /*~~fpnuwj~~*/this.commander = commander
    }
    
     /**
 * Prints the usage to [JCommander.getConsole] on the underlying commander instance.
 */
    fun usage(commandName: String?) {
        val sb: StringBuilder = StringBuilder()
        usage(commandName, sb)
        commander.getConsole().println(sb)
    }
    
     /**
 * Store the usage for the argument command in the argument string builder.
 */
    fun usage(commandName: String?, out: StringBuilder) {
        usage(commandName, out, "")
    }
    
     /**
 * Store the usage in the argument string builder.
 */
    fun usage(out: StringBuilder) {
        usage(out, "")
    }
    
     /**
 * Store the usage for the command in the argument string builder, indenting every line with the
 * value of indent.
 */
    fun usage(commandName: String?, out: StringBuilder, indent: String?) {
        val description: String? = getCommandDescription(commandName)
        val jc: JCommander? = commander.findCommandByAlias(commandName)
        
        if (description != null) {
            out.append(indent).append(description)
            out.append('\n')
        }
        jc.getUsageFormatter().usage(out, indent)
    }
    
     /**
 * Stores the usage in the argument string builder, with the argument indentation. This works by appending
 * each portion of the help in the following order. Their outputs can be modified by overriding them in a
 * subclass of this class.
 * 
 * 
 *  * Main line - [.appendMainLine]
 *  * Parameters - [.appendAllParametersDetails]
 *  * Commands - [.appendCommands]
 * 
 */
    fun usage(out: StringBuilder, indent: String) {
        if (commander.getDescriptions() == null) {
            commander.createDescriptions()
        }
        val hasCommands: Boolean = !commander.getCommands().isEmpty()
        val hasOptions: Boolean = !commander.getDescriptions().isEmpty()
        
                // Indentation constants
        val descriptionIndent: Int = 6
        val indentCount: Int = indent.length() + descriptionIndent
        
                // Append first line (aka main line) of the usage
        appendMainLine(out, hasOptions, hasCommands, indentCount, indent)
        
                // Align the descriptions at the "longestName" column
        var longestName: Int = 0
        val sortedParameters: List<ParameterDescription?>? = Lists.newArrayList()
        
        for (pd: ParameterDescription in commander.getFields().values()) {
            if (!pd.getParameter().hidden()) {
                sortedParameters.add(pd)
                                // + to have an extra space between the name and the description
                val length: Int = pd.getNames().length() + 2
                
                if (length > longestName) {
                    longestName = length
                }
            }
        }
        
                // Sort the options
        sortedParameters.sort(commander.getParameterDescriptionComparator())
        
                // Append all the parameter names and descriptions
        appendAllParametersDetails(out, indentCount, indent, sortedParameters)
        
                // Append commands if they were specified
        if (hasCommands) {
            appendCommands(out, indentCount, descriptionIndent, indent)
        }
    }
    
     /**
 * Appends the main line segment of the usage to the argument string builder, indenting every
 * line with indentCount-many indent.
 * 
 * @param out the builder to append to
 * @param hasOptions if the options section should be appended
 * @param hasCommands if the comments section should be appended
 * @param indentCount the amount of indentation to apply
 * @param indent the indentation
 */
    fun appendMainLine(out: StringBuilder, hasOptions: Boolean, hasCommands: Boolean, indentCount: Int, 
    indent: String?) {
        val programName: String? = if (commander.getProgramDisplayName() != null) 
        commander.getProgramDisplayName()
        else 
        "<main class>"
        val mainLine: StringBuilder = StringBuilder()
        mainLine.append(indent).append("Usage: ").append(programName)
        
        if (hasOptions) {
            mainLine.append(" [options]")
        }
        
        if (hasCommands) {
            mainLine.append(indent).append(" [command] [command options]")
        }
        
        if (commander.getMainParameter() != null && commander.getMainParameter().getDescription() != null) {
            mainLine.append(" ").append(commander.getMainParameter().getDescription().getDescription())
        }
        wrapDescription(out, indentCount, mainLine)
        out.append('\n')
    }
    
     /**
 * Appends the details of all parameters in the given order to the argument string builder, indenting every
 * line with indentCount-many indent.
 * 
 * @param out the builder to append to
 * @param indentCount the amount of indentation to apply
 * @param indent the indentation
 * @param sortedParameters the parameters to append to the builder
 */
    fun appendAllParametersDetails(out: StringBuilder, indentCount: Int, indent: String?, 
    sortedParameters: List<ParameterDescription?>) {
        if (sortedParameters.size() > 0) {
            out.append('\n')
            out.append(indent).append("  Options:\n")
        }
        
        for (pd: ParameterDescription in sortedParameters) {
            val parameter: WrappedParameter? = pd.getParameter()
            val description: String? = pd.getDescription()
            val hasDescription: Boolean = !description.isEmpty()
            
                        // First line, command name
            out.append(indent)
            .append("  ")
            .append(if (parameter.required()) "* " else "  ")
            .append(pd.getNames())
            .append(if (parameter.placeholder().isBlank()) "" else " " + parameter.placeholder())
            .append('\n')
            
            if (hasDescription) {
                wrapDescription(out, indentCount, com.beust.jcommander.DefaultUsageFormatter.Companion.s(indentCount) + description)
            }
            
            val category: String? = pd.getCategory()
            if (!category.isEmpty()) {
                val categoryType: String = "Category: " + category
                
                if (hasDescription) {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.newLineAndIndent(indentCount))
                } else {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.s(indentCount))
                }
                out.append(categoryType)
            }
            
            val def: Object? = pd.getDefaultValueDescription()
            
            if (pd.isDynamicParameter()) {
                val syntax: String = "Syntax: " + parameter.names().get(0) + "key" + parameter.getAssignment() + "value"
                
                if (hasDescription) {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.newLineAndIndent(indentCount))
                } else {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.s(indentCount))
                }
                out.append(syntax)
            }
            
            if (def != null && !pd.isHelp()) {
                val displayedDef: String? = if (Strings.isStringEmpty(def.toString())) "<empty string>" else def.toString()
                val defaultText: String = "Default: " + (if (parameter.password()) "********" else displayedDef)
                
                if (hasDescription) {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.newLineAndIndent(indentCount))
                } else {
                    out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.s(indentCount))
                }
                out.append(defaultText)
            }
            val type: Class<*>? = pd.getParameterized().getType()
            
            if (type.isEnum()) {
                val valueList: String? = EnumSet.allOf(type as Class<out Enum?>).toString()
                val possibleValues: String = "Possible Values: " + valueList
                
                                // Prevent duplicate values list, since it is set as 'Options: [values]' if the description
                // of an enum field is empty in ParameterDescription#init(..)
                if (!description.contains("Options: " + valueList)) {
                    if (hasDescription) {
                        out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.newLineAndIndent(indentCount))
                    } else {
                        out.append(com.beust.jcommander.DefaultUsageFormatter.Companion.s(indentCount))
                    }
                    out.append(possibleValues)
                }
            }
            out.append('\n')
        }
    }
    
     /**
 * Appends the details of all commands to the argument string builder, indenting every line with
 * indentCount-many indent. The commands are obtained from calling
 * [JCommander.getRawCommands] and the commands are resolved using
 * [JCommander.findCommandByAlias] on the underlying commander instance.
 * 
 * @param out the builder to append to
 * @param indentCount the amount of indentation to apply
 * @param descriptionIndent the indentation for the description
 * @param indent the indentation
 */
    fun appendCommands(out: StringBuilder, indentCount: Int, descriptionIndent: Int, indent: String?) {
        var hasOnlyHiddenCommands: Boolean = true
        for (commands: Map.Entry<JCommander.ProgramName?, JCommander?> in commander.getRawCommands().entrySet()) {
            val arg: Object? = commands.getValue().getObjects().getFirst()
            val p: Parameters? = arg.getClass().getAnnotation(Parameters::class.java)
            
            if (p == null || !p.hidden()) hasOnlyHiddenCommands = false
        }
        
        if (hasOnlyHiddenCommands) return 
        
        out.append('\n')
        out.append(indent.toString() + "  Commands:\n")
        
        var firstCommand: Boolean = true
                // The magic value 3 is the number of spaces between the name of the option and its description
        for (commands: Map.Entry<JCommander.ProgramName?, JCommander?> in commander.getRawCommands().entrySet()) {
            val arg: Object? = commands.getValue().getObjects().getFirst()
            val p: Parameters? = arg.getClass().getAnnotation(Parameters::class.java)
            
            if (p == null || !p.hidden()) {
                if (!firstCommand) {
                    out.append('\n')
                } else {
                    firstCommand = false
                }
                val progName: JCommander.ProgramName? = commands.getKey()
                val dispName: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? = progName.getDisplayName()
                val commandDescription: String? = Optional.ofNullable(getCommandDescription(progName.getName()))
                .map({desc -> com.beust.jcommander.DefaultUsageFormatter.Companion.s(6) + desc})
                .orElse("")
                val description: String = indent + com.beust.jcommander.DefaultUsageFormatter.Companion.s(4) + dispName + commandDescription
                wrapDescription(out, indentCount + descriptionIndent, description)
                out.append('\n')
                
                                // Options for this command
                val jc: JCommander? = commander.findCommandByAlias(progName.getName())
                jc.getUsageFormatter().usage(out, indent + com.beust.jcommander.DefaultUsageFormatter.Companion.s(6))
            }
        }
    }
    
     /**
 * Returns the description of the command corresponding to the argument command name. The commands are resolved
 * by calling [JCommander.findCommandByAlias], and the default resource bundle used from
 * [JCommander.getBundle] on the underlying commander instance.
 * 
 * @param commandName the name of the command to get the description for
 * @return the description of the command.
 */
    fun getCommandDescription(commandName: String?): String? {
        val jc: JCommander? = commander.findCommandByAlias(commandName)
        
        if (jc == null) {
            throw ParameterException("Asking description for unknown command: " + commandName)
        }
        val arg: Object? = jc.getObjects().getFirst()
        val p: Parameters? = arg.getClass().getAnnotation(Parameters::class.java)
        val bundle: java.util.ResourceBundle?
        var result: String? = null
        
        if (p != null) {
            result = p.commandDescription()
            val bundleName: String? = p.resourceBundle()
            
            if (!bundleName.isEmpty()) {
                bundle = ResourceBundle.getBundle(bundleName, Locale.getDefault(), arg.getClass().getClassLoader())
            } else {
                bundle = commander.getBundle()
            }
            
            if (bundle != null) {
                val descriptionKey: String? = p.commandDescriptionKey()
                
                if (!descriptionKey.isEmpty()) {
                    result = com.beust.jcommander.DefaultUsageFormatter.Companion.getI18nString(bundle, descriptionKey, p.commandDescription())
                }
            }
        }
        return result
    }
    
     /**
 * Wrap a potentially long line to the value obtained by calling [JCommander.getColumnSize] on the
 * underlying commander instance.
 * 
 * @param out               the output
 * @param indent            the indentation in spaces for lines after the first line.
 * @param currentLineIndent the length of the indentation of the initial line
 * @param description       the text to wrap. No extra spaces are inserted before `description`. If the first line needs to be indented prepend the
 * correct number of spaces to `description`.
 */
    fun wrapDescription(out: StringBuilder, indent: Int, currentLineIndent: Int, description: CharSequence) {
        val max: Int = commander.getColumnSize()
        val words: kotlin.Array<String?>? = description.toString().split(" ")
        var current: Int = currentLineIndent
        
        for (i in words.indices) {
            val word: String? = words.get(i)
            
            if (word.length() > max || current + 1 + word.length() <= max) {
                out.append(word)
                current += word.length()
                
                if (i != words.size - 1) {
                    out.append(" ")
                    current++
                }
            } else {
                out.append('\n').append(com.beust.jcommander.DefaultUsageFormatter.Companion.s(indent)).append(word).append(" ")
                current = indent + word.length() + 1
            }
        }
    }
    
     /**
 * Wrap a potentially long line to { @link #commander#getColumnSize()}.
 * 
 * @param out         the output
 * @param indent      the indentation in spaces for lines after the first line.
 * @param description the text to wrap. No extra spaces are inserted before `description`. If the first line needs to be indented prepend the
 * correct number of spaces to `description`.
 * @see .wrapDescription
 */
    fun wrapDescription(out: StringBuilder, indent: Int, description: CharSequence) {
        wrapDescription(out, indent, 0, description)
    }
    
    companion object {
         /**
 * Returns the internationalized version of the string if available, otherwise it returns def.
 * 
 * @return the internationalized version of the string if available, otherwise it returns def
 */
        fun getI18nString(bundle: ResourceBundle?, key: String?, def: String?): String? {
            val s: String? = if (bundle != null) bundle.getString(key) else null
            return if (s != null) s else def
        }
        
         /**
 * Returns count-many spaces.
 * 
 * @return count-many spaces
 */
        fun s(count: Int): String? {
            return " ".repeat(count)
        }
        
         /**
 * Returns new line followed by indent-many spaces.
 * 
 * @return new line followed by indent-many spaces
 */
        private fun newLineAndIndent(indent: Int): String? {
            return "\n" + com.beust.jcommander.DefaultUsageFormatter.Companion.s(indent)
        }
    }}
