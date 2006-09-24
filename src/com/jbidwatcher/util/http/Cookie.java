package com.jbidwatcher.util.http;
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

/*!@class Cookie
 * @brief A single HTTP cookie, kept by the CookieJar.
 */
public class Cookie {
  private String _key;
  private String _value;
  private String _wholeCookie;

  public Cookie(String newCookie) {
    setCookie(newCookie);
  }

  public String getCookieKey(String inCookie) {
    int idxEquals = inCookie.indexOf('=');

    if(idxEquals == -1) {
      return null;
    }

    return(inCookie.substring(0,idxEquals));
  }

  public String toString() { return _wholeCookie; }

  public String getKey() { return _key; }

  public String getValue() { return _value; }

  public final void setCookie(String newCookie) {
    int idxEquals = newCookie.indexOf('=');

    _key = newCookie.substring(0,idxEquals);
    _value = newCookie.substring(idxEquals+1, newCookie.indexOf(';'));
    _wholeCookie = newCookie;
  }

  public boolean sameKey(String test_key) {
    return(_key.equals(test_key.substring(0,test_key.indexOf('='))));
  }
}
