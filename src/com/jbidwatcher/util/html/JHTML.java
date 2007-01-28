package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.net.*;
import java.io.*;
import java.util.*;

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.xml.XMLElement;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.auction.CleanupHandler;
import com.jbidwatcher.util.http.Http;

public class JHTML implements JHTMLListener {
  protected boolean m_loaded = false;
  protected int m_tokenIndex;
  protected int m_contentIndex;
  private JHTMLParser m_parser;
  private Map<String, intPair> contentMap;
  private Map<String, intPair> caselessContentMap;
  private List<String> contentList;
  private List<Form> m_formList;
  private Form m_curForm;
  private static boolean do_uber_debug=false;

  public JHTML(StringBuffer strBuf) {
    setup();
    m_parser = new JHTMLParser(strBuf, this);
  }

  private void setup() {
    caselessContentMap = new HashMap<String, intPair>();
    contentMap = new HashMap<String, intPair>();
    contentList = new ArrayList<String>();
    m_formList = new ArrayList<Form>();
    m_curForm = null;
    reset();
  }

  /**
   * @brief Set the 'tag pointer' to the start of the document.
   */
  public void reset() {
    m_tokenIndex = 0;
    m_contentIndex = 0;
  }

  private class intPair {
    public int first;
    public int second;

    public intPair(int f, int s) { first = f; second = s; }
  }

  public class Form {
    private List<XMLElement> allInputs;
    private XMLElement formTag;
    private static final String FORM_VALUE = "value";
    private static final String FORM_SUBMIT = "submit";
    private static final String FORM_CHECKBOX = "checkbox";
    public static final String FORM_PASSWORD = "password";
    private static final String FORM_HIDDEN = "hidden";
    private static final String FORM_RADIO = "radio";

    public Form(String initialTag) {
      formTag = new XMLElement();
      formTag.parseString('<' + initialTag + "/>");

      allInputs = new ArrayList<XMLElement>();

      if (do_uber_debug) ErrorManagement.logDebug("Name: " + formTag.getProperty("name", "(unnamed)"));
    }

    public String getName() { return formTag.getProperty("name"); }
    public boolean hasInput(String srchFor) {
      for (XMLElement curInput : allInputs) {
        String name = curInput.getProperty("name");
        if (name != null) {
          if (srchFor.equalsIgnoreCase(name)) {
            return true;
          }
        }
      }
      return false;
    }

    public boolean delInput(String srchFor) {
      Iterator<XMLElement> it = allInputs.iterator();
      while (it.hasNext()) {
        XMLElement curInput = it.next();
        String name=curInput.getProperty("name");
        if(name != null) {
          if(srchFor.equalsIgnoreCase(name)) {
            it.remove();
            return true;
          }
        }
      }
      return false;
    }

    public String getCGI() throws UnsupportedEncodingException {
      Iterator<XMLElement> it = allInputs.iterator();
      StringBuffer rval = new StringBuffer("");
      String seperator = "";
      while(it.hasNext()) {
        XMLElement curInput = it.next();

        if(do_uber_debug) ErrorManagement.logDebug("Type == " + curInput.getProperty("type", "text"));
        if (rval.length() != 0) {
          seperator = "&";
        }

        String type = curInput.getProperty("type", "text");
        String name = curInput.getProperty("name", "");

        if(type.equals("text") || type.equals(FORM_HIDDEN) || type.equals(FORM_PASSWORD)) {
          //  Need to URL-Encode 'value'...
          rval.append(seperator).append(name).append('=').append(URLEncoder.encode(curInput.getProperty(FORM_VALUE, ""), "UTF-8"));
        } else if(type.equals(FORM_CHECKBOX) || type.equals(FORM_RADIO)) {
          if(curInput.getProperty("checked") != null) {
            rval.append(seperator).append(name).append('=').append(URLEncoder.encode(curInput.getProperty(FORM_VALUE, "on"), "UTF-8"));
          }
        } else if(type.equals(FORM_SUBMIT)) {
          if(name.length() != 0) {
            String value = curInput.getProperty(FORM_VALUE, "Submit");
            if (!value.equalsIgnoreCase("cancel")) {
              rval.append(seperator).append(name).append('=').append(URLEncoder.encode(value, "UTF-8"));
            }
          }
        }
      }

      String action = formTag.getProperty(JHTMLDialog.FORM_ACTION);
      if(action != null) {
        if (action.indexOf('?') == -1) {
          return action + '?' + rval;
        } else {
          return action + '&' + rval;
        }
      } else {
        return rval.toString();  //  If no action?!?
      }
    }

    public void addInput(String newTag) {
      XMLElement inputTag = new XMLElement();

      inputTag.parseString('<' + newTag + "/>");
      boolean isError = false;
      String inputType = inputTag.getProperty("type", "text").toLowerCase();

      boolean showInputs = JConfig.queryConfiguration("debug.showInputs", "false").equals("true");

      if(inputType.equals("text")) {
        if (showInputs) ErrorManagement.logDebug("T: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals(FORM_PASSWORD)) {
        if (showInputs) ErrorManagement.logDebug("P: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if (inputType.equals(FORM_HIDDEN)) {
        if (showInputs) ErrorManagement.logDebug("H: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals(FORM_CHECKBOX)) {
        if (showInputs) ErrorManagement.logDebug("CB: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals(FORM_RADIO)) {
        if (showInputs) ErrorManagement.logDebug("R: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals(FORM_SUBMIT)) {
        if (showInputs) ErrorManagement.logDebug("S: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals("image")) {
        if (showInputs) ErrorManagement.logDebug("I: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals("button")) {
        if (showInputs) ErrorManagement.logDebug("B: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else if(inputType.equals("reset")) {
        if (showInputs) ErrorManagement.logDebug("RST: Name: " + inputTag.getProperty("name") + ", Value: " + inputTag.getProperty(FORM_VALUE));
      } else {
        ErrorManagement.logDebug("Unknown input type: " + inputType);
        isError = true;
      }

      if(!isError) {
        allInputs.add(inputTag);
      }
    }

    public void setText(String key, String val) {
      for (XMLElement curInput : allInputs) {
        String name = curInput.getProperty("name");

        if (name != null) {
          if (name.equalsIgnoreCase(key)) {
            curInput.setProperty(FORM_VALUE, val);
          }
        }
      }
    }
  }

  public List<Form> getForms() { return m_formList; }

  /**
   * @brief Added to work with JHTMLParser, which takes a JHTMLListener (which this implements); this
   * adds each content token into a hash map for later fast lookup.
   *
   * @param newToken - The token that has been extracted.
   * @param contentIndex - This token's index into the total token list...
   * m_parser.getTokenAt(contentIndex) == newTok.
   */
  public void addToken(htmlToken newToken, int contentIndex) {
    if(newToken.getTokenType() == htmlToken.HTML_CONTENT) {
      //  Non-numeric single character content tokens suck.
      if(newToken.getToken().length() == 1 && !Character.isDigit(newToken.getToken().charAt(0))) return;
      //  Keep the content stored by lowercase value, for case-insensitive searching.
      //  Store the passed content index (the 'real' index), and the internal index,
      //  for quick lookups.
      intPair pair = new intPair(contentIndex, contentList.size());

      //  First entry into the table wins.
      if(!contentMap.containsKey(newToken.getToken())) {
        contentMap.put(newToken.getToken(), pair);
        caselessContentMap.put(newToken.getToken().toLowerCase(), pair);
      }
      contentList.add(newToken.getToken());
    } else {
      if(newToken.getTokenType() == htmlToken.HTML_TAG ||
        newToken.getTokenType() == htmlToken.HTML_ENDTAG ||
        newToken.getTokenType() == htmlToken.HTML_SINGLETAG) {
        if(newToken.getToken().toLowerCase().startsWith("form")) {
          if(m_curForm == null) {
            m_curForm = new Form(newToken.getToken());
          } else {
            m_formList.add(m_curForm);
            m_curForm = new Form(newToken.getToken());
          }
        } else if(newToken.getToken().toLowerCase().startsWith("/form")) {
          if(m_curForm != null) m_formList.add(m_curForm);
          m_curForm = null;
        }
        if(m_curForm != null) {
          if(newToken.getToken().regionMatches(true, 0, "input", 0, 5)) {
            m_curForm.addInput(newToken.getToken());
          }
        }
      }
    }
  }

  //------------------------------------------------------------
  // Content operations.
  //------------------------------------------------------------

  /**
   * @brief Helper function to retrieve just the first piece of content from a potentially HTML string.
   *
   * @param toSearch - The string to search for non-tag content.
   *
   * @return The very first block of non-tag content in a potentially HTML string.
   */
  public static String getFirstContent(String toSearch) {
    JHTML parser = new JHTML(new StringBuffer(toSearch));

    return parser.contentList.get(0);
  }

  public String getFirstContent() {
    if(contentList.size() == 0) return null;
    return contentList.get(0);
  }

  public String getNextContent() {
    if( (m_contentIndex+1) >= contentList.size()) return null;

    return contentList.get(m_contentIndex++);
  }

  public String getPrevContent() {
    if(m_contentIndex == 0) return null;

    return contentList.get(--m_contentIndex);
  }

  public String getPrevContent(int farBack) {
    if(farBack > m_contentIndex) {
      m_contentIndex = 0;
      return null;
    }

    m_contentIndex -= farBack;
    return contentList.get(m_contentIndex);
  }

//  None of these parameter definitions are needed right now.

//  private static final boolean IGNORE_CASE = true;
//  private static final boolean IS_REGEX = true;
//  private static final boolean NOT_REGEX = false;
//  private static final boolean EXACT = true;
//  private static final boolean INEXACT = false;
//  private static final int DOWN = -1;
//  private static final int UP = 1;
  private static final boolean CHECK_CASE = false;

  public intPair lookup(String hunt, boolean caseless) {
    intPair at;
    if (caseless) {
      at = caselessContentMap.get(hunt.toLowerCase());
    } else {
      at = contentMap.get(hunt);
    }
    return at;
  }

  private String contentLookup(String hunt, boolean caseless) {
    intPair at = lookup(hunt, caseless);
    if(at == null) return null;

    m_tokenIndex = at.first+2;
    m_contentIndex = at.second+1;
    return contentList.get(m_contentIndex++);
  }

  public String find(String hunt, boolean ignoreCase) {
    for (String nextContent : contentList) {
      if (nextContent.regionMatches(ignoreCase, 0, hunt, 0, hunt.length())) {
        return nextContent;
      }
    }

    return null;
  }

  private String contentFind(String hunt, boolean ignoreCase) {
    String nextContent = find(hunt, ignoreCase);
    if (nextContent != null) {
      //  This might not be safe...
      nextContent = contentLookup(nextContent, CHECK_CASE);
    }
    return nextContent;
  }

  public String grep(String match) {
    for (String nextContent : contentList) {
      if (nextContent.matches(match)) {
        //  This might not be safe...
        return nextContent;
      }
    }
    return null;
  }

  private String grepAfter(String match, String ignore) {
    for (Iterator<String> it = contentList.iterator(); it.hasNext();) {
      String contentStep = it.next();
      if(contentStep.matches(match)) {
        Iterator<String> save = it;
        if(it.hasNext()) {
          String potential = it.next();
          if(ignore == null || !potential.matches(ignore)) {
            contentLookup(contentStep, false);
            return potential;
          }
        }
        it = save;
      }
    }

    return null;
  }

  private String contentGrep(String match, String ignore) {
    return grepAfter(match, ignore);
  }

  //  Default to caseless lookups.
  public String getNextContentAfterContent(String previousData) {
    return contentFind(previousData, CHECK_CASE);
  }

  public String getNextContentAfterContent(String previousData, boolean exactMatch, boolean ignoreCase) {
    if (exactMatch) {
      return contentLookup(previousData, ignoreCase);
    } else {
      return contentFind(previousData, ignoreCase);
    }
  }

  public String getContentBeforeContent(String followingData) {
    if (contentFind(followingData, CHECK_CASE) != null && getPrevContent() != null && getPrevContent() != null) return getPrevContent();
    return null;
  }

  public String getNextContentAfterRegex(String match) {
    return contentGrep(match, null);
  }

  public String getNextContentAfterRegexIgnoring(String match, String ignore) {
    return contentGrep(match, ignore);
  }

  //------------------------------------------------------------
  // Tag operations.
  //------------------------------------------------------------

  public String getNextTag() {
    htmlToken returnToken = nextToken();
    if (returnToken != null) {
      while (returnToken != null &&
        returnToken.getTokenType() == htmlToken.HTML_CONTENT &&
        returnToken.getTokenType() != htmlToken.HTML_EOF) {
        returnToken = nextToken();
      }
      if (returnToken != null && returnToken.getTokenType() != htmlToken.HTML_EOF) {
        return returnToken.getToken();
      }
    }

    return null;
  }

  public List<String> getAllLinks() {
    List<String> linkTags = null;
    String curTag = getNextTag();

    while(curTag != null) {
      if(curTag.startsWith("A ") || curTag.startsWith("a ")) {
        if(linkTags == null) {
          linkTags = new ArrayList<String>();
        }
        linkTags.add(curTag);
      }

      curTag = getNextTag();
    }
    return linkTags;
  }

  public List<String> getAllImages() {
    HashSet<String> linkTags = null;
    String curTag = getNextTag();

    while(curTag != null) {
      if(curTag.toLowerCase().startsWith("img ")) {
        if(linkTags == null) {
          linkTags = new HashSet<String>();
        }
        linkTags.add(deAmpersand(curTag));
      }

      curTag = getNextTag();
    }

    return new ArrayList<String>(linkTags);
  }

  public List<String> getAllURLsOnPage(boolean viewOnly) {
    // Add ALL auctions on myEbay bidding/watching page!
    List<String> addressTags = getAllLinks();
    if(addressTags == null) return null;
    List<String> outEntries = null;

    for (String curTag : addressTags) {
      //  Extract just the HREF portion (should look for HREF=\")
      int searchIndex = curTag.indexOf('"');
      if (searchIndex != -1) {
        String href = curTag.substring(searchIndex + 1);

        //  Find the end of the quoted string (hopefully)
        searchIndex = href.indexOf('"');
        if (searchIndex != -1) {
          href = href.substring(0, searchIndex);

          searchIndex = href.indexOf('#');
          //  As long as there isn't an anchor location...
          if (searchIndex == -1) {
            boolean isView = false;
            if (viewOnly) {
              isView = href.indexOf("ViewItem") != -1;
              if (isView) {
                href = deAmpersand(href);
              }
            }

            if (!viewOnly || isView) {
              if (outEntries == null) {
                outEntries = new ArrayList<String>();
              }
              outEntries.add(href);
            }
          }
        }
      }
    }
    return outEntries;
  }

  public static String deAmpersand(String href) {
    int searchIndex = href.indexOf("&amp;");
    while (searchIndex != -1) {
      href = href.substring(0, searchIndex + 1) +
              href.substring(searchIndex + 5);
      searchIndex = href.indexOf("&amp;");
    }

    return href;
  }

  //------------------------------------------------------------
  // Generic token operations.
  //------------------------------------------------------------

  public htmlToken nextToken() {
    htmlToken rval = m_parser.getTokenAt(m_tokenIndex++);
    if (rval == null) --m_tokenIndex;
    return rval;
  }

  public boolean isLoaded() { return m_loaded; }

  private void loadParseURL(String newURL, String cookie, CleanupHandler cl) {

    m_parser = new JHTMLParser(this);
    StringBuffer loadedPage;

    try {
      URLConnection uc = Http.getPage(newURL, cookie, null, true);
      loadedPage = Http.receivePage(uc);
      if(loadedPage != null) {
        if(cl != null) {
          cl.cleanup(loadedPage);
        }
        m_parser.parse(loadedPage);
        m_loaded = true;
      }
    } catch(IOException e) {
      loadedPage = null;
      ErrorManagement.handleException("JHTML.loadPage: " + e, e);
    }
    if(loadedPage == null) m_loaded = false;
  }

  /** 
   * @brief Simple function that does all the 'usual' stuff for a web page,
   * constructing a JHTML object with the data from the given page.
   * 
   * For pages that need more processing, they have to do it by hand.
   *
   * @param newURL - The URL to get, receive, and pre-parse.
   * @param cookie - A cookie to pass along when getting the page.
   * @param cl - A CleanupHandler to call to clean up the StringBuffer before continuing.
   */
  public JHTML(String newURL, String cookie, CleanupHandler cl) {
    setup();
  	loadParseURL(newURL, cookie, cl);
  }

  public JHTML.Form getFormWithInput(String input) {
    List<Form> forms = getForms();
    for (Form curForm : forms) {
      if (curForm.hasInput(input)) return curForm;
    }

    return null;
  }

  public static void main(String[] args) {
    JHTML testLoad = new JHTML(args[1], null, null);
    JHTML.Form continueForm = testLoad.getFormWithInput("firedFilterId");
    try {
      System.err.println("Form == \"" + continueForm.getCGI() + '\"' );
    } catch(Exception e) {
      System.err.println("Caught: " + e);
    }
  }
}
