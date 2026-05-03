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

 /**
 * A formatter for help messages.
 */
interface IUsageFormatter {

     /**
 * Display the usage for this command.
 */
    fun usage(commandName: String?)
    
     /**
 * Store the help for the command in the passed string builder.
 */
    fun usage(commandName: String?, out: StringBuilder?)
    
     /**
 * Store the help in the passed string builder.
 */
    fun usage(out: StringBuilder?)
    
     /**
 * Store the help for the command in the passed string builder, indenting every line with "indent".
 */
    fun usage(commandName: String?, out: StringBuilder?, indent: String?)
    
     /**
 * Stores the help in the passed string builder, with the argument indentation.
 */
    fun usage(out: StringBuilder?, indent: String?)
    
     /**
 * @return the description of the argument command
 */
    fun getCommandDescription(commandName: String?): String?
}
