/* Copyright Alan Gutierrez 2006 */
package com.goodworkalan.dovetail.stripes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface DovetailBinding
{
    public String value(); 
    
    public int priority() default 0;
}

/* vim: set et sw=4 ts=4 ai tw=78 nowrap: */