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

import java.util.EnumSet
 /**
 * A unix-style usage formatter. This works by overriding and modifying the output of
 * [.appendAllParametersDetails] which is inherited from
 * [DefaultUsageFormatter].
 */
class UnixStyleUsageFormatter (commander: JCommander?) : DefaultUsageFormatter(commander) {

     /**
 * Appends the details of all parameters in the given order to the argument string builder, indenting every
 * line with indentCount-many indent.
 * 
 * @param out the builder to append to
 * @param indentCount the amount of indentation to apply
 * @param indent the indentation
 * @param sortedParameters the parameters to append to the builder
 */
    fun appendAllParametersDetails(out: StringBuilder, indentCount: Int, indent: String, 
    sortedParameters: List<ParameterDescription?>) {
        if (sortedParameters.size() > 0) {
            out.append(indent).append("  Options:\n")
        }
        
                // Calculate prefix indent
        var prefixIndent: Int = 0
        
        for (pd: ParameterDescription in sortedParameters) {
            val parameter: WrappedParameter? = pd.getParameter()
            val prefix: String = (if (parameter.required()) "* " else "  ") + pd.getNames()
            
            if (prefix.length() > prefixIndent) {
                prefixIndent = prefix.length()
            }
        }
        
                // Append parameters
        for (pd: ParameterDescription in sortedParameters) {
            val parameter: WrappedParameter? = pd.getParameter()
            
            val prefix: String = (if (parameter.required()) "* " else "  ") + pd.getNames()
            out.append(indent)
            .append("  ")
            .append(prefix)
            .append(s(prefixIndent - prefix.length()))
            .append(" ")
            val initialLinePrefixLength: Int = indent.length() + prefixIndent + 3
            
                        // Generate description
            var description: String? = pd.getDescription()
            val def: Object? = pd.getDefaultValueDescription()
            
            if (pd.isDynamicParameter()) {
                val syntax: String = "(syntax: " + parameter.names().get(0) + "key" + parameter.getAssignment() + "value)"
                description += (if (description.isEmpty()) "" else " ") + syntax
            }
            
            if (def != null && !pd.isHelp()) {
                val displayedDef: String? = if (Strings.isStringEmpty(def.toString())) "<empty string>" else def.toString()
                val defaultText: String = "(default: " + (if (parameter.password()) "********" else displayedDef) + ")"
                description += (if (description.isEmpty()) "" else " ") + defaultText
            }
            val type: Class<*>? = pd.getParameterized().getType()
            
            if (type.isEnum()) {
                @SuppressWarnings("unchecked") val valueList: String? = EnumSet.allOf(type as Class<out Enum?>).toString()
                
                                // Prevent duplicate values list, since it is set as 'Options: [values]' if the description
                // of an enum field is empty in ParameterDescription#init(..)
                if (!description.contains("Options: " + valueList)) {
                    val possibleValues: String = "(values: " + valueList + ")"
                    description += (if (description.isEmpty()) "" else " ") + possibleValues
                }
            }
            
                        // Append description
            // The magic value 3 is the number of spaces between the name of the option and its description
            // in DefaultUsageFormatter#appendCommands(..)
            wrapDescription(out, indentCount + prefixIndent - 3, initialLinePrefixLength, description)
            out.append('\n')
        }
    }
}
