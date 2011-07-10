package com.jbidwatcher.ui.commands;

import com.jbidwatcher.ui.JBHelp;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.util.Constants;

import javax.swing.*;
import java.awt.Dimension;

/**
* User: mrs
* Date: Jun 23, 2010
* Time: 12:43:00 PM
* Command to display the Frequently Asked Questions dialog.
*/
public class FAQCommand extends AbstractCommand {
  protected String getCommand() { return "FAQ"; }

  private final static StringBuffer badFAQ = new StringBuffer("Error loading FAQ text!  (D'oh!)  Email <a href=\"mailto:cyberfox@jbidwatcher.com\">me</a>!");
  private static StringBuffer _faqText = null;
  private static JFrame faqFrame = null;
  private OptionUI _oui = new OptionUI();

  public void execute() {
    if (faqFrame == null) {
      Dimension faqBoxSize = new Dimension(625, 500);

      if (_faqText == null) {
        _faqText = JBHelp.loadHelp("/help/faq.jbh", "FAQ for " + Constants.PROGRAM_NAME + "...");
      }

      faqFrame = _oui.showHTMLDisplay(_faqText != null ? _faqText : badFAQ, faqBoxSize, Constants.PROGRAM_NAME + " FAQ");
    } else {
      faqFrame.setVisible(true);
    }
  }
}
