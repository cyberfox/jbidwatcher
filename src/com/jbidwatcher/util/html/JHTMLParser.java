package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.xml.XMLElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Morgan Schweers
 * Date: Jun 26, 2004
 * Time: 2:34:56 PM
 *
 */
public class JHTMLParser {
  private List<htmlToken> m_tokens;
  private JHTMLListener m_notify = null;

  public JHTMLParser(StringBuffer sb, JHTMLListener notify) {
    m_notify = notify;
    setup();
    parse(sb);
  }

  public JHTMLParser(JHTMLListener notify) {
    m_notify = notify;
    setup();
  }

  private void setup() {
    m_tokens = Collections.synchronizedList(new ArrayList<htmlToken>());
  }

  protected void parse(StringBuffer trueBuffer) {
    boolean inQuote=false, inTag=false, inComment=false;
    char ch, prev = '\0', next = '\0';

    trueBuffer = setupBuffer(trueBuffer);

    int bufLen = trueBuffer.length();
    boolean spitNextTag = false;
    int start = 0;
    int firstClose = 0;
    boolean suspicious = false;
    for(int charStep = 0; charStep<bufLen; charStep++) {
      ch = trueBuffer.charAt(charStep);

      if(charStep>1) prev = trueBuffer.charAt(charStep-1);
      if(charStep<(bufLen-1)) next = trueBuffer.charAt(charStep+1);

      if(inTag) {
        // quoting disabled inside of comment
        if(!inComment) {
          if(inQuote && ch == '>') {
            suspicious = true;
            if(JConfig.debugging) {
              debugUnusualTags(trueBuffer, bufLen, charStep);
            }
            firstClose = charStep;
          }
          if(ch == '"') {
            //  This tries to detects not closing a quote.  It only
            //  works if the next open quote is in another tag,
            //  instead of in the middle of some random content.
            if(suspicious && inQuote && prev == '=') {
              charStep=firstClose;

              if(charStep>1) prev = trueBuffer.charAt(charStep-1);
              if(charStep<(bufLen-1)) next = trueBuffer.charAt(charStep+1);

              inQuote = false;
              suspicious = false;
              JConfig.log().logDebug("Potential quote error!");
              spitNextTag = true;
            }

            if(isEndTag(inQuote, prev, next)) {
              spitNextTag = true;
            } else {
              inQuote = !inQuote;
            }
          }
        }
        // parsing disabled inside of quoted string
        if(!inQuote) {
          // end Tag and start Content
          if(ch == '>') {
            if(!inComment) {
              //  We've ended a tag, outside a quote.  It's all good.
              if(suspicious) suspicious = false;
              spitNextTag = addValidTag(trueBuffer, spitNextTag, start, charStep);
            } else {
              // Comment ends with "-->"
              inComment = (prev != '-') || (trueBuffer.charAt(charStep-2) != '-');
            }
            inTag = inComment;
            if(!inTag) start = charStep+1;  // start of content
          }
        }
      } else {
        // in Content
        if(ch == '<') {
          addStartTag(trueBuffer, start, charStep);

          inTag = true;

          // Comments begin with "<!--"
          inComment = (charStep+3 < bufLen) && (next == '!')
                   && (trueBuffer.charAt(charStep+2) == '-')
                   && (trueBuffer.charAt(charStep+3) == '-');

          start = charStep+1;  // start of tag

          if(inComment) charStep += 3;
        }
      }
    }

    addToken("", htmlToken.HTML_EOF);
  }

  private StringBuffer setupBuffer(StringBuffer trueBuffer) {
    if(JConfig.queryConfiguration("ebay.titleFix", "true").equals("true")) {
      StringBuffer trueBuffer1 = trueBuffer;
      StringBuffer sb1;
      Matcher m1;
      sb1 = new StringBuffer(trueBuffer1.length());
      m1 = Pattern.compile("<title>(.*)</title>").matcher(trueBuffer1);
      String quotedTitle = null;
      while(m1.find()) {
        if(quotedTitle == null) quotedTitle = "<title>" + XMLElement.encodeString(m1.group(1)) + "</title>";
        m1.appendReplacement(sb1, Matcher.quoteReplacement(quotedTitle));
      }
      m1.appendTail(sb1);
      trueBuffer1 = sb1;
      trueBuffer = trueBuffer1;
    }

    trueBuffer = stripNoBR(trueBuffer);
    return trueBuffer;
  }

  private void addStartTag(StringBuffer trueBuffer, int start, int charStep) {// end Content and start Tag
    if(start != charStep) {
      String whatToAdd = trueBuffer.substring(start, charStep);
      String trimmed = whatToAdd.trim();

      if(!trimmed.equals("")) {
        addToken(whatToAdd, htmlToken.HTML_CONTENT);
      }
    }
  }

  private boolean addValidTag(StringBuffer trueBuffer, boolean spitNextTag, int start, int charStep) {
    addToken(trueBuffer.substring(start, charStep), htmlToken.HTML_TAG);
    if(spitNextTag) {
      if(JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
        JConfig.log().logDebug("Added 'bad' tag: <" + trueBuffer.substring(start, charStep) + ">");
      }
      spitNextTag = false;
    }
    return spitNextTag;
  }

  private void debugUnusualTags(StringBuffer trueBuffer, int bufLen, int charStep) {
    int pre_nl=0, post_nl=0, i;
    for(i=charStep-1; pre_nl == 0 && i>0 && i>(charStep-40); i--) if(trueBuffer.charAt(i) == '\n') pre_nl = i+1;
    if(pre_nl == 0) pre_nl = i;
    for(i=charStep+1; post_nl == 0 && i<bufLen && i<(charStep+20); i++) if(trueBuffer.charAt(i) == '\n') post_nl = i;
    if(post_nl == 0) post_nl = i;
    String oddText = trueBuffer.substring(pre_nl, post_nl);
    if(oddText.indexOf("type=\"submit\"") == -1 &&
       oddText.indexOf("name=\"Submit\"") == -1 &&
       !oddText.startsWith("<META")) {
      if(JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
        JConfig.log().logMessage("Found an unusual tag @ " + charStep + "...  (" + oddText + ")");
      }
    }
  }

  private StringBuffer stripNoBR(StringBuffer trueBuffer) {
    StringBuffer sb;
    Matcher m;
    sb = new StringBuffer(trueBuffer.length());
    m = Pattern.compile("(<nobr>|</nobr>)").matcher(trueBuffer);
    while(m.find()) {
      m.appendReplacement(sb, "");
    }
    m.appendTail(sb);
    trueBuffer = sb;
    return trueBuffer;
  }

  private void addToken(String newToken, int tokType) {
    htmlToken finalToken;

    switch(tokType) {
      //  Tags are the page-logic.
      case htmlToken.HTML_TAG: {
        int realTokenType = tokType;
        if(isEndTag(newToken)) realTokenType = htmlToken.HTML_ENDTAG;
        if(isSingletonTag(newToken)) realTokenType = htmlToken.HTML_SINGLETAG;
        finalToken = new htmlToken(newToken, realTokenType);
        break;
      }
      //  Content is the non-layout portions of the document.
      case htmlToken.HTML_CONTENT: {
        String cleanToken = stripWhitespace(newToken);

        if(cleanToken.length() == 0) return;

        finalToken = new htmlToken(cleanToken, tokType);
        break;
      }
      //  Things like 'HTML_ENDTOKEN', and other arbitrary m_tokens.
      default: {
        finalToken = new htmlToken(newToken, tokType);
      }
    }

    if(m_notify != null) {
      m_notify.addToken(finalToken, m_tokens.size());
    }
    m_tokens.add(finalToken);
  }

  //  Endtags start with '/', i.e. </A>.
  //
  private boolean isEndTag(String checkTag) {
    return(checkTag.length() != 0 && checkTag.charAt(0) == '/');
  }

  /**
   * Right now this 'fakes' it, by checking for the XMLish extension to
   * HTML, which places a '/' at the end of singleton tags.
   *
   * @param checkTag - The tag to check, to see if it's a singleton.
   *
   * @return - true if the tag is considered a singleton, false otherwise.
   */
  private boolean isSingletonTag(String checkTag) {
    return(checkTag.length() != 0 && checkTag.charAt(checkTag.length()-1)=='/');
  }

  //  Strip whitespace, including 'faked' whitespace (&nbsp;) from both sides of the provided string,
  //  and faked whitespace from the inside of the string.
  private String stripWhitespace(String cleanupString) {
    String resultString = cleanupString.replaceAll("&nbsp;?", " ");

    if(resultString.length() != 0) {
      resultString = resultString.replace((char)160, (char)32);
    }

    //  Trim, to remove anything left after &nbsp; stripping.
    return resultString.trim();
  }

  public List<htmlToken> getTokens() {
    return m_tokens;
  }

  public int getTokenCount() { return m_tokens.size(); }

  public htmlToken getTokenAt(int index) {
    if (index < getTokenCount()) {
      return (m_tokens.get(index));
    }

    return null;
  }
}
