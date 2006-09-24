package com.jbidwatcher.xml;
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

/**
 * @file   XMLSerialize.java
 * @author Morgan Schweers <cyberfox@users.sourceforge.net>
 * @note   Library GPL'ed.
 * @date   Thu Oct 10 01:30:40 2002
 * 
 * @brief  Contains the interface/marker class for XML serializable classes.
 * 
 */

/*!@class XMLSerialize
 *
 * @brief Requires that any classes that want to be marked as XML
 * Serializable will implement the core toXML and fromXML functions.
 *
 */

public interface XMLSerialize {

  /** 
   * @brief Step through all the important variables in a serializable
   * class, and export them as XMLElements.
   * 
   * @return - An XMLElement that is the 'root object' for the XML
   * tree built by serializing this class, and all subclasses.
   */
  XMLElement toXML();

  /** 
   * @brief Step through all the important variables that had been
   * serialized out, and import them from XMLElements.
   *
   */
  void fromXML(XMLElement inXML);
}
