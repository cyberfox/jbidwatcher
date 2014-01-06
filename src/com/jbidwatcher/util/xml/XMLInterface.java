package com.jbidwatcher.util.xml;

import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: Aug 6, 2009
 * Time: 1:34:43 AM
 *
 * A high level interface view of the XML Elements.
 */
public interface XMLInterface {
  Iterator<XMLInterface> getChildren();

  String getContents();

  String getProperty(String key);

  String getProperty(String key, String defaultValue);

  StringBuffer toStringBuffer();

  StringBuffer toStringBuffer(StringBuffer wholeXML, int depth);

  String getTagName();

  void setProperty(String formValue, String val);

  String toString(int depth);
}
