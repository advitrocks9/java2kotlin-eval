package com.beust.jcommander.internal

import java.lang.annotation.ElementType.FIELD
import java.lang.annotation.ElementType.PARAMETER
import java.lang.annotation.Retention
import java.lang.annotation.Target
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target([FIELD, PARAMETER]) 
annotation class Nullable 
