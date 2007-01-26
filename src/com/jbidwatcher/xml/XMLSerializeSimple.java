package com.jbidwatcher.xml;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.util.Iterator;

public abstract class XMLSerializeSimple implements XMLSerialize {
  protected abstract void handleTag(int i, XMLElement curElement);
  protected abstract String[] getTags();

  public void fromXML(XMLElement inXML) {
    String[] infoTags = getTags();
    Iterator<XMLElement> infoFields = inXML.getChildren();

    while(infoFields.hasNext()) {
      XMLElement fieldStep = infoFields.next();
      String curField = fieldStep.getTagName();

      for(int i=0; i<infoTags.length; i++) {
        if(infoTags[i].equals(curField)) {

          handleTag(i, fieldStep);
          break;
        }
      }
    }
  }

  public abstract XMLElement toXML();
}
