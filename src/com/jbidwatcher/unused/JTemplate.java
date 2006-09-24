package com.jbidwatcher.unused;
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

/*!@class JTemplate
 *
 * @brief My own template code, to aid in doing invoicing, primarily.
 *
 */

public class JTemplate {
  protected JParser _parser;

  JTemplate(JParser jp) {
    _parser = jp;
  }

  protected int findUnescaped(String txt, char ch, int start) {
    int jump = start;
    int next = -2;
    while(next == -2) {
      next = txt.indexOf(ch, jump);
      if(next != 0 && next != -1) {
        if(txt.charAt(next-1) == '\\') {
          jump = next+1;
          next = -2;
        }
      }
    }

    return next;
  }

  public static String replace(String inSource, String search, String replacement) {
    String result = inSource;
    int srch_index = result.indexOf(search);

    while(srch_index != -1) {
      result = result.substring(0,srch_index) +
        replacement +
        result.substring(srch_index + search.length());

      srch_index = result.indexOf(search, srch_index + replacement.length());
    }

    return result;
  }

  public String expand(String txt) {
    String result = txt;
    int start = findUnescaped(result, '<', 0);

    while(start != -1) {
      result = expand2(result, start);
      start = findUnescaped(result, '<', 0);
    }

    result = replace(result, "\\<", "<");
    result = replace(result, "\\>", ">");

    return result;
  }

  private String expand2(String txt, int start) {
    String result = txt;
    int end, maybe;
    do {
      end = findUnescaped(result, '>', start+1);
      maybe=findUnescaped(result, '<', start+1);

      if(maybe != -1 && end != -1) {
        if(maybe < end) {
          result = expand2(result, maybe);
        }
      }
    } while(maybe != -1 && end != -1 && maybe < end);

    return result.substring(0, start) +
      _parser.evaluate(result.substring(start+1,end)) +
      result.substring(end+1);
  }
}
