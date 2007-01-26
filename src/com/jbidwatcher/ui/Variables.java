package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.stevesoft.pat.*;
import java.util.HashMap;

// The variables are name value pairs stored in a hastable.
// Whenever the Regex matches on one of the names, this rule
// adds the appropriate value to the StringBuffer.
public class Variables extends ReplaceRule {
  private HashMap<String, String> varStorage;
  public Variables(HashMap<String, String> h) { varStorage = new HashMap<String, String>(h); }
  public void apply(StringBufferLike sb,RegRes rr) {
    String o=varStorage.get(rr.stringMatched());
    if(o == null)
      sb.append(rr.stringMatched());
    else
      sb.append(o);
  }
  // Needed if we are to clone this rule.  This
  // class is a singly linked list, the super class's
  // method clone() makes sure the whole list is
  // cloned if it just knows how to clone this one
  // extension.
  public Object clone1() { return new Variables(varStorage); }
}
