// Copyright (c) 2003-2014, Jodd Team (jodd.org). All Rights Reserved.

package jodd.petite.meta;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import static jodd.petite.meta.InitMethodInvocationStrategy.POST_INITIALIZE;

/**
 * Points to the Petite bean implementation. If used on an interfaces, Petite will
 * resolve specified implementation. If used on package, all interfaces marked with PetiteBean
 * will be resolved to real implementation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.PACKAGE})
public @interface PetiteInitMethod {

	/**
	 * Indicates the order of init method. Order number of first methods starts from 1.
	 * Order number of last methods starts from -1 to negatives. 0 is default and
	 * marks 'in between' methods: methods that executes after first ones and before last ones.
	 */
	int order() default 0;

	/**
	 * Defines init method invocation strategy, i.e. moment in beans lifecycle when
	 * init methods will be invoked.
	 */
	InitMethodInvocationStrategy invoke() default POST_INITIALIZE;

}