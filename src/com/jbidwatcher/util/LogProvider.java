package com.jbidwatcher.util;

/**
 * Interface for log providers.
 *
 * User: mrs
 * Date: Aug 7, 2010
 * Time: 8:27:04 PM
 * Abstracts log providers so that one can be passed into the log viewer,
 * and a refresh trivially called without having to know too much about
 * which kind of log a class provides.
 */
public interface LogProvider {
  public StringBuffer getLog();
}
