package com.jbidwatcher.util.xml;
/* This file was part of NanoXML.
 *
 * $Revision: 1.32 $
 * $Date: 2006/08/25 08:34:00 $
 * $Name:  $
 *
 * Copyright (C) 2000 Marc De Scheemaecker, All Rights Reserved.
 *
 * This software is provided 'as-is', without any express or implied warranty.
 * In no event will the authors be held liable for any damages arising from the
 * use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 *  1. The origin of this software must not be misrepresented; you must not
 *     claim that you wrote the original software. If you use this software in
 *     a product, an acknowledgment in the product documentation would be
 *     appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *     misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source distribution.
 */

/*  Un-'packaged' from the NanoXML package, so it can be easily
 *  integrated w/ JBidWatcher. -- mrs: 07-August-2000 21:24
 *  Then HEAVILY modified, to support writing XML as well as reading it.
 *  Plus reformatted, for readability and to fit with the rest of the
 *  project that it's being integrated into.
 */

import java.io.IOException;
import java.io.Reader;
import java.util.*;


/**
 * XMLElement is a representation of an XML object. The object is able to parse
 * XML code.
 * <P>
 * Note that NanoXML is not 100% XML 1.0 compliant:
 * <UL><LI>The parser is non-validating.
 *     <LI>The DTD is fully ignored, including <CODE>&lt;!ENTITY...&gt;</CODE>.
 *     <LI>There is no support for mixed content (elements containing both
 *         subelements and CDATA elements)
 *     <LI>There is no support for <CODE>&lt;![CDATA[...]]&gt;</CODE>
 * </UL>
 * <P>
 * You can opt to use a SAX compatible API, by including both
 * <CODE>nanoxml.jar</CODE> and <CODE>nanoxml-sax.jar</CODE> in your classpath
 * and setting the property <CODE>org.xml.sax.parser</CODE> to
 * <CODE>nanoxml.sax.SAXParser</CODE>
 * <P>
 * $Revision: 1.32 $<BR>
 * $Date: 2006/08/25 08:34:00 $<P>
 *
 * @see XMLParseException
 *
 * @author Marc De Scheemaecker
 *         &lt;<A HREF="mailto:Marc.DeScheemaecker@advalvas.be"
 *         >Marc.DeScheemaecker@advalvas.be</A>&gt;
 * @version 1.5
 */
@SuppressWarnings({"JavaDoc"})
public class XMLElement {
  /**
   * Major version of NanoXML.
   */
  public static final int NANOXML_MAJOR_VERSION = 1;


  /**
   * Minor version of NanoXML.
   */
  public static final int NANOXML_MINOR_VERSION = 7;


  private static boolean mRejectBadHTML = false;

  public static void rejectBadHTML(boolean rejectBadHtml) {
    mRejectBadHTML = rejectBadHtml;
  }

  /**
   * The attributes given to the object.
   */
  protected HashMap<String, String> _attributes;


  /**
   * Subobjects of the object. The subobjects are of class XMLElement
   * themselves.
   */
  protected List<XMLElement> _children;


  /**
   * The class of the object (the name indicated in the tag).
   */
  protected String _tagName;


  /**
   * Whether or not this is a clean 'empty' tag.
   */
  protected boolean _empty;


  /**
   * The #PCDATA content of the object. If there is no such content, this
   * field is null.
   */
  protected String _contents;


  /**
   * Conversion table for &amp;...; tags.
   */
  protected HashMap<String, String> _conversionTable;


  /**
   * Whether to skip leading whitespace in CDATA.
   */
  protected boolean _skipLeadingWhitespace;


  /**
   * The line number where the element starts.
   */
  protected int _lineNr;
  private static final int INITIAL_BLOCKSIZE = 20480;


  /**
   * Creates a new XML element. The following settings are used:
   * <DL><DT>Conversion table</DT>
   *     <DD>Minimal XML conversions: <CODE>&amp;amp; &amp;lt; &amp;gt;
   *         &amp;apos; &amp;quot;</CODE></DD>
   *     <DT>Skip whitespace in contents</DT>
   *     <DD><CODE>false</CODE></DD>
   * </DL>
   *
   * @see XMLElement#XMLElement(HashMap)
   * @see XMLElement#XMLElement(boolean)
   * @see XMLElement#XMLElement(HashMap,boolean)
   */
  public XMLElement() {
    this(null, false, true);
  }


  /**
   * Creates a new XML element. The following settings are used:
   * <DL><DT>Conversion table</DT>
   *     <DD><I>conversionTable</I> combined with the minimal XML
   *         conversions: <CODE>&amp;amp; &amp;lt; &amp;gt;
   *         &amp;apos; &amp;quot;</CODE></DD>
   *     <DT>Skip whitespace in contents</DT>
   *     <DD><CODE>false</CODE></DD>
   * </DL>
   *
   * @see XMLElement#XMLElement()
   * @see XMLElement#XMLElement(boolean)
   * @see XMLElement#XMLElement(HashMap,boolean)
   * @param conversionTable - Quoted string to character conversion table.
   */
  public XMLElement(HashMap<String, String> conversionTable) {
    this(conversionTable, false, true);
  }


  /**
   * Creates a new XML element. The following settings are used:
   * <DL><DT>Conversion table</DT>
   *     <DD>Minimal XML conversions: <CODE>&amp;amp; &amp;lt; &amp;gt;
   *         &amp;apos; &amp;quot;</CODE></DD>
   *     <DT>Skip whitespace in contents</DT>
   *     <DD><I>skipLeadingWhitespace</I></DD>
   * </DL>
   *
   * @see XMLElement#XMLElement()
   * @see XMLElement#XMLElement(HashMap)
   * @see XMLElement#XMLElement(HashMap,boolean)
   * @param skipLeadingWhitespace - Ignore white space that starts the tag content.
   */
  public XMLElement(boolean skipLeadingWhitespace) {
    this(null, skipLeadingWhitespace, true);
  }


  /**
   * Creates a new XML element. The following settings are used:
   * <DL><DT>Conversion table</DT>
   *     <DD><I>conversionTable</I> combined with the minimal XML
   *         conversions: <CODE>&amp;amp; &amp;lt; &amp;gt;
   *         &amp;apos; &amp;quot;</CODE></DD>
   *     <DT>Skip whitespace in contents</DT>
   *     <DD><I>skipLeadingWhitespace</I></DD>
   * </DL>
   *
   * @see XMLElement#XMLElement()
   * @see XMLElement#XMLElement(boolean)
   * @see XMLElement#XMLElement(HashMap)
   * @param conversionTable - Quoted string to character conversion table.
   * @param skipLeadingWhitespace - Ignore white space that starts the tag content.
   */
  public XMLElement(HashMap<String, String> conversionTable,
                    boolean    skipLeadingWhitespace) {
    this(conversionTable, skipLeadingWhitespace, true);
  }


  /**
   * Creates a new XML element. The following settings are used:
   * <DL><DT>Conversion table</DT>
   *     <DD><I>conversionTable</I>, eventually combined with the minimal XML
   *         conversions: <CODE>&amp;amp; &amp;lt; &amp;gt;
   *         &amp;apos; &amp;quot;</CODE>
   *         (depending on <I>fillBasicConversionTable</I>)</DD>
   *     <DT>Skip whitespace in contents</DT>
   *     <DD><I>skipLeadingWhitespace</I></DD>
   * </DL>
   * <P>
   * This constructor should <I>only</I> be called from XMLElement itself
   * to create child elements.
   *
   * @see XMLElement#XMLElement()
   * @see XMLElement#XMLElement(boolean)
   * @see XMLElement#XMLElement(HashMap)
   * @see XMLElement#XMLElement(HashMap,boolean)
   * @param conversionTable - Quoted string to character conversion table.
   * @param skipLeadingWhitespace - Ignore white space that starts the tag content.
   * @param fillBasicConversionTable - Add in the fundamental HTML quoted entities if this is true.
   */
  private XMLElement(HashMap<String, String> conversionTable,
                     boolean   skipLeadingWhitespace,
                     boolean   fillBasicConversionTable) {
    _skipLeadingWhitespace = skipLeadingWhitespace;
    _tagName = null;
    _contents = null;
    _attributes = new HashMap<String, String>(10);
    _children = new ArrayList<XMLElement>(10);
    _conversionTable = conversionTable==null?new HashMap<String, String>(10):new HashMap<String, String>(conversionTable);
    _lineNr = 0;

    if (fillBasicConversionTable)
      {
        _conversionTable.put("lt", "<");
        _conversionTable.put("gt", ">");
        _conversionTable.put("quot", "\"");
        _conversionTable.put("apos", "'");
        _conversionTable.put("amp", "&");
        _conversionTable.put("pound", "#");
      }
  }

  public XMLElement(String elementName) {
    this(null, false, true);

    _tagName = elementName;
  }

  /**
   * Returns the number of subobjects of the object.
   * @return - The number of children of this element.
   */
  public int countChildren() {
    return _children.size();
  }


  /**
   * Enumerates the attribute names.
   * @return - The list of attribute names.
   */
  public Iterator<String> getAttributes() {
    return _attributes.keySet().iterator();
  }


  /**
   * Enumerates the subobjects of the object.
   * @return - An iterator over the children of this element.
   */
  public Iterator<XMLElement> getChildren() {
    return _children.iterator();
  }


  /**
   * Returns the specifically named child.
   * @param tagName - The child tag to get.
   * @return - The element identified by the named child tag.
   */
  public XMLElement getChild(String tagName) {
    Iterator<XMLElement> step = getChildren();
    while(step.hasNext()) {
      XMLElement child = step.next();

      if(child.getTagName().equals(tagName)) {
        return child;
      }
    }
    return null;
  }


  /**
   * Returns the #PCDATA content of the object. If there is no such content,
   * <CODE>null</CODE> is returned.
   */
  public String getContents() {
    return _contents;
  }


  /**
   * Returns the line nr on which the element is found.
   */
  public int getLineNr() {
    return _lineNr;
  }


  /**
   * Returns a property of the object. The property has to be specified in
   * capital letters. If there is no such property, this method returns
   * <CODE>null</CODE>.
   */
  public String getProperty(String key) {
    return _attributes.get(key.toLowerCase());
  }


  /**
   * Returns a property of the object. The property has to be specified in
   * capital letters.
   * If the property doesn't exist, <I>defaultValue</I> is returned.
   */
  public String getProperty(String key, String defaultValue) {
    String result = _attributes.get(key.toLowerCase());

    if(result == null) result = defaultValue;

    return result;
  }


  /**
   * Sets a property of the object.
   */
  public void setProperty(String key, String newValue) {
    String lowerKey = key.toLowerCase();
    _attributes.put(lowerKey, newValue);
  }

  /**
   * Returns the class (i.e. the name indicated in the tag) of the object.
   */
  public String getTagName() {
    return _tagName;
  }

  /**
   * Identify this tag as empty.
   */
  public void setEmpty() {
    _empty = true;
  }

  /**
   * Identify this tag as a normal tag.
   */
  public void setNonEmpty() {
    _empty = false;
  }

  /**
   * Returns the class (i.e. the name indicated in the tag) of the object.
   */
  public void setTagName(String newName) {
    _tagName = newName;
  }

  /**
   * Sets the content of this XMLElement to be a particular value.  Does
   * not need to be quoted.  This cannot be done if a child has already
   * been added.
   */
  public void setContents(String newContents) {
    if(_children.size() == 0) {
      _contents = newContents;
    } else {
      throw new XMLParseException(_tagName, _lineNr, "Cannot add contents to an XML element that already has children.");
    }
  }

  /**
   * Adds another XMLElement as a child of this one.  This can't be done if
   * contents is already set.
   */
  public void addChild(XMLElement newChild) {
    if(_contents == null) {
      _children.add(newChild);
    } else {
      throw new XMLParseException(_tagName, _lineNr, "Cannot add children to an XML element that already has contents.");
    }
  }

  /**
   * Appends to a StringBuffer all the attributes of this element, in a form clean
   * enough to append into a tag to recreate the original tag.  Returns the provided
   * stringbuffer, untouched, if there are no attributes.
   */
  private StringBuffer allAttribs(StringBuffer attrList) {
    Iterator<String> attrStep = getAttributes();

    while(attrStep.hasNext()) {
      String attrName = attrStep.next();
      attrList.append(' ');
      attrList.append(attrName);
      attrList.append("=\"");
      attrList.append(encodeString(getProperty(attrName)));
      attrList.append('\"');
    }
    return attrList;
  }

  /**
   * Convert the entire XMLElement tree from this element down into a string.
   */
  public String toString() {
    return toStringBuffer().toString();
  }

  /**
   * Convert the entire XMLElement tree from this element down into a string, with
   * prepended spacing dependant on the tree depth.
   */
  public String toString(int depth) {
    return toStringBuffer(new StringBuffer(), depth).toString();
  }

  public StringBuffer toStringBuffer() {
    return toStringBuffer(new StringBuffer(), 0);
  }

  public StringBuffer toStringBuffer(StringBuffer wholeXML) {
    return toStringBuffer(wholeXML, 0);
  }

  public StringBuffer toStringBuffer(StringBuffer wholeXML, int depth) {
    prependSpaces(wholeXML, depth);
    wholeXML.append('<').append(_tagName);

    allAttribs(wholeXML);

    if (_empty) {
      wholeXML.append("/>");
    } else {
      wholeXML.append('>');
      if (_contents == null) {
        Iterator<XMLElement> xmlStep = getChildren();

        wholeXML.append('\n');
        while (xmlStep.hasNext()) {
          (xmlStep.next()).toStringBuffer(wholeXML, depth+1);
        }

        prependSpaces(wholeXML, depth);
      } else {
        wholeXML.append(encodeString(_contents));
      }

      wholeXML.append("</").append(_tagName).append('>');
    }
    wholeXML.append('\n');

    return wholeXML;
  }

  private static void prependSpaces(StringBuffer wholeXML, int depth) {
    for(int i=0; i<depth; i++) {
      wholeXML.append("  ");
    }
  }

  /**
   * Checks whether a character may be part of an identifier.
   */
  protected static boolean isIdentifierChar(char ch) {
    return (((ch >= 'A') && (ch <= 'Z')) || ((ch >= 'a') && (ch <= 'z'))
            || ((ch >= '0') && (ch <= '9')) || (".-_:".indexOf(ch) >= 0));
  }


  /**
   * Reads an XML definition from a java.io.Reader and parses it.
   *
   * @exception IOException
   *    if an error occured while reading the input
   * @exception XMLParseException
   *    if an error occured while parsing the read data
   */
  public void parseFromReader(Reader reader) throws IOException, XMLParseException {
    parseFromReader(reader, 1);
  }


  /**
   * Reads an XML definition from a java.io.Reader and parses it.
   *
   * @exception IOException
   *    if an error occured while reading the input
   * @exception XMLParseException
   *    if an error occured while parsing the read data
   */
  public void parseFromReader(Reader reader, int startingLineNr) throws IOException, XMLParseException {
    int blockSize = INITIAL_BLOCKSIZE;
    char[] input = null;
    int size = 0;

    while (reader.ready()) {
      if (input == null) {
        input = new char[blockSize];
      } else {
        char[] oldInput = input;
        input = new char[input.length*3];
        blockSize = oldInput.length * 2;
        System.arraycopy(oldInput, 0, input, 0, oldInput.length);
      }

      int charsRead = reader.read(input, size, blockSize);

      if (charsRead < 0) {
        break;
      }

      size += charsRead;
    }
    parseCharArray(input, 0, size, startingLineNr);
  }


  /**
   * Parses an XML definition.
   *
   * @exception XMLParseException
   *    if an error occured while parsing the string
   */
  public void parseString(String string) throws XMLParseException {
    parseCharArray(string.toCharArray(), 0, string.length(), 1);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the string following the XML data
   *
   * @exception XMLParseException
   *    if an error occured while parsing the string
   */
  public int parseString(String string, int offset) throws XMLParseException {
    return parseCharArray(string.toCharArray(), offset,
                          string.length(), 1);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the string following the XML data (&lt;= end)
   *
   * @exception XMLParseException
   *    if an error occured while parsing the string
   */
  public int parseString(String string, int offset, int end) throws XMLParseException {
    return parseCharArray(string.toCharArray(), offset, end, 1);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the string following the XML data (&lt;= end)
   *
   * @exception XMLParseException
   *    if an error occured while parsing the string
   */
  public int parseString(String string, int offset, int end, int startingLineNr) throws XMLParseException {
    return parseCharArray(string.toCharArray(), offset, end, startingLineNr);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the array following the XML data (&lt;= end)
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  public int parseCharArray(char[] input, int offset, int end) throws XMLParseException {
    return parseCharArray(input, offset, end, 1);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the array following the XML data (&lt;= end)
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  public int parseCharArray(char[] input, int offset, int end, int startingLineNr) throws XMLParseException {
    int[] lineNr = new int[1];
    lineNr[0] = startingLineNr;
    return parseCharArray(input, offset, end, lineNr);
  }


  /**
   * Parses an XML definition starting at <I>offset</I>.
   *
   * @return the offset of the array following the XML data (&lt;= end)
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int parseCharArray(char[] input, int startLoc, int end, int[] currentLineNr) throws XMLParseException {
    int offset = startLoc;
    offset = skipWhitespace(input, offset, end, currentLineNr);
    offset = skipPreamble(input, offset, end, currentLineNr);
    offset = scanTagName(input, offset, end, currentLineNr);
    _lineNr = currentLineNr[0];
    offset = scanAttributes(input, offset, end, currentLineNr);
    int[] contentOffset = new int[1];
    int[] contentSize = new int[1];
    int contentLineNr = currentLineNr[0];
    offset = scanContent(input, offset, end, contentOffset, contentSize, currentLineNr);

    //  If there was any data (not including whitespace) between
    //  this tag and the next, then process that.
    if (contentSize[0] > 0) {
      scanChildren(input, contentOffset[0], contentSize[0], contentLineNr);

      //  If child-elements were found, then contents is set to null...
      if (_children.size() > 0) {
        _contents = null;
      } else {
        //  If there are no child elements, but there is data, then
        //  it's contents to be processed.
        processContents(input, contentOffset[0], contentSize[0], contentLineNr);
      }
    }

    return offset;
  }

  /**
   * Decodes the entities in the contents and, if skipLeadingWhitespace is
   * <CODE>true</CODE>, removes extraneous whitespaces after newlines and
   * convert those newlines into spaces.
   *
   * @see XMLElement#decodeString
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected void processContents(char[] input, int contentOffset,
                                 int contentSize, int contentLineNr) throws XMLParseException {
    int[] lineNr = new int[1];
    lineNr[0] = contentLineNr;

    if (! _skipLeadingWhitespace) {
      String str = new String(input, contentOffset, contentSize);
      _contents = decodeString(str, lineNr[0]);
      if(_contents.startsWith("<![CDATA[")) {
        _contents = _contents.substring(9, _contents.length()-3);
      }
      return;
    }

    StringBuffer result = new StringBuffer(contentSize);
    int end = contentSize + contentOffset;

    for (int i = contentOffset; i < end; i++) {
      char ch = input[i];

      // The end of the contents is always a < character, so there's
      // no danger for bounds violation
      while ((ch == '\r') || (ch == '\n')) {
        lineNr[0]++;
        result.append(ch);

        i++;
        ch = input[i];

        if (ch != '\n') {
          result.append(ch);
        }

        do {
          i++;
          ch = input[i];
        } while ((ch == ' ') || (ch == '\t'));
      }

      if (i < end) {
        result.append(ch);
      }
    }

    _contents = decodeString(result.toString(), lineNr[0]);
    if(_contents.startsWith("<![CDATA[")) {
      _contents = _contents.substring(9, _contents.length()-3);
    }
  }

  /**
   * Scans the attributes of the object.
   *
   * @return the offset in the string following the attributes, so that
   *         input[offset] in { '/', '>' }
   *
   * @see XMLElement#scanOneAttribute
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int scanAttributes(char[] input, int startLoc, int end, int[] lineNr) throws XMLParseException {
    int offset = startLoc;
    boolean done=false;
    while(!done) {
      offset = skipWhitespace(input, offset, end, lineNr);

      char ch = input[offset];

      if ((offset + 1 < end && ch == '/' && input[offset + 1] == '>') || ch == '>') done = true;
      else offset = scanOneAttribute(input, offset, end, lineNr);
    }

    return offset;
  }


  /**
   * Searches the content for child objects. If such objects exist, the
   * content is reduced to <CODE>null</CODE>.
   *
   * @see XMLElement#parseCharArray
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected void scanChildren(char[] input, int contentOffset, int contentSize,
                              int contentLineNr) throws XMLParseException {
    int end = contentOffset + contentSize;
    int offset = contentOffset;
    int lineNr[] = new int[1];
    lineNr[0] = contentLineNr;

    //noinspection PointlessArithmeticExpression
    if(contentSize > 11 &&
       input[offset+0] == '<' &&
       input[offset+1] == '!' &&
       input[offset+2] == '[' &&
       input[offset+3] == 'C' &&
       input[offset+4] == 'D' &&
       input[offset+5] == 'A' &&
       input[offset+6] == 'T' &&
       input[offset+7] == 'A' &&
       input[offset+8] == '[' &&
       input[offset+contentSize-3] == ']' &&
       input[offset+contentSize-2] == ']' &&
       input[offset+contentSize-1] == '>') {
      return;
    }

    while (offset < end) {
      try {
        offset = skipWhitespace(input, offset, end, lineNr);
      } catch (XMLParseException e) {
        return;
      }

      if (input[offset] != '<') {
        return;
      }

      XMLElement child = new XMLElement(_conversionTable, _skipLeadingWhitespace, false);
      offset = child.parseCharArray(input, offset, end, lineNr);
      _children.add(child);
    }
  }


  /**
   * Scans the content of the object.
   *
   * @return the offset after the XML element; contentOffset points to the
   *         start of the content section; contentSize is the size of the
   *         content section.  'offset' points to the first byte past the
   *         tag and attributes.  (I.e. in '<zarf/>', offset would point to
   *         the '/'.)  If multiple content sections were found, it SHOULD
   *         point each of the contentOffset entries to them.  That's not
   *         really possible with this design.
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int scanContent(char[] input, int startLoc, int expectedEnd,
                            int[] contentOffset, int[] contentSize, int[] lineNr) throws XMLParseException {
    int offset=startLoc;
    int end = expectedEnd;
    char ch = input[offset];

    //  If this is an 'EMPTY' tag, then the content size is zero.  Ensure that the
    //  next character is a '>' and punt.
    if (ch == '/') {
      _empty = true;
      contentSize[0] = 0;

      //  Return an offset pointing to right after the tag.
      if (input[offset + 1] == '>') return offset + 2;

      throw expectedInput("'>'", lineNr[0]);
    }

    //  Otherwise this REALLY should be a '>', to end the open-tag we've just
    //  processed.
    if (ch != '>') {
      throw expectedInput("'>'", lineNr[0]);
    }

    //  Everything's okay so far, now skip all whitespace between the backside of the
    //  open tag, and the first non-whitespace character.
    if (_skipLeadingWhitespace) {
      offset = skipWhitespace(input, offset + 1, end, lineNr);
    } else {
      offset++;
    }

    //  'begin' is the beginning of non-whitespace content.
    //    int begin = offset;

    //  Write out the start location of the content.
    contentOffset[0] = offset;
    //  'tag' is used to search for the end-tag for this item.
    char[] tag = _tagName.toCharArray();
    //  Since there 'must' be </'tag'> at the end, it pulls back the end of
    //  the possible content.
    end -= (tag.length + 2);

    //  Level is, in some fashion, the number of elements deep we are.
    int level = 0;
    while ((offset < end) && (level >= 0)) {
      ch = input[offset];

      //  Is it the start of another tag, either close or open?
      if (ch == '<') {
        boolean ok = true;

        //  Loop over the tag, just comparing the tag with the 'current' tag,
        //  to see if it's identical.
        for (int i = 0; ok && (i < tag.length); i++) {
          ok &= (input[offset + (i + 1)] == tag[i]);
        }

        //  If the tag is identical...  (Nodes can be embedded inside themselves?!?)
        if (ok) {
          //  off2 is the content starting just past the end of the duplicate tag.
          int off2 = offset + tag.length + 1;

          //  Skip all whitespace after the duplicate tag, and return
          //  the offset of that location.
          offset = skipWhitespace(input, off2, end, lineNr);
          ch = input[offset];

          // Open level if /<BLAH[ \t]+[^\/]/ or /<BLAH[ \t]*>/ Allow
          // another level deep, if it's the same node as the outer
          // one.  I.e. nodes can be embedded w/in themselves.
          // Prolly wrong.
          if ((ch == '>') || ((off2 != offset) && (ch != '/'))) {
            level++;
          }
          continue;

          //  On the other hand, if it's not identical, let's see if
          //  it's a close-tag.
        } else if (input[offset + 1] == '/') {
          ok = true;

          //  It is a close tag, let's see if it's for the current tag.
          for (int i = 0; ok && (i < tag.length); i++) {
            ok &= (input[offset + (i + 2)] == tag[i]);
          }

          //  Great!  It's a close tag for the current tag!  We're set!
          if (ok) {
            //  Set the content size that gets returned.
            contentSize[0] = offset - contentOffset[0];
            //  Increment the offset to just past the open tag.
            offset += tag.length + 2;
            //  plus any whitespace.
            offset = skipWhitespace(input, offset, end, lineNr);

            //  If there's an end-tag marker, consider it ending the
            //  current depth, since it ends the current tag too...
            if (input[offset] == '>') {
              level--;
              offset++;
            }
          } else {
            offset++;
          }
          continue;
        }
      }

      //  Is it the end of the line?
      if (ch == '\r') {
        lineNr[0]++;

        //  (On the off chance it's DOS formatted, clear out the
        //  extra character too...)
        if ((offset != end) && (input[offset + 1] == '\n')) {
          offset++;
        }
        //  In case it's Mac formatted...
      } else if (ch == '\n') {
        lineNr[0]++;
      }

      offset++;
    }

    //  If there weren't an equal amount of tag starts as tag ends,
    //  then punt.
    if (level >= 0) {
      throw unexpectedEndOfData(lineNr[0]);
    }

    //  If we're supposed to skip whitespace then go ahead and do
    //  that.  NOTE: skipLeadingWhitespace is a misnomer!  This skips
    //  leading AND trailing whitespace!
    if (_skipLeadingWhitespace) {
      int i = contentOffset[0] + contentSize[0] - 1;

      while ((contentSize[0] >= 0) && (input[i] <= ' ')) {
        i--;
        contentSize[0]--;
      }
    }

    return offset;
  }

  /**
   * Scans an identifier.
   *
   * @return the identifier, or <CODE>null</CODE> if offset doesn't point
   *         to an identifier
   */
  protected String scanIdentifier(char[] input, int offset, int end) {
    int begin = offset;

    while ((offset < end) && (isIdentifierChar(input[offset]) || (input[offset] == '/' && input[offset+1] != '>'))) {
      offset++;
    }

    if ((offset == end) || (offset == begin)) {
      return null;
    } else {
      return new String(input, begin, offset - begin);
    }
  }


  /**
   * Scans one attribute of an object.
   *
   * @return the offset after the attribute
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int scanOneAttribute(char[] input, int startLoc, int end, int[] lineNr) throws XMLParseException {
    int offset = startLoc;

    String key = scanIdentifier(input, offset, end);

    if (key == null) {
      throw syntaxError("an attribute key", lineNr[0]);
    }

    offset = skipWhitespace(input, offset + key.length(), end, lineNr);
    key = key.toLowerCase(); // toUpperCase();
    String value;

    if (input[offset] == '=') {
      offset = skipWhitespace(input, offset + 1, end, lineNr);

      value = scanString(input, offset, end, lineNr);

      if (value == null) {
        throw syntaxError("an attribute value", lineNr[0]);
      }

      if (value.charAt(0) == '"') {
        value = value.substring(1, value.length() - 1);
        offset += 2;
      }
    } else {
      value = "";
      if(!key.equals("disabled") && !key.equals("checked") && !key.equals("/")) {
        if(mRejectBadHTML) {
          throw valueMissingForAttribute(key, lineNr[0]);
        }
      }
    }

    if(!key.equals("/")) _attributes.put(key, decodeString(value, lineNr[0]));
    return offset + value.length();
  }

  /**
   * Scans a string. Strings are either identifiers, or text delimited by
   * double quotes.
   *
   * @return the string found, without delimiting double quotes; or null
   *         if offset didn't point to a valid string
   *
   * @see XMLElement#scanIdentifier
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected String scanString(char[] input,
                              int    startLoc,
                              int    end,
                              int[]  lineNr)
    throws XMLParseException
  {
    int offset = startLoc;
    char delim = input[offset];

    if (delim == '"') {
      int begin = offset;
      offset++;

      while ((offset < end) && (input[offset] != delim)) {
        offset += nextCharCountSkipEOL(input, offset, end, lineNr);
      }

      if (offset == end) {
        return null;
      } else {
        return new String(input, begin, offset - begin + 1);
      }
    } else {
      return scanIdentifier(input, offset, end);
    }
  }


  /**
   * Scans the class (tag) name of the object.
   *
   * @return the position after the tag name
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int scanTagName(char[] input, int offset, int end, int[] lineNr) throws XMLParseException {
    _tagName = scanIdentifier(input, offset, end);

    if (_tagName == null) {
      throw syntaxError("a tag name", lineNr[0]);
    }

    return offset + _tagName.length();
  }


  /**
   * Skips a tag that don't contain any useful data: &lt;?...?&gt;,
   * &lt;!...&gt; and comments.
   *
   * @return the position after the tag
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int skipBogusTag(char[] input, int startLoc, int end, int[] lineNr) {
    int offset = startLoc;
    if ((input[offset + 1] == '-') && (input[offset + 2] == '-')) {
      while ((offset < end) && ((input[offset] != '-')
                                || (input[offset + 1] != '-')
                                || (input[offset + 2] != '>'))) {
        offset += nextCharCountSkipEOL(input, offset, end, lineNr);
      }

      if (offset == end) {
        throw unexpectedEndOfData(lineNr[0]);
      } else {
        return offset + 3;
      }
    }

    int level = 1;

    while (offset < end) {
      char ch = input[offset++];

      switch (ch) {
        case '\r':
          if ((offset < end) && (input[offset] == '\n')) {
            offset++;
          }

          lineNr[0]++;
          break;

        case '\n':
          lineNr[0]++;
          break;

        case '<':
          level++;
          break;

        case '>':
          level--;

          if (level == 0) {
            return offset;
          }

          break;

        default:
      }
    }

    throw unexpectedEndOfData(lineNr[0]);
  }


  /**
   * Skips a tag that don't contain any useful data: &lt;?...?&gt;,
   * &lt;!...&gt; and comments.
   *
   * @return the position after the tag
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int skipPreamble(char[] input,
                             int    startLoc,
                             int    end,
                             int[]  lineNr)
    throws XMLParseException
  {
    int offset = startLoc;
    char ch;

    do {
      offset = skipWhitespace(input, offset, end, lineNr);

      if (input[offset] != '<') {
        expectedInput("'<'", lineNr[0]);
      }

      offset++;

      if (offset >= end) {
        throw unexpectedEndOfData(lineNr[0]);
      }

      ch = input[offset];

      if ((ch == '!') || (ch == '?')) {
        offset = skipBogusTag(input, offset, end, lineNr);
      }
    } while (! isIdentifierChar(ch));

    return offset;
  }

  protected static int nextCharCountSkipEOL(char[] input, int offset, int end, int[] lineNr) {
    int initial_offset = offset;

    if (input[offset] == '\r') {
      lineNr[0]++;

      if ((offset != end) && (input[offset + 1] == '\n')) {
        offset++;
      }
    } else if (input[offset] == '\n') {
      lineNr[0]++;
    }
    if(offset == initial_offset) {
      return 1;
    } else {
      return offset-initial_offset;
    }
  }

  /**
   * Skips whitespace characters.
   *
   * @return the position after the whitespace
   *
   * @exception XMLParseException
   *    if an error occured while parsing the array
   */
  protected int skipWhitespace(char[] input, int startLoc, int end, int[] lineNr) {
    int offset = startLoc;
    while ((offset < end) && (input[offset] <= ' ')) {
      offset += nextCharCountSkipEOL(input, offset, end, lineNr);
    }

    if (offset == end) {
      throw unexpectedEndOfData(lineNr[0]);
    }

    return offset;
  }

  /**
   * Converts 'normal' characters into escaped '&amp;...;' sequences.
   * BUG -- This does not use the same conversion table as the main
   * decode routine.
   */
  public static String encodeString(String s) {
    char trueBytes[] = new char[s.length()];

    s.getChars(0, s.length(), trueBytes, 0);
    int lastIndex = 0;
    String insertString = null;
    StringBuffer outBuffer = null;
    for(int i = 0; i<trueBytes.length; i++) {
      char ch = trueBytes[i];
      switch(ch) {
        case '<':
          insertString = "&lt;";
          break;
        case '>':
          insertString = "&gt;";
          break;
        case '\"':
          insertString = "&quot;";
          break;
        case '\'':
          insertString = "&apos;";
          break;
        case '&':
          insertString = "&amp;";
          break;
        case '#':
          if(i == 0 || (i>0 && trueBytes[i-1] != '&')) insertString = "&pound;";
          break;
        default:
          insertString = null;
          break;
      }
      if(ch > 0x80) insertString = "&#" + (int)ch + ";";
      if(insertString != null) {
        if(outBuffer == null) outBuffer = new StringBuffer();
        outBuffer.append(s.substring(lastIndex, i));  //  TODO -- Relies on byte<->char mapping being 1:1 for UTF8.  Fix this?
        lastIndex = i+1;
        outBuffer.append(insertString);
      }
    }
    if(insertString == null) {
      if(lastIndex != 0) {
        outBuffer.append(s.substring(lastIndex));
      }
    }
    if(lastIndex == 0) return s;

  if(outBuffer == null) return null;
    return outBuffer.toString();
  }

  /**
   * Converts &amp;...; sequences to "normal" chars.
   */
  public String decodeString(String s, int    lineNr) {
    if(s == null) return "";
    StringBuffer result = new StringBuffer(s.length());
    int index = 0;

    for (;;) {
      int index2 = (s + '&').indexOf('&', index);
      result.append(s.substring(index, index2));

      if (index2 == s.length()) break;

      index = s.indexOf(';', index2);

      if (index < 0) {
        result.append(s.substring(index2));
        break;
      }

      String key = s.substring(index2 + 1, index);

      if (key.charAt(0) == '#') {
        if (key.charAt(1) == 'x') {
          result.append((char) Integer.parseInt(key.substring(2), 16));
        } else {
          result.append((char) Integer.parseInt(key.substring(1), 10));
        }
      } else {
        String cvt = _conversionTable.get(key);
        if(cvt == null) {
          result.append('&').append(key).append(';');
        } else {
          result.append(cvt);
        }
      }

      index++;
    }

    return result.toString();
  }


  /**
   * Creates a parse exception for when an invalid valueset is given to
   * a method.
   */
  protected XMLParseException invalidValueSet(String key) {
    String msg = "Invalid value set (key = \"" + key + "\")";
    return new XMLParseException(getTagName(), msg);
  }


  /**
   * Creates a parse exception for when an invalid value is given to a
   * method.
   */
  protected XMLParseException invalidValue(String key,
                                           String value,
                                           int    lineNr) {
    String msg = "Attribute \"" + key + "\" does not contain a valid "
      + "value (\"" + value + "\")";
    return new XMLParseException(getTagName(), lineNr,  msg);
  }


  /**
   * The end of the data input has been reached.
   */
  protected XMLParseException unexpectedEndOfData(int lineNr) {
    String msg = "Unexpected end of data reached";
    return new XMLParseException(getTagName(), lineNr,  msg);
  }

  /**
   * A syntax error occured.
   */
  protected XMLParseException syntaxError(String context, int lineNr) {
    String msg = "Syntax error while parsing " + context;
    return new XMLParseException(getTagName(), lineNr,  msg);
  }


  /**
   * A character has been expected.
   */
  protected XMLParseException expectedInput(String charSet, int lineNr) {
    String msg = "Expected: " + charSet;
    return new XMLParseException(getTagName(), lineNr,  msg);
  }


  /**
   * A value is missing for an attribute.
   */
  protected XMLParseException valueMissingForAttribute(String key, int lineNr) {
    String msg = "Value missing for attribute with key \"" + key + '\"';
    return new XMLParseException(getTagName(), lineNr,  msg);
  }
}
