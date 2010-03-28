package com.jbidwatcher.util.xml;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/**
 * @file   XMLSerialize.java
 * @author Morgan Schweers <cyberfox@jbidwatcher.com>
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
  XMLInterface toXML();

  /** 
   * @brief Step through all the important variables that had been
   * serialized out, and import them from XMLElements.
   *
   * @param inXML - The XML element to start the deserialization process from.
   */
  void fromXML(XMLInterface inXML);
}
