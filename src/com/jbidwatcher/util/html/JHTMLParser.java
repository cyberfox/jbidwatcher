package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.util.ErrorManagement;

import java.util.Vector;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Administrator
 * Date: Jun 26, 2004
 * Time: 2:34:56 PM
 *
 */
public class JHTMLParser {
  private List<htmlToken> m_tokens;
  private JHTMLListener m_notify = null;
  private final static boolean do_uber_debug = false;

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
    m_tokens = new Vector<htmlToken>();
  }

  protected void parse(StringBuffer trueBuffer) {
    boolean inQuote=false, inTag=false, inComment=false;
    boolean suspicious = false;
    int firstClose=0;
    int charStep, start=0;
    char ch, prev = '\0', next = '\0';
    boolean spitNextTag = false;

    trueBuffer = new StringBuffer(trueBuffer.toString().replaceAll("(<nobr>|</nobr>)", ""));
    int bufLen = trueBuffer.length();

    for(charStep=0; charStep<bufLen; charStep++) {
      ch = trueBuffer.charAt(charStep);

      if(charStep>1) prev = trueBuffer.charAt(charStep-1);
      if(charStep<(bufLen-1)) next = trueBuffer.charAt(charStep+1);

      if(inTag) {
        // quoting disabled inside of comment
        if(!inComment) {
          if(inQuote && ch == '>') {
            suspicious = true;
            if(JConfig.debugging) {
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
                  ErrorManagement.logMessage("Found an unusual tag @ " + charStep + "...  (" + oddText + ")");
                }
              }
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
              ErrorManagement.logDebug("Potential quote error!");
              spitNextTag = true;
            }
            //  This prevents opening a quote at the end of a tag.
            if(!inQuote && prev != '=' && next == '>') {
              if(JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
                ErrorManagement.logDebug("Quote error!");
              }
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
              if(charStep < start) {
                if(do_uber_debug) {
                  ErrorManagement.logDebug("substring(" + start + ", " + charStep + ") of " + trueBuffer.length());
                  ErrorManagement.logDebug("FAILURE @\n-------------------\n" + trueBuffer.substring(charStep, start));
                }
              }
              addToken(trueBuffer.substring(start, charStep), htmlToken.HTML_TAG);
              if(spitNextTag) {
                if(JConfig.queryConfiguration("show.badhtml", "false").equals("true")) {
                  ErrorManagement.logDebug("Added 'bad' tag: <" + trueBuffer.substring(start, charStep) + ">");
                }
                spitNextTag = false;
              }
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
          // end Content and start Tag
          if(start != charStep) {
            String whatToAdd = trueBuffer.substring(start, charStep);
            String trimmed = whatToAdd.trim();

            if(!trimmed.equals("")) {
              addToken(whatToAdd, htmlToken.HTML_CONTENT);
            }
          }

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
    return(checkTag.charAt(0) == '/');
  }

  //  Right now this 'fakes' it, by checking for the XMLish extension to
  //  HTML, which places a '/' at the end of singleton tags.
  //  TODO: This should be replaced with a lookup into a table of singleton tags.
  //
  private boolean isSingletonTag(String checkTag) {
    return(checkTag.charAt(checkTag.length()-1)=='/');
  }

  private String clearBlankSpaceEscaped(String cleanupString) {
    String workingString = cleanupString;

    int i = workingString.indexOf("&nbsp;");
    while(i != -1) {
      workingString = workingString.substring(0, i) + workingString.substring(i+6);

      i = workingString.indexOf("&nbsp;");
    }

    return workingString;
  }

  //  Strip whitespace, including 'faked' whitespace (&nbsp;) from both sides of the provided string,
  //  and faked whitespace from the inside of the string.
  private String stripWhitespace(String cleanupString) {
    String resultString = clearBlankSpaceEscaped(cleanupString);

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
