package com.jbidwatcher.util;

import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
* User: Morgan
* Date: Sep 30, 2007
* Time: 1:44:08 AM
* To change this template use File | Settings | File Templates.
*/
public class Record extends HashMap<String, String> {
  public String dump() {
    StringBuffer sb = dumpRecord(0);
    sb.append("\n");
    return sb.toString();
  }

  private StringBuffer dumpRecord(int offset) {
    StringBuffer sb = new StringBuffer();
    sb.append("{");
    for (Object o : keySet()) {
      Object value = get(o);
      if (value != null) {
        sb.append("\"").append(o.toString()).append("\" => \"").append(value.toString().replace("\"", "\\\"")).append("\"");
      }
    }
    sb.append("}");
    return sb;
  }
}
