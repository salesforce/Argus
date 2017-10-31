package com.salesforce.dva.argus.entity;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * This is used for annotating fields within entities that should be treated as metadata.
 * 
 * @author	Bhinav Sura (bhinav.sura@salesforce.com)
 *
 */
@Target({ FIELD, PARAMETER, METHOD })
@Retention(RUNTIME)
public @interface Metadata {}
