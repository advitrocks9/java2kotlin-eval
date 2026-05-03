package com.beust.jcommander

import com.beust.jcommander.internal.Maps
 /**
 * Helper class to perform fuzzy key look ups: looking up case insensitive or
 * abbreviated keys.
 */
object FuzzyMap {
    fun <V>findInMap(map: Map<out com.beust.jcommander.FuzzyMap.IKey?, V?>, name: com.beust.jcommander.FuzzyMap.IKey, 
    caseSensitive: Boolean, allowAbbreviations: Boolean): V? {
        if (allowAbbreviations) {
            return com.beust.jcommander.FuzzyMap.findAbbreviatedValue<V?>(map, name, caseSensitive)
        } else {
            if (caseSensitive) {
                return map.get(name)
            } else {
                for (c: com.beust.jcommander.FuzzyMap.IKey in map.keySet()) {
                    if (c.getName().equalsIgnoreCase(name.getName())) {
                        return map.get(c)
                    }
                }
            }
        }
        return null
    }
    
    private fun <V>findAbbreviatedValue(map: Map<out com.beust.jcommander.FuzzyMap.IKey?, V?>, name: com.beust.jcommander.FuzzyMap.IKey, 
    caseSensitive: Boolean): V? {
        val string: String? = name.getName()
        val results: Map<String?, V?>? = Maps.newHashMap()
        for (c: com.beust.jcommander.FuzzyMap.IKey in map.keySet()) {
            val n: String? = c.getName()
            val match: Boolean = (caseSensitive && n.startsWith(string))
            || ((!caseSensitive) && n.toLowerCase().startsWith(string.toLowerCase()))
            if (match) {
                results.put(n, map.get(c))
            }
        }
        
        val result: V?
        if (results.size() > 1) {
            throw ParameterException(("Ambiguous option: " + name
            + " matches " + results.keySet()))
        } else if (results.size() === 1) {
            result = results.values().iterator().next()
        } else {
            result = null
        }
        
        return result
    }
    
    
    internal interface IKey {
        fun getName(): String?
    }
    
}
