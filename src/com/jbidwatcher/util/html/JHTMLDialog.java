package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*!@class JHTMLDialog
 *
 * @brief A class to simplify constructing an HTML dialog to prompt
 * the user for actions (sniping/bidding).
 *
 */

public class JHTMLDialog {
  protected static final String FORM_ACTION = "action";
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  //  These allow me to create HTML forms quickly.
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  public final String form(String in_name, String in_action, String in_method, String in_text) {
    return("<form name=\"" + in_name + "\" action=\"" + in_action + "\" method=\"" + in_method + "\">"+in_text+"</form>\n");
  }

  public final String table(String in_text) {
    return("<table border=1 cellspacing=1 cellpadding=0>\n" + in_text + "</table>\n");
  }

  public final String table0(String in_text) {
    return("<table border=0>\n" + in_text + "</table>\n");
  }

  public final String tr(String in_text) {
    return("<tr>" + in_text + "</tr>\n");
  }

  public final String td(String in_text) {
    return("<td>" + in_text + "</td>");
  }

  public final String button(String in_value, String in_onclick) {
    return("<input type=\"button\" value=\"" + in_value + "\" onClick=\"" + in_onclick +"\">");
  }

  public final String submit_button(String in_name, String in_value, String in_onclick) {
    return("<input type=\"submit\" name=\"" + in_name + "\" value=\"" + in_value + "\" onClick=\"" + in_onclick +"\">");
  }

  public final String inputString(String in_name, int in_size, String in_value) {
    return("<input name=\"" + in_name + "\" size=" + in_size + " value=\"" + in_value + "\">");
  }

  public final String hidden(String in_name, String in_value) {
    return("<input type=\"hidden\" name=\"" + in_name + "\" value =\"" + in_value + "\">");
  }

  public final String inputPassword(String in_name, String in_size) {
    return("<input type=\"password\" name=\"" + in_name + "\" size=" + in_size + ">");
  }

  public final String radio(String in_name, String in_value, String in_check, String in_text, String in_item) {
    String retStr = "<input type=\"radio\" name=\"" + in_name + "\" value=\"" + in_value + "\" ";

    if(in_check.equals("1")) retStr += "CHECKED"; else retStr += "UNCHECKED onclick=\"return set_complete(event, " + in_item + ");\"";

    retStr += ">" + in_text;

    return retStr;
  }

  private String _currentPage;

  public JHTMLDialog(String formName, String urlText, String reqType,
                     String actionName, String dataHeader,
                     String dataName, int dataLength, String dataValue) {
    _currentPage = form(formName, urlText, reqType, table0(tr(td(dataHeader) + td(inputString(dataName, dataLength, dataValue)) + td(submit_button(FORM_ACTION, actionName, "")))));
  }

  public JHTMLDialog(String formName, String urlText, String reqType,
                     String cookieName, String cookieData,
                     String actionName, String dataHeader,
                     String dataName, int dataLength, String dataValue)
  {
    _currentPage = table(tr(td(form(formName, urlText, reqType,
                                    hidden(cookieName, cookieData) +
                                    table0(tr(td(table0(tr(td(submit_button(FORM_ACTION, actionName, "")))))) +
                                           tr(td(table0(tr(td(dataHeader))))) +
                                           tr(td(inputString(dataName, dataLength, dataValue))))))));
  }

  public String toString() { return _currentPage; }
}
