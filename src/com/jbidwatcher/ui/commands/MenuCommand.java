package com.jbidwatcher.ui.commands;

/**
 * An annotation to menu and user-generated commands for automatic processing.
 * User: mrs
 * Date: 5/19/12
 * Time: 12:22 AM
 */

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MenuCommand {
  int params() default 0;
  String action() default "";
}
