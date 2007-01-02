package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
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
