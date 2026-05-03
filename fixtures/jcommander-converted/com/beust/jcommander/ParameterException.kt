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
 * The main exception that JCommand will throw when something goes wrong while
 * parsing parameters.
 * 
 * @author Cedric Beust &lt;cedric@beust.com&gt;
 */
@SuppressWarnings("serial") 
class ParameterException : RuntimeException {
    constructor(t: Throwable?) : super(t)
    
    constructor(string: String?) : super(string)
    
    constructor(string: String?, t: Throwable?) : super(string, t)
    
    private var jc: JCommander? = null
    
    fun setJCommander(jc: JCommander?) {
        /*~~zwjeqx~~*/this.jc = jc
    }
    
    fun getJCommander(): JCommander? {
        return jc
    }
    
    fun usage() {
        if (jc != null) {
            jc.usage()
        }
    }
}
