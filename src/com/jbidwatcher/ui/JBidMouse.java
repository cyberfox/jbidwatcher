package com.jbidwatcher.ui;//  -*- Java -*-
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.awt.*;
import java.awt.event.*;
import java.awt.datatransfer.*;
import java.io.*;
import javax.swing.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import com.jbidwatcher.util.config.*;
import com.jbidwatcher.util.config.ErrorManagement;
import com.jbidwatcher.ui.config.JConfigFrame;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.ui.util.OptionUI;
import com.jbidwatcher.util.db.ActiveRecordCache;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.AuctionQObject;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.xml.XMLElement;
import com.jbidwatcher.util.html.JHTMLOutput;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.util.*;
import com.jbidwatcher.util.Currency;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.auction.*;
import com.jbidwatcher.auction.server.AuctionServerManager;
import com.jbidwatcher.auction.server.AuctionServer;

public class JBidMouse extends JBidContext implements MessageQueue.Listener {
  private static JConfigFrame jcf = null;
  private static SearchFrame _searchFrame = null;
  private JDropListener _jdl = new JDropListener(null); //  This would fail miserably if we called drop()...
  private OptionUI _oui = new OptionUI();
  private RSSDialog _rssDialog = null;
  private static StringBuffer _colorHelp = null;
  private static StringBuffer _aboutText = null;
  private static StringBuffer _faqText = null;

  private boolean _in_deleting = false;
  private JMenu tabMenu = null;
  private ScriptManager mScriptFrame;

  public JBidMouse(JPopupMenu inPopup) {
    super(inPopup);
    buildMenu(inPopup);
  }

  public JBidMouse() {
    buildMenu(localPopup);
    MQFactory.getConcrete("user").registerListener(this);
  }

  public static void setConfigFrame(JConfigFrame curCfg) {
    if(curCfg != null) jcf = curCfg;
  }

  public static final String ADD_AUCTION="ADD ";
  private static final String GET_SERVER_TIME="GETTIME";
  private static final String SEARCH="SEARCH";

  private AuctionEntry addAuction(String auctionSource) {
    AuctionEntry aeNew = AuctionsManager.getInstance().newAuctionEntry(auctionSource);
    if(aeNew != null && aeNew.isLoaded()) {
      aeNew.clearNeedsUpdate();
      AuctionsManager.getInstance().addEntry(aeNew);
      MQFactory.getConcrete("Swing").enqueue("Added [ " + aeNew.getTitle() + " ]");
      return aeNew;
    } else {
      if(aeNew != null) AuctionServerManager.getInstance().deleteEntry(aeNew);
      return null;
    }
  }

  public void messageAction(Object deQ) {
    String commandStr = (String) deQ;

    if(commandStr.startsWith(ADD_AUCTION)) {
      String auctionSource = commandStr.substring(ADD_AUCTION.length());

      cmdAddAuction(auctionSource);
    } else if(commandStr.equals(GET_SERVER_TIME)) {
      /**
       * Resynchronize with the server's 'official' time, so as to make sure
       * not to miss a snipe, for example.  This does NOT happen inline with
       * the call, it's queued to happen later because otherwise it could
       * impact interactivity.
       */
      if (JConfig.queryConfiguration("timesync.enabled", "true").equals("true")) {
        MQFactory.getConcrete("auction_manager").enqueue("TIMECHECK");
      }
    } else if(commandStr.equals(SEARCH)) {
      DoSearch();
    } else if(commandStr.equals("About " + Constants.PROGRAM_NAME)) {
      DoAbout();
    } else if(commandStr.equals("FAQ")) {
      DoFAQ();
    } else if(commandStr.equals("Configure")) {
      DoConfigure();
    }
  }

  private void cmdAddAuction(String auctionSource) {
    if(auctionSource.regionMatches(true, 0, "<html>", 0, 6)) {
      auctionSource = JHTML.getFirstContent(auctionSource);
    }

    auctionSource = auctionSource.trim();

    AuctionEntry aeNew = addAuction(auctionSource);
    if(aeNew == null) {
      AuctionsManager am = AuctionsManager.getInstance();
      String id = AuctionsManager.stripId(auctionSource);
      //  For user-interactive adds, always override the deleted state.
      if(DeletedEntry.exists(id)) {
        am.undelete(id);
        aeNew = addAuction(auctionSource);
      }
      if(aeNew == null) {
        if(am.verifyEntry(id)) {
          MQFactory.getConcrete("Swing").enqueue("ERROR " + "Cannot add auction " + auctionSource + ", it is already in your auction list.");
        } else {
          MQFactory.getConcrete("Swing").enqueue("ERROR " + "Cannot add auction " + auctionSource + ", either invalid or\ncommunication error talking to server.");
        }
      }
    }
  }

  protected boolean confirmDeletion(Component src, String prompt) {
    int endResult = JOptionPane.showOptionDialog(src, prompt,
        "Confirm", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
        null, null, null);

    return !(endResult == JOptionPane.CANCEL_OPTION ||
            endResult == JOptionPane.CLOSED_OPTION);

  }

  private void DoHideShowToolbar() {
    MQFactory.getConcrete("Swing").enqueue("TOOLBAR");
  }

  private void DoSearch() {
    if(_searchFrame == null) {
      _searchFrame = new SearchFrame();
    } else {
      _searchFrame.show();
    }
  }

  private void DoScripting() {
    if(JConfig.scriptingEnabled()) {
      if (mScriptFrame == null) mScriptFrame = new ScriptManager();
      MQFactory.getConcrete("scripting").enqueue("SHOW");
    } else {
      //  Warn the user that scripting is not enabled.
    }
  }

  private void DoShowLastError(Component src, AuctionEntry passedAE) {
    AuctionEntry ae = passedAE;
    int[] rowList = getPossibleRows();

    if(ae == null && rowList.length == 0 || rowList.length > 1) {
      JOptionPane.showMessageDialog(src, "You must select a single auction to view the error page for.",
                                    "Error view", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(ae == null) ae = (AuctionEntry) getIndexedEntry(rowList[0]);

    StringBuffer wholeStatus = ae.getErrorPage();
    Dimension statusBox = new Dimension(756, 444);

    _oui.showTextDisplay(new JHTMLOutput("Error Page", wholeStatus).getStringBuffer(), statusBox, "Error Page...");
  }

  /**
   * @brief Display a list of the items to be deleted, and let the
   * user choose whether to delete them or not.
   *
   * @param src - The component that the items came from.
   * @param passedAE - The specific item, if one in particular was chosen.
   */
  private void DoDelete(Component src, AuctionEntry passedAE) {
    AuctionEntry ae = passedAE;
    if(_in_deleting) return;

    _in_deleting = true;

    ArrayList<AuctionEntry> deleteIds = new ArrayList<AuctionEntry>();
    StringBuffer wholeDelete = new StringBuffer();
    int[] rowList = getPossibleRows();

    if(ae == null && rowList.length == 0) {
      _in_deleting = false;
      JOptionPane.showMessageDialog(src, "You must select an auction to delete.", "Delete error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    Dimension statusBox;
    if(rowList.length != 0 && rowList.length != 1) {
      wholeDelete.append("<table border=0 spacing=0 width=\"100%\">");
      wholeDelete.append("<tr><td><u><b>Item Number</b></u></td><td><u><b>Title</b></u></td></tr>");
      for(int i = 0; i<rowList.length; i++) {
        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(rowList[i]);
        deleteIds.add(tempEntry);
        String color = "FFFFFF";
        if( (i % 2) == 1) {
          color = "C0C0C0";
        }
        wholeDelete.append("<tr><td bgcolor=#").
                    append(color).
                    append("><b>").
                    append(tempEntry.getIdentifier()).
                    append("</b></td><td bgcolor=#").
                    append(color).
                    append('>').
                    append(tempEntry.getTitle()).
                    append("</td></tr>");
      }
      wholeDelete.append("</table>");
      //  This is magic, but the idea is that a line is roughly 30
      //  pixels, the heading adds 30, but we don't want it to get
      //  larger than 756x372, so as to keep 800x600 users capable of
      //  reading it.
      statusBox = new Dimension(756, Math.min(372, rowList.length * 30 + 30));
    } else {
      if(rowList.length == 1) {
        ae = (AuctionEntry) getIndexedEntry(rowList[0]);
      }
      if ((ae == null)) {
        throw new IllegalArgumentException();
      }
      wholeDelete.append("<table border=0 spacing=0 width=\"100%\"><tr><td><b>").
                  append(ae.getIdentifier()).
                  append("</b></td><td>").
                  append(ae.getTitle()).
                  append("</td></tr></table>");
      deleteIds.add(ae);
      statusBox = new Dimension(756, 45);
    }

    if(JConfig.queryConfiguration("prompt.hide_delete_confirm", "false").equals("true")) {
      for (AuctionEntry deleteId : deleteIds) {
        AuctionsManager.getInstance().delEntry(deleteId);
      }
    } else {
      List<String> buttons = new ArrayList<String>();
      buttons.add("TEXT Are you sure you want to delete these auctions: ");
      buttons.add("Yes");
      buttons.add("TEXT    ");
      buttons.add("No, Cancel");
      buttons.add("CHECK Don't prompt in the future.");

      MyActionListener al = new MyActionListener() {
        boolean m_dontprompt = false;
        public void actionPerformed(ActionEvent listen_ae) {
          String actionString = listen_ae.getActionCommand();
          if(actionString.equals("Don't prompt in the future.")) {
            JCheckBox jch = (JCheckBox) listen_ae.getSource();
            m_dontprompt = jch.isSelected();
          } else {
            if(actionString.equals("Yes")) {
              //  Delete all those items...
              for (AuctionEntry m_entry : m_entries) {
                m_entry.cancelSnipe(false);
                FilterManager.getInstance().deleteAuction(m_entry);
                new DeletedEntry(m_entry.getIdentifier()).saveDB();
              }
              //  Just pass the list of ids down to a low-level 'delete multiple' method.
              AuctionEntry.deleteAll(m_entries);
              if(m_dontprompt) {
                JConfig.setConfiguration("prompt.hide_delete_confirm", "true");
              }
            }
            m_within.dispose();
            m_within = null;
          }
        }
      };

      al.setEntries(deleteIds);

      JFrame newFrame = _oui.showChoiceTextDisplay(new JHTMLOutput("Deleting", wholeDelete).getStringBuffer(), statusBox, "Deleting...", buttons, "Items to delete...", al);
      al.setFrame(newFrame);
    }
    _in_deleting = false;
  }

  private void DoConfigure() {
    if(jcf == null) {
      List<JConfigTab> serverTabs = AuctionServerManager.getInstance().getServerConfigurationTabs();
      jcf = new JConfigFrame(serverTabs);
    } else {
      jcf.show();
    }
  }

  private String getClipboardString() {
    Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    Transferable t = sysClip.getContents(null);

    ErrorManagement.logDebug("Clipboard: " + sysClip.getName() + ", valid flavors: " + Arrays.toString(t.getTransferDataFlavors()));

    StringBuffer stBuff = _jdl.getTransferData(t);
    String clipString;
    if(stBuff == null) {
      try {
        clipString = (String)t.getTransferData(DataFlavor.stringFlavor);
      } catch(Exception e) {
        //  Nothing really to do here...
        clipString = null;
      }
    } else {
      clipString = stBuff.toString();
    }

    return clipString;
  }

  public static void setClipboardString(String saveString) {
    Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection t = new StringSelection(saveString);

    sysClip.setContents(t, t);
  }

  private static Pattern digitSearch = Pattern.compile("[0-9]+");

  private void DoPasteFromClipboard() {
    String auctionId = getClipboardString();
    String original = auctionId;

    if(auctionId.charAt(0) == '<') {
      auctionId = JHTML.getFirstContent(auctionId);
      if(auctionId.charAt(0) < '0' || auctionId.charAt(0) > '9') {
        Matcher digits = digitSearch.matcher(auctionId);
        if(digits.find()) auctionId = digits.group();
        if(auctionId == null) {
          ErrorManagement.logDebug("Failed to paste auction id: " + original);
        }
      }
    }

    if(auctionId != null) {
      MQFactory.getConcrete("user").enqueue(ADD_AUCTION + auctionId);
    }
  }

  private String makeUsefulString(AuctionEntry ae) {
    String result;

    if(ae.isSeller()) {
      result = ae.getHighBidder();
    } else {
      result = ae.getSeller();
    }
    result += "  " + ae.getIdentifier() + "  " + ae.getCurBid() + " (" + ae.getTitle() + ")\n";

    return result;
  }

  /**
   * @brief Copy data from the items, in a format similar to one I use
   * for tracking things in a plain text file.  Gets less use these days.
   *
   * @param src - The source component, for error messages.
   * @param ae - The AuctionEntry, in case it's just a single unselected one.
   */
  private void DoCopy(Component src, AuctionEntry ae) {
    DoCopySomething(src, ae, DO_COPY_DATA, "No auctions selected to copy!", "");
  }

  /**
   * @brief Pick and return a value from the entry that best describes
   * how much COULD be spent on it by the buyer.
   *
   * For an item not bid on, it's the current bid price.  For an item
   * the user has bid on, it's their maximum bid.  For an item the
   * user has a snipe set for, it's the maximum of their snipe bid.
   * If the item is closed, it's just the current bid price.
   *
   * @param checkEntry - The AuctionEntry to operate on.
   *
   * @return - A currency value containing either the current bid, the
   * users high bid, or the users snipe bid.
   */
  Currency getBestBidValue(AuctionEntry checkEntry) {
    return checkEntry.bestValue();
  }

  protected String sum(int rowList[]) {
    boolean approx = false, i18n = true;
    Currency accum = null;
    Currency realAccum = null;

    for (int aRowList : rowList) {
      try {
        AuctionEntry ae2 = (AuctionEntry) getIndexedEntry(aRowList);
        if (accum == null) {
          accum = ae2.getUSCurBid();
          realAccum = getBestBidValue(ae2);
        } else {
          Currency stepVal = ae2.getUSCurBid();
          accum = accum.add(stepVal);

          //  If we're still trying to do the internationalization
          //  thing, then try to keep track of the 'real' total.
          if (i18n) {
            try {
              realAccum = realAccum.add(getBestBidValue(ae2));
            } catch (Currency.CurrencyTypeException cte) {
              //  We can't handle multiple non-USD currency types, so
              //  we stop trying to do the internationalization thing.
              i18n = false;
            }
          }
        }
        if (ae2.getCurBid().getCurrencyType() != Currency.US_DOLLAR) approx = true;
      } catch (Exception e) {
        ErrorManagement.handleException("Sum currency exception!", e);
        return "<unknown>";
      }
    }

    if(accum == null) {
      return "<unknown>";
    }

    //  If we managed to do the i18n thing through it all, and we have
    //  some real values, return it.
    if(i18n && realAccum != null) {
      return realAccum.toString();
    }

    if(approx) return "Approximately " + accum.toString();

    return accum.toString();
  }

  private void DoSendTo(String tab) {
    int[] rowList = getPossibleRows();

    if(rowList.length == 0) {
      JOptionPane.showMessageDialog(null, "No auctions selected to move!", "Error moving listings", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(FilterManager.getInstance().findCategory(tab) == null) {
      JOptionPane.showMessageDialog(null, "Cannot locate that tab, something has gone wrong.\nClose and restart JBidwatcher.", "Error moving listings", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    //  Build a temporary table, because the items will vanish out of
    //  the table when we start refiltering them, and that will mess
    //  everything up.
    ArrayList<AuctionEntry> tempTable = new ArrayList<AuctionEntry>(rowList.length);
    for (int aRowList : rowList) {
      AuctionEntry moveEntry = (AuctionEntry) getIndexedEntry(aRowList);
      tempTable.add(moveEntry);
    }

    //  Now move all entries in the temporary table to the new tab.
    for (AuctionEntry moveEntry : tempTable) {
      moveEntry.setCategory(tab);
      FilterManager.getInstance().refilterAuction(moveEntry);
    }
  }

  private void DoAdd(Component src) {
    String prompt = "Enter the auction number to add";

    String endResult = promptString(src, prompt, "Adding");

    //  They closed the window or cancelled.
    if (endResult == null) return;

    endResult = endResult.trim();
    MQFactory.getConcrete("user").enqueue(ADD_AUCTION + endResult);
  }

  private String promptString(Component src, String prePrompt, String preTitle) {
    return(_oui.promptString(src, prePrompt, preTitle, ""));
  }

  private String promptString(Component src, String prePrompt, String preTitle, String preFill) {
    return(_oui.promptString(src, prePrompt, preTitle, preFill));
  }

  private String[] promptString(Component src, String prePrompt, String preTitle, String preFill, String postPrompt, String postFill) {
    return(_oui.promptString(src, prePrompt, preTitle, preFill, postPrompt, postFill));
  }

  private void CancelSnipe(Component src, AuctionEntry ae) {
    int[] rowList = getPossibleRows();
    int len = rowList.length;

    if(ae == null && len == 0) {
      JOptionPane.showMessageDialog(src, "You must select an auction to be able to cancel snipes.",
                                    "Snipe-cancel error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(len != 0) {
      for (int aRowList : rowList) {
        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(aRowList);

        tempEntry.cancelSnipe(false);
        FilterManager.getInstance().redrawEntry(tempEntry);
      }
    } else {
      ae.cancelSnipe(false);
      FilterManager.getInstance().redrawEntry(ae);
    }
  }

  private void DoShowTime(Component src, AuctionEntry ae) {
    AuctionServer as = AuctionServerManager.getInstance().getDefaultServer();
    if(ae != null) as = ae.getServer();

    String prompt = "<html><body><table>";
    prompt += "<tr><td><b>Current time:</b></td><td>" + new Date() + "</td></tr>";
    prompt += "<tr><td><b>Page load time:</td><td>" + as.getPageRequestTime() + "</td></tr>";
    prompt += "<tr><td><b>eBay time delta:</td><td>" + as.getServerTimeDelta() + "</td></tr>";
    prompt += "</table></body></html>";

    JOptionPane jop = new JOptionPane(prompt, JOptionPane.INFORMATION_MESSAGE);
    JDialog jdTime = jop.createDialog(src, "Auction Server Time Information");
    jdTime.addWindowListener(new WindowAdapter() {
        public void windowDeactivated(WindowEvent ev) {
          ev.getWindow().toFront();
        }
      });
    jdTime.setVisible(true);
  }

  private void DoInformation(Component src, AuctionEntry ae) {
    int[] rowList = getPossibleRows();

    int len = rowList.length;
    if(ae == null && len == 0) {
      JOptionPane.showMessageDialog(src, "Must select an auction to get information about.",
									     "Information error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    showComplexInformation(rowList);
  }

  private void showComplexInformation(int[] rowList) {
    StringBuffer prompt = new StringBuffer();
    for (int aRowList : rowList) {
      AuctionEntry stepAE = (AuctionEntry) getIndexedEntry(aRowList);
      prompt.append(stepAE.buildInfoHTML(false)).append("<hr>");
    }
    Dimension statusBox = new Dimension(480, Math.min(372, rowList.length * 30 + 200));
    ArrayList<String> buttons = new ArrayList<String>(2);
    buttons.add("Continue");
    MyActionListener al = new MyActionListener() {
        public void actionPerformed(ActionEvent listen_ae) {
          m_within.dispose();
          m_within = null;
        }
      };

    JFrame newFrame = _oui.showChoiceTextDisplay(new JHTMLOutput("Information", prompt).getStringBuffer(), statusBox, "Information...", buttons, "Information...", al);
    al.setFrame(newFrame);
  }

  private boolean dangerousSnipeWarning(Component src) {
    String warning =
      "Two or more of your auctions complete within the snipe time of\n" +
      "another.  What will happen is that if the first of those is bid on,\n" +
      "the second WILL be bid on as well.  This is very unlikely to be what\n" +
      "you want.  It is strongly recommended that you cancel the multisnipe\n" +
      "and check the end times of your auctions, only selecting auctions\n" +
      "which end more than your snipe timer seconds apart.";
    Object options[] = { "Cancel", "Proceed" };
    int choiceResult = JOptionPane.showOptionDialog(src, warning, "Dangerous Multisnipe", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);

    return choiceResult == 0 || choiceResult == JOptionPane.CLOSED_OPTION;
  }

  /*
   *  Brute force check all snipe times vs. all other snipe times, first.
   */
  private boolean checkIfDangerousMultiSnipe(Component src, int[] rowList, MultiSnipe ms) {
    boolean foundDangerousSnipe=false;

    for(int i = 0; i<rowList.length && !foundDangerousSnipe; i++) {
      AuctionEntry ae1 = (AuctionEntry) getIndexedEntry(rowList[i]);
      if(ms != null) {
        if(!ms.isSafeToAdd(ae1)) foundDangerousSnipe = true;
      }

      for(int j = i + 1; j<rowList.length && !foundDangerousSnipe; j++) {
        AuctionEntry ae2 = (AuctionEntry) getIndexedEntry(rowList[j]);
        if(!MultiSnipe.isSafeMultiSnipe(ae1, ae2)) {
          foundDangerousSnipe = true;
        }
      }
    }

    return foundDangerousSnipe && dangerousSnipeWarning(src);
  }

  private void DoMultiSnipe(Component src) {
    int[] rowList = getPossibleRows();
    Currency baseAllBid = Currency.NoValue();

    //  You must select multiple auctions to make this work.
    if(rowList.length == 0) {
      JOptionPane.showMessageDialog(src, "No auctions selected to set to MultiSnipe mode!", "Error setting multisnipe", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    //  Go through the list of auctions to make sure they're all the
    //  same currency, and other similar requirements.
    MultiSnipe aeMS = null;
    int i;
    for(i=0; i<rowList.length; i++) {
      AuctionEntry tempAE = (AuctionEntry) getIndexedEntry(rowList[i]);
      Currency curBid = tempAE.getCurBid();

      if(tempAE.getServer().isDefaultUser()) {
        JOptionPane.showMessageDialog(src, "One or more of your auctions to multisnipe is on a server that you have not\n" +
                                      "entered your user account information for.  Go to the the " + tempAE.getServer().getName() + " configuration tab,\n" +
                                      "and fill it out.",
                                      "No auction account error", JOptionPane.PLAIN_MESSAGE);
        return;
      }

      if(tempAE.isComplete()) {
        JOptionPane.showMessageDialog(src, "You cannot place a multi-snipe on a set of entries that include an ended auction",
                                      "Snipe error", JOptionPane.PLAIN_MESSAGE);
        return;
      }

      if(tempAE.isPrivate()) {
        JOptionPane.showMessageDialog(src, "Multisnipes cannot include private auctions.  Unfortunately,\n" +
                                      "private auctions make it impossible under certain circumstances\n" +
                                      "to know if a bid was ultimately the winner or not.  The two\n" +
                                      "potential answers are to either fail conservatively (think you\n" +
                                      "won, and cancel later multisnipes), or fail liberally (think\n" +
                                      "you lost, and allow later multisnipes).  Neither is a good\n" +
                                      "solution, so private auctions are barred from multisnipe groups.",
                                      "Private auction/multisnipe error", JOptionPane.PLAIN_MESSAGE);
        return;
      }

      Currency minBid;
      try {
        minBid = curBid.add(tempAE.getServer().getMinimumBidIncrement(curBid, tempAE.getNumBidders()));
      } catch(Currency.CurrencyTypeException cte) {
        //  Don't worry about different currencies, here, it just
        //  means the auctions are in a different currency than the
        //  server can do 'minimum next bid' calculations with.
        minBid = curBid;
      }

      try {
        if(baseAllBid.isNull()) {
          baseAllBid = curBid;
        } else if(baseAllBid.less(minBid)) {
          baseAllBid = curBid;
        }
      } catch(Currency.CurrencyTypeException cte) {
        // THIS is a failure, because it means that some of the
        // auctions selected have a different currency than each
        // other.  VERY bad.
        JOptionPane.showMessageDialog(src,
                                      "Cannot include auctions in different currencies\n" +
                                      "in a multi-snipe group.  It's a really bad idea,\n" +
                                      "because the snipe value may mean different values\n" +
                                      "in each currency.",
                                      "Error setting multisnipe", JOptionPane.PLAIN_MESSAGE);
        return;
      }
      //  IF one of the auctions we're adding is already multi-sniped,
      //  then we're adding this auction into that one's list.
      if(tempAE.isMultiSniped()) {
        if(aeMS != null && aeMS != tempAE.getMultiSnipe()) {
          //  WHOA!  Cannot add an auction to TWO multi-snipe groups
          //  at once!
          JOptionPane.showMessageDialog(src,
                                        "Cannot add auctions to two multi-snipe auctions\n" +
                                        "at once.  Cancel the snipes on one auction or set\n" +
                                        "of auctions, and then retry the action.",
                                        "Error setting multisnipe", JOptionPane.PLAIN_MESSAGE);
          return;
        }
        if(aeMS == null) {
          aeMS = tempAE.getMultiSnipe();
        }
      }
    }

    if(checkIfDangerousMultiSnipe(src, rowList, aeMS)) return;

    if(aeMS == null) {
      //  Build the snipe value prompt
      String prompt = "<html><body><table>";
      prompt += "<tr><td>Highest current bid:</td><td>" + baseAllBid + "</td></tr>";
      prompt += "<tr><td>Number of auctions selected:</td><td>" + rowList.length + "</td></tr>";
      prompt += "</table>";
      prompt += "Approximate minimum bid across all selected auctions is " + baseAllBid + "<br>";
      prompt += "<i>Do not enter any punctuation or currency symbols<br>other than the optional decimal point.</i><br>";
      prompt += "How much do you wish to snipe?</body></html>";

      SnipeDialog sd = new SnipeDialog();
      sd.clear();
      sd.setPrompt(prompt);
      sd.useQuantity(false);
      sd.pack();
      Rectangle rec = OptionUI.findCenterBounds(sd.getPreferredSize());
      sd.setLocation(rec.x, rec.y);
      sd.setVisible(true);

      if(sd.isCancelled() || sd.getAmount().length() == 0) {
        JOptionPane.showMessageDialog(src, "Establishing multi-auction snipe canceled.", "Multisnipe canceled", JOptionPane.PLAIN_MESSAGE);
        return;
      }

      String snipeAmount = sd.getAmount();
      boolean subtractShipping = sd.subtractShipping();

      //  Have the user select a text background color to identify this
      //  group of related snipes.
      Color groupColor = JColorChooser.showDialog(src, "Select a background color for this multi-snipe group", null);
      if(groupColor == null) {
        JOptionPane.showMessageDialog(src, "Establishing multi-auction snipe canceled.", "Multisnipe canceled", JOptionPane.PLAIN_MESSAGE);
        return;
      }

      //  Construct a new multisnipe entry, if one wasn't found from the
      //  list they selected...
      aeMS = new MultiSnipe(groupColor, Currency.getCurrency(baseAllBid.fullCurrencyName(), snipeAmount), subtractShipping);
      aeMS.saveDB();
    }

    for(i=0; i<rowList.length; i++) {
      AuctionEntry stepAE = (AuctionEntry)getIndexedEntry(rowList[i]);
      stepAE.setMultiSnipe(aeMS);
      FilterManager.getInstance().redrawEntry(stepAE);
    }
  }

  private String genBidSnipeHTML(AuctionEntry ae, Currency minBid) {
    String prompt = "<html><body><table>";
    prompt += "<tr><td>Title:</td><td>" + ae.getTitle() + "</td></tr>";
    prompt += "<tr><td>Current bid:</td><td>" + ae.getCurBid() + "</td></tr>";
    if(ae.getShipping() != null && !ae.getShipping().isNull()) {
      prompt += "<tr><td>Shipping:</td><td>" + ae.getShipping() + "</td></tr>";
    }
    prompt += "<tr><td>High bidder:</td><td>" + ae.getHighBidder() + "</td></tr>";
    prompt += "<tr><td>Seller:</td><td>" + ae.getSeller() + "</td></tr>";
    prompt += "</table>";
    if(minBid != null) {
      prompt += "Minimum legal bid is " + minBid + "<br>";
    } else {
      prompt += "Cannot calculate minimum legal bid for this currency.<br>";
    }
    prompt += "<i>Do not enter any punctuation or currency symbols<br>other than the optional decimal point.</i><br>";
    return(prompt);
  }

  private void DoSnipe(Component src, AuctionEntry passedAE) {
    AuctionEntry ae = passedAE;
    int[] rowList = getPossibleRows();

    if(rowList.length > 1) {
      DoMultiSnipe(src);
      return;
    }
    if(rowList.length == 1) {
      ae = (AuctionEntry) getIndexedEntry(rowList[0]);
    }
    if (ae == null) {
      JOptionPane.showMessageDialog(src, "You have not chosen an auction to snipe on!",
                                    "Snipe error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(ae.getServer().isDefaultUser()) {
      JOptionPane.showMessageDialog(src, "You cannot snipe on an auction without first entering your\n" +
                                    "user account information on the " + ae.getServer().getName() + " configuration tab.",
                                    "No auction account error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(ae.isComplete()) {
      JOptionPane.showMessageDialog(src, "You cannot place a snipe on an ended auction",
                                    "Snipe error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    Currency minimumNextBid;
    try {
      minimumNextBid = ae.getCurBid().add(ae.getServer().getMinimumBidIncrement(ae.getCurBid(), ae.getNumBidders()));
    } catch(Currency.CurrencyTypeException cte) {
      minimumNextBid = null;
    }
    String prompt = genBidSnipeHTML(ae, minimumNextBid);
    prompt += "</body></html>";

    SnipeDialog sd = new SnipeDialog();
    sd.clear();
    sd.setPrompt(prompt);
    if(ae.isDutch()) {
      sd.useQuantity(true);
    } else {
      sd.useQuantity(false);
    }
    sd.pack();
    Rectangle rec = OptionUI.findCenterBounds(sd.getPreferredSize());
    sd.setLocation(rec.x, rec.y);
    sd.setVisible(true);

    if(sd.isCancelled() || sd.getAmount().length() == 0) return;

    String snipeAmount = sd.getAmount();
    String snipeQuant = sd.getQuantity();

    try {
      Currency bidAmount = Currency.getCurrency(ae.getCurBid().fullCurrencyName(), snipeAmount);
      if(sd.subtractShipping()) {
        Currency shipping = ae.getShippingWithInsurance();
        if(shipping != null && !shipping.isNull()) {
          bidAmount = bidAmount.subtract(shipping);
        }
      }
      ae.prepareSnipe(bidAmount, Integer.parseInt(snipeQuant));
    } catch(NumberFormatException nfe) {
      JOptionPane.showMessageDialog(src, "You have entered a bad price for your snipe.\n" +
                                    snipeAmount + " is not a valid snipe.\n" +
                                    "Punctuation (other than a decimal point) and currency symbols are not legal.",
                                    "Bad snipe value", JOptionPane.PLAIN_MESSAGE);
      return;
    } catch (Currency.CurrencyTypeException e) {
      ErrorManagement.handleException("Couldn't subtract shipping from bid amount.", e);
      return;
    }

    //  if(JConfig.queryConfiguration("message.sniped", null) == null) { ... }
    FilterManager.getInstance().redrawEntry(ae);
    _oui.promptWithCheckbox(src, "Sniped for: " + ae.getSnipeAmount(), "Snipe Alert", "message.sniped", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
  }

  private void DoShipping(Component src, AuctionEntry ae) {
    if(ae == null) {
      JOptionPane.showMessageDialog(src, "You have not chosen an auction to set the shipping for!",
                                    "Shipping-set error", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    String prompt = "<html><body>" + ae.buildInfoHTML(false);
    prompt += "<br><b>How much is shipping?</b></body></html>";
    String endResult[] = promptString(src, prompt, "Shipping", null, null, null);

    //  They closed the window
    if (endResult == null || endResult[0] == null) {
      return;
    }

    Currency shippingAmount;
    try {
      if(endResult[0] != null) endResult[0] = endResult[0].replace(',', '.');
      shippingAmount = Currency.getCurrency(ae.getCurBid().fullCurrencyName(), endResult[0]);
    } catch(NumberFormatException nfe) {
      JOptionPane.showMessageDialog(src, "You have entered a bad shipping amount.\n" +
                                    endResult[0] + " is not a valid shipping cost.\n" +
                                    "Punctuation (other than a decimal point) and currency symbols are not legal.\n" +
                                    "The currency used is always the same as the auction.",
                                    "Bad shipping value", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(shippingAmount != null) {
      ae.setShipping(shippingAmount);
      FilterManager.getInstance().redrawEntry(ae);
    }
  }

  private boolean anyBiddingErrors(Component src, AuctionEntry ae) {
    if (ae == null) {
      JOptionPane.showMessageDialog(src, "You have not chosen an auction to bid on!",
                                    "Bid error", JOptionPane.PLAIN_MESSAGE);
      return true;
    }

    if(ae.getServer().isDefaultUser()) {
      JOptionPane.showMessageDialog(src, "You cannot bid on an auction without first entering your\n" +
                                    "user account information on the " + ae.getServer().getName() + " configuration tab.",
                                    "No auction account error", JOptionPane.PLAIN_MESSAGE);
      return true;
    }

    if(ae.isComplete()) {
      JOptionPane.showMessageDialog(src, "You cannot place a bid on an ended auction",
                                    "Bid error", JOptionPane.PLAIN_MESSAGE);
      return true;
    }

    return false;
  }

  private void DoBuy(Component src, AuctionEntry ae) {
    if(anyBiddingErrors(src, ae)) return;

    int endResult = _oui.promptWithCheckbox(src, "This will buy the item outright at the price of " + ae.getBuyNow() + ".\nIs this what you want?",
                                            "Buy Item", "prompt.bin_confirm");

    if(endResult != JOptionPane.CANCEL_OPTION && endResult != JOptionPane.CLOSED_OPTION) {
      MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.BID, new AuctionBuy(ae, Currency.NoValue(), 1), "none"));
    }
  }

  private void DoBid(Component src, AuctionEntry ae) {
    if(anyBiddingErrors(src, ae)) return;

    Currency minimumNextBid;
    try {
      minimumNextBid = ae.getCurBid().add(ae.getServer().getMinimumBidIncrement(ae.getCurBid(), ae.getNumBidders()));
    } catch(Currency.CurrencyTypeException cte) {
      minimumNextBid = null;
    }
    String prompt = genBidSnipeHTML(ae, minimumNextBid);
    prompt += "How much do you wish to bid?</body></html>";

    String[] endResult;
    if(minimumNextBid != null) {
      if(ae.isDutch()) {
        endResult = promptString(src, prompt, "Bidding", Float.toString((float)minimumNextBid.getValue()), "Quantity", "1");
      } else {
        endResult = promptString(src, prompt, "Bidding", Float.toString((float)minimumNextBid.getValue()), null, null);
        if(endResult != null) endResult[1] = "1";
      }
    } else {
      if(ae.isDutch()) {
        endResult = promptString(src, prompt, "Bidding", null, "Quantity", "1");
      } else {
        endResult = promptString(src, prompt, "Bidding", null, null, null);
        if(endResult != null) endResult[1] = "1";
      }
    }

    //  They closed the window
    if (endResult == null || endResult[0] == null) {
      return;
    }

    if(endResult[1] == null || endResult[1].length() == 0) endResult[1] = "1";

    Currency bidAmount;
    try {
      if(endResult[0] != null) endResult[0] = endResult[0].replace(',','.');
      bidAmount = Currency.getCurrency(ae.getCurBid().fullCurrencyName(), endResult[0]);
    } catch(NumberFormatException nfe) {
      JOptionPane.showMessageDialog(src, "You have entered a bad price for your bid.\n" +
                                    endResult[0] + " is not a valid bid.\n" +
                                    "Punctuation (other than a decimal point) and currency symbols are not legal.",
                                    "Bad bid value", JOptionPane.PLAIN_MESSAGE);
      return;
    }
    MQFactory.getConcrete("ebay").enqueue(new AuctionQObject(AuctionQObject.BID, new AuctionBid(ae, bidAmount, Integer.parseInt(endResult[1])), "none"));
  }

  private void DoShowInBrowser(Component src, AuctionEntry inAuction) {
    AuctionEntry ae = inAuction;
    int[] rowList = getPossibleRows();

    if(rowList.length != 0) {
//      Vector<String> multiAuctionIds = new Vector<String>();
//      int i;
//
//      for(i=0; i<rowList.length; i++) {
//        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(rowList[i]);
//
//        multiAuctionIds.add(tempEntry.getIdentifier());
//      }
//      TODO -- Find another way to do this...
//      JBidProxy.setItems(multiAuctionIds);

      ae = (AuctionEntry) getIndexedEntry(rowList[0]);
    } else {
      if(ae == null) {
        JOptionPane.showMessageDialog(src, "Cannot launch browser from menu, you must select an auction.", "Menu Error", JOptionPane.PLAIN_MESSAGE);
        return;
      }
    }

    showInBrowser(ae);
  }

  /**
   * @brief Show an auction entry in the browser.
   *
   * @param inEntry - The auction entry to load up and display in the users browser.
   */
  public void showInBrowser(AuctionEntry inEntry) {
    final String entryId = inEntry.getIdentifier();
    String doLocalServer = JConfig.queryConfiguration("server.enabled", "false");
    String browseTo;

    if (doLocalServer.equals("false")) {
      browseTo = inEntry.getServer().getBrowsableURLFromItem(entryId);
    } else {
      String localServerPort = JConfig.queryConfiguration("server.port", Constants.DEFAULT_SERVER_PORT_STRING);
      if (inEntry.isInvalid()) {
        browseTo = "http://localhost:" + localServerPort + "/cached_" + entryId;
      } else {
        browseTo = "http://localhost:" + localServerPort + '/' + entryId;
      }
    }

    MQFactory.getConcrete("browse").enqueue(browseTo);
  }

  private void DeleteComment(AuctionEntry ae) {
    if(ae == null) {
      ErrorManagement.logMessage("Auction selected to delete comment from is null, unexpected error!");
      return;
    }

    ae.setComment("");
    FilterManager.getInstance().redrawEntry(ae);
  }

  private void DoComment(Component src, AuctionEntry inAuction) {
    if(inAuction == null) {
      ErrorManagement.logMessage("Auction selected to comment on is null, unexpected error!");
      return;
    }

    String curComment = inAuction.getComment();
    if(curComment == null) curComment = "";
    String endResult = promptString(src, "Enter a comment for: " + inAuction.getTitle(), "Commenting", curComment);
    if(endResult == null) return;

    inAuction.setComment(endResult);
    FilterManager.getInstance().redrawEntry(inAuction);
  }

  private void ShowComment(Component src, AuctionEntry inAuction) {
    if(inAuction == null) {
      ErrorManagement.logMessage("Can't show comments from menu items yet.");
      return;
    }

    JOptionPane.showMessageDialog(src, inAuction.getComment(), "Comment", JOptionPane.PLAIN_MESSAGE);
  }

  private void DoUpdateAll() {
    Iterator<AuctionEntry> stepThrough = AuctionsManager.getAuctionIterator();

    while(stepThrough.hasNext()) {
      AuctionEntry ae = stepThrough.next();
      if(!ae.isComplete()) {
        ae.setNeedsUpdate();
      }
    }
  }

  private void DoStopUpdating(Component src) {
    int endResult = _oui.promptWithCheckbox(src, "This will terminate all searches, as well as\nall updates that are currently pending.\n\nStop all searches?", "Stop updating/searching", "prompt.search_stop");

    if(endResult != JOptionPane.CANCEL_OPTION &&
       endResult != JOptionPane.CLOSED_OPTION) {
      AuctionServerManager.getInstance().cancelSearches();

      //  Clear all dropped or programmatically added auctions.
      MQFactory.getConcrete("drop").clear();

      Iterator<AuctionEntry> stepThrough = AuctionsManager.getAuctionIterator();

      while(stepThrough.hasNext()) {
        AuctionEntry ae = stepThrough.next();
        if(!ae.isComplete()) {
          ae.clearNeedsUpdate();
          ae.pauseUpdate();
        }
      }
    }
  }

  private void DoUpdate(Component src, AuctionEntry inAuction) {
    int[] rowList = getPossibleRows();

    if(rowList.length != 0) {
      for (int aRowList : rowList) {
        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(aRowList);

        tempEntry.setNeedsUpdate();
        if (tempEntry.isComplete() || tempEntry.isPaused()) {
          tempEntry.forceUpdate();
        }
      }
    } else {
      if(inAuction == null) {
        JOptionPane.showMessageDialog(src, "No auction selected to update.", "No auction to update", JOptionPane.INFORMATION_MESSAGE);
      } else {
        inAuction.setNeedsUpdate();
        if(inAuction.isPaused()) {
          inAuction.forceUpdate();
        }
      }
    }
  }

  private void DoSetNotEnded(AuctionEntry whichAuction) {
    int[] rowList = getPossibleRows();

    if (rowList.length != 0) {
      for (int aRowList : rowList) {
        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(aRowList);

        tempEntry.setComplete(false);
        tempEntry.setNeedsUpdate();
      }
    } else {
      whichAuction.setComplete(false);
      whichAuction.setNeedsUpdate();
    }
  }

  private void DoResetServerTime() {
    //  Always resets the server time based on the 'default' server.
    MQFactory.getConcrete("user").enqueue(GET_SERVER_TIME);
  }

  private final static StringBuffer badAbout = new StringBuffer("Error loading About text!  (D'oh!)  Email <a href=\"mailto:cyberfox@users.sourceforge.net\">me</a>!");
  private final static StringBuffer badFAQ = new StringBuffer("Error loading FAQ text!  (D'oh!)  Email <a href=\"mailto:cyberfox@users.sourceforge.net\">me</a>!");

  static private JFrame aboutFrame = null;
  private void DoAbout() {
    if(aboutFrame == null) {
      Dimension aboutBoxSize = new Dimension(495, 245);

      if(_aboutText == null) {
        _aboutText = JBHelp.loadHelp("/help/about.jbh", "About " + Constants.PROGRAM_NAME + "...");
      }

      aboutFrame = _oui.showTextDisplay(_aboutText!=null?_aboutText:badAbout, aboutBoxSize, "About " + Constants.PROGRAM_NAME);
    } else {
      aboutFrame.setVisible(true);
    }
  }

  private static final StringBuffer EMPTY_LOG = new StringBuffer("The log is empty.");
  private void showLog(StringBuffer logText, String frameName) {
    Dimension logBoxSize = new Dimension(625, 500);

    if(logText == null || logText.length() == 0) {
      logText = EMPTY_LOG;
    }

    JFrame logFrame = _oui.showTextDisplay(logText, logBoxSize, Constants.PROGRAM_NAME + " " + frameName, false);
    logFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
  }

  private void DoViewLog() {
    StringBuffer logText = ErrorMonitor.getInstance().getLog();
    showLog(logText, "Log");
  }

  private void DoViewActivity() {
    StringBuffer logText = ActivityMonitor.getInstance().getLog();
    showLog(logText, "Activity Log");
  }

  private static JFrame faqFrame = null;
  private void DoFAQ() {
    if(faqFrame == null) {
      Dimension faqBoxSize = new Dimension(625, 500);

      if(_faqText == null) {
        _faqText = JBHelp.loadHelp("/help/faq.jbh", "FAQ for " + Constants.PROGRAM_NAME + "...");
      }

      faqFrame = _oui.showTextDisplay(_faqText!=null?_faqText:badFAQ, faqBoxSize, Constants.PROGRAM_NAME + " FAQ");
    } else {
      faqFrame.setVisible(true);
    }
  }

  private void DoSerialize() {
    System.out.println(AuctionServerManager.getInstance().toXML());
  }

  private void DoLoad(String fname) {
    String canonicalFName = fname;
    if(canonicalFName == null) {

      canonicalFName = JConfig.queryConfiguration("savefile", "auctions.xml");
      String oldFname = canonicalFName;

      canonicalFName = JConfig.getCanonicalFile(canonicalFName, "jbidwatcher", true);

      if(!canonicalFName.equals(oldFname)) {
        JConfig.setConfiguration("savefile", canonicalFName);
      }
    }

    try {
      XMLElement xmlFile = new XMLElement(true);

      InputStreamReader isr = new InputStreamReader(new FileInputStream(canonicalFName));

      xmlFile.parseFromReader(isr);

      AuctionServerManager.getInstance().fromXML(xmlFile);
    } catch(IOException e) {
      ErrorManagement.handleException("Error loading XML file with auctions: " + canonicalFName, e);
    }
  }

  private void DoCloseDown() {
    MQFactory.getConcrete("Swing").enqueue("QUIT");
  }

  private void DoSave(Component src) {
    boolean didSave = AuctionsManager.getInstance().saveAuctions();
    System.gc();

    if(didSave) {
//      JOptionPane.showMessageDialog(src, "Auctions Saved!", "Save Complete", JOptionPane.INFORMATION_MESSAGE);
      _oui.promptWithCheckbox(src, "Auctions saved!", "Save Complete", "prompt.savecomplete", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
    } else {
      JOptionPane.showMessageDialog(src, "An error occurred in saving the auctions!", "Save Failed", JOptionPane.INFORMATION_MESSAGE);
    }
  }

  /**
   * @brief Given an action value, and an auction entry, return the
   * right string associated with that action.
   *
   * @param action - The action we're looking to do.
   * @param ae - The entry to retrieve the data from.
   *
   * @return - A string containing the relevant data from ae.
   */
  private String getActionValue(int action, AuctionEntry ae) {
    switch(action) {
      case DO_COPY_URL:
        return ae.getServer().getBrowsableURLFromItem(ae.getIdentifier());
      case DO_COPY_ID:
        return ae.getIdentifier();
      case DO_COPY_DATA:
        return makeUsefulString(ae);
      default://  Don't do anything...
    }

    return "";
  }

  private static final int DO_COPY_URL  = 0;
  private static final int DO_COPY_ID   = 1;
  private static final int DO_COPY_DATA = 2;

  /**
   * @brief Copy some data into the clipboard, based on the action
   * parameter, seperated by the seperator parameter.
   *
   * @param src - The source component, for error messages.
   * @param passedAE - The AuctionEntry, in case it's just a single unselected one.
   * @param action - The action to perform (defined just before this function).
   * @param fail_msg - The message to display in a dialog on failure.
   * @param seperator - The string to seperate selected results with.
   */
  private void DoCopySomething(Component src, AuctionEntry passedAE, int action, String fail_msg, String seperator) {
    AuctionEntry ae = passedAE;
    int[] rowList = getPossibleRows();

    if(ae == null && rowList.length == 0) {
      JOptionPane.showMessageDialog(src, fail_msg, "Error copying", JOptionPane.PLAIN_MESSAGE);
      return;
    }

    if(rowList.length != 0 && rowList.length != 1) {
      StringBuffer sb = new StringBuffer();

      for(int i = 0; i<rowList.length; i++) {
        AuctionEntry tempEntry = (AuctionEntry) getIndexedEntry(rowList[i]);

        if(i != 0) sb.append(seperator);

        sb.append(getActionValue(action, tempEntry));
      }

      setClipboardString(sb.toString());
    } else {
      //  Shortcut to not have to create and destroy a Stringbuffer
      if(rowList.length == 1) ae = (AuctionEntry) getIndexedEntry(rowList[0]);

      setClipboardString(getActionValue(action, ae));
    }
  }

  /**
   * @brief Copy the URLs for all the selected items into the clipboard.
   *
   * @param src - The component we're on.
   * @param ae - The auction entry, if just one was selected.
   */
  private void DoCopyURL(Component src, AuctionEntry ae) {
    DoCopySomething(src, ae, DO_COPY_URL, "No auctions selected to copy URLs of!", "\n");
  }

  /**
   * @brief Copy the IDs for all the selected items into the clipboard.
   *
   * @param src - The component we're on.
   * @param ae - The auction entry, if just one was selected.
   */
  private void DoCopyID(Component src, AuctionEntry ae) {
    DoCopySomething(src, ae, DO_COPY_ID, "No auctions selected to copy IDs of!", ", ");
  }

  private void DoHelp(Component src) {
    //  Not really implemented yet...  --  BUGBUG (need to write help!)
    JOptionPane.showMessageDialog(src,
                                  "I'm very sorry, but help has not been implemented yet.\n" +
                                  "If you'd like to assist in getting help up, you could\n" +
                                  "write me an email at cyberfox@users.sourceforge.net\n" +
                                  "describing how you use a particular part of JBidwatcher,\n" +
                                  "and I'll try to collect those into contextual help options.",
                                  "Sorry, no help!", JOptionPane.INFORMATION_MESSAGE);
  }

  private final static StringBuffer badColors = new StringBuffer("Error loading Color help text!  (D'oh!)  Email <a href=\"mailto:cyberfox%40users.sourceforge.net\">me</a>!");

  private static JFrame helpFrame = null;
  private void DoHelpColors() {
    if(helpFrame == null) {
      Dimension chSize = new Dimension(495, 245);

      if(_colorHelp == null) {
        _colorHelp = JBHelp.loadHelp("/help/colors.jbh", "Help on Colors");
      }

      helpFrame = _oui.showTextDisplay(_colorHelp!=null?_colorHelp:badColors, chSize, "Help on color use in JBidwatcher");
    } else {
      helpFrame.setVisible(true);
    }
  }

  private void DoRSS() {
    if(_rssDialog == null) {
      _rssDialog = new RSSDialog();
    }

    _rssDialog.prepare();
    _rssDialog.pack();
    _rssDialog.setVisible(true);
  }

  protected void DoCheckUpdates() {
    // Force the 'last known version' to be the current, so that users can check
    // later, and have it still find the new version.
    JConfig.setConfiguration("updates.last_version", Constants.PROGRAM_VERS);
    MQFactory.getConcrete("update").enqueue(Boolean.TRUE);
  }

  protected void DoSetBackgroundColor(Component src) {
    Color bgColor = JColorChooser.showDialog(src, "Select a background color for your auction tables", null);
    if(bgColor == null) {
      return;
    }
    FilterManager.getInstance().setBackground(bgColor);
    myTableCellRenderer.resetBehavior();
    JConfig.setConfiguration("background", MultiSnipe.makeRGB(bgColor));
  }

  private void DoClearDeleted(Component src) {
    int clearedCount = AuctionsManager.getInstance().clearDeleted();

    _oui.promptWithCheckbox(src, "Cleared " + clearedCount + " deleted entries.", "Clear Complete", "prompt.clear_complete", JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_OPTION);
  }

  protected void buildMenu(JPopupMenu menu) {
    menu.add(makeMenuItem("Snipe")).addActionListener(this);
    menu.add(makeMenuItem("Cancel Snipe")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());

    menu.add(makeMenuItem("Bid")).addActionListener(this);
    menu.add(makeMenuItem("Buy")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());

    menu.add(makeMenuItem("Update Auction", "Update")).addActionListener(this);
    menu.add(makeMenuItem("Show Information", "Information")).addActionListener(this);
    menu.add(makeMenuItem("Show In Browser", "Browse")).addActionListener(this);
    //menu.add(makeMenuItem("Add Up Prices", "Sum")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());
    menu.add(makeMenuItem("Set Shipping", "Shipping")).addActionListener(this);
    menu.add(new JPopupMenu.Separator());

    tabMenu = new JMenu("Send To");
    menu.add(tabMenu);
    JMenu comment = new JMenu("Comment");
    comment.add(makeMenuItem("Add", "Add Comment")).addActionListener(this);
    comment.add(makeMenuItem("View", "View Comment")).addActionListener(this);
    comment.add(makeMenuItem("Remove", "Remove Comment")).addActionListener(this);
    menu.add(comment);
    JMenu advanced = new JMenu("Advanced");
    advanced.add(makeMenuItem("Show Last Error", "ShowError")).addActionListener(this);
    advanced.add(makeMenuItem("Mark As Not Ended", "NotEnded")).addActionListener(this);
    menu.add(advanced);
    menu.add(new JPopupMenu.Separator());

    menu.add(makeMenuItem("Delete")).addActionListener(this);
  }

  protected void beforePopup(JPopupMenu jp, MouseEvent e) {
    ActionListener tabActions = new ActionListener() {
        public void actionPerformed(ActionEvent action) {
          String toTab = action.getActionCommand();
          DoSendTo(toTab);
        }
      };
    super.beforePopup(jp, e);

    if(tabMenu != null) {
      tabMenu.removeAll();

      JTabbedPane tabbedPane = AuctionsUIModel.getTabManager().getTabs();
      String currentTitle = tabbedPane.getTitleAt(tabbedPane.getSelectedIndex());
      List<String> tabs = FilterManager.getInstance().allCategories();
      if(tabs == null) {
        tabMenu.setEnabled(false);
      } else {
        tabs.remove("selling");
        tabs.remove(currentTitle);
        tabMenu.setEnabled(true);
        for (String tab : tabs) {
          tabMenu.add(makeMenuItem(tab)).addActionListener(tabActions);
        }
      }
    }

    /**
     * This sucks.  I need to push the generic code up, and leave the auction-specific code here, or
     * somehow move all the non-auction-specific functionality to its own class.  This code is broken,
     * at the least because it has to use 'instanceof' to work.
     */
    Object resolvedObject = resolvePoint();
    AuctionEntry ae = null;

    if(resolvedObject != null && resolvedObject instanceof AuctionEntry) {
      ae = (AuctionEntry) resolvedObject;
    }

    int[] rowList = getPossibleRows();

    if(rowList != null && rowList.length != 0) {
      if(rowList.length == 1) {
        Object firstSelected = getIndexedEntry(rowList[0]);
        if(firstSelected != null && firstSelected instanceof AuctionEntry) {
          ae = (AuctionEntry) firstSelected;
        }
      } else {
        ae = null;
      }
    }

    //  Ignored if it wasn't renamed, but otherwise always restore to 'known state'.
    rename("Multisnipe", "Snipe");
    rename("Edit", "Add");               // Comment

    if(ae != null) {
      if(ae.getComment() == null) {
        disable("View");
        disable("Remove");
      } else {
        rename("Add", "Edit");
      }
      if(!ae.isSniped()) disable("Cancel Snipe");
      if(!ae.isComplete()) {
        disable("Complete");
        disable("Mark As Not Ended");
      } else {
        enable("Mark As Not Ended");
      }

      if(ae.isSeller() || ae.isComplete()) {
        disable("Buy");
        disable("Bid");
        disable("Snipe");
      }

      if(ae.isFixed()) {
        disable("Bid");
        disable("Snipe");
      }

      if(!ae.isFixed() && ae.getBuyNow().isNull()) {
        disable("Buy");
      }
    }

    if(rowList != null && rowList.length > 1) {
      disable("Bid");
      disable("Buy");
      disable("Show Last Error");
      disable("Set Shipping");
      disable("Add");
      disable("View");
      disable("Remove");

      boolean anySniped = false;
      boolean anyFixed = false;
      boolean anyEnded = false;
      boolean anyCurrent = false;
      for (int aRowList : rowList) {
        Object line = getIndexedEntry(aRowList);
        AuctionEntry step = (AuctionEntry) line;
        if (step.isSniped()) anySniped = true;
        if (step.isFixed()) anyFixed = true;
        if (step.isComplete()) anyEnded = true;
        if (!step.isComplete()) anyCurrent = true;
      }

      if(!anySniped) disable("Cancel Snipe");
      if(anyFixed || anyEnded) disable("Snipe");
      if(!anyCurrent) enable("Complete");
      if(anyEnded) enable("Mark As Not Ended"); else disable("Mark As Not Ended");
      rename("Snipe", "Multisnipe");
    }

    if(ae == null || ae.getErrorPage() == null) {
      disable("Show Last Error");
    }
  }

  protected void DoAction(Object src, String actionString, Object whichAuction) {
    DoAction(src, actionString, (AuctionEntry)whichAuction);
  }

  protected void DoAction(Object src, String actionString, AuctionEntry whichAuction) {
    Component c_src;

    if(src instanceof Component) {
      c_src = (Component)src;
    } else {
      c_src = null;
    }
    if(actionString.equals("Save")) DoSave(c_src);
    else if(actionString.equals("Load")) DoLoad(null);
    else if(actionString.equals("Clear Deleted")) DoClearDeleted(c_src);
    else if(actionString.equals("Configure")) DoConfigure();
    else if(actionString.equals("Check Updates")) DoCheckUpdates();
    else if(actionString.equals("Check For Updates")) DoCheckUpdates();
    else if(actionString.equals("Exit")) DoCloseDown();
    else if(actionString.equals("Help")) DoHelp(c_src);
    else if(actionString.equals("Explain Colors And Icons")) DoHelpColors();
    else if(actionString.equals("RSS")) DoRSS();

    else if(actionString.equals("Serialize")) DoSerialize();

    else if(actionString.equals("Paste")) DoPasteFromClipboard();
    else if(actionString.equals("CopyURL")) DoCopyURL(c_src, whichAuction);
    else if(actionString.equals("CopyID")) DoCopyID(c_src, whichAuction);
    else if(actionString.equals("Add")) DoAdd(c_src);
    else if(actionString.equals("Delete")) DoDelete(c_src, whichAuction);

    else if(actionString.equals("UpdateAll")) DoUpdateAll();
    else if(actionString.equals("StopUpdating")) DoStopUpdating(c_src);

    else if(actionString.equals("Resync")) DoResetServerTime();

    else if(actionString.equals("Information")) DoInformation(c_src, whichAuction);
    else if(actionString.equals("Update")) DoUpdate(c_src, whichAuction);
    else if(actionString.equals("Browse")) DoShowInBrowser(c_src, whichAuction);
//    else if(actionString.equals("Status")) DoShowStatus(c_src, whichAuction);
    else if(actionString.equals("Show Time Info")) DoShowTime(c_src, whichAuction);
    else if(actionString.equals("ShowError")) DoShowLastError(c_src, whichAuction);
    else if(actionString.equals("Bid")) DoBid(c_src, whichAuction);
    else if(actionString.equals("Buy")) DoBuy(c_src, whichAuction);
    else if(actionString.equals("Shipping")) DoShipping(c_src, whichAuction);
    else if(actionString.equals("NotEnded")) DoSetNotEnded(whichAuction);
    else if(actionString.equals("Snipe")) DoSnipe(c_src, whichAuction);
    else if(actionString.equals("Multiple Snipe")) DoMultiSnipe(c_src);

    else if(actionString.equals("About " + Constants.PROGRAM_NAME)) DoAbout();
    else if(actionString.equals("About")) DoAbout();
    else if(actionString.equals("FAQ")) DoFAQ();

    else if(actionString.equals("Cancel Snipe")) CancelSnipe(c_src, whichAuction);
    else if(actionString.equals("Add Comment")) DoComment(c_src, whichAuction);
    else if(actionString.equals("View Comment")) ShowComment(c_src, whichAuction);
    else if(actionString.equals("Remove Comment")) DeleteComment(whichAuction);
    else if(actionString.equals("Copy")) DoCopy(c_src, whichAuction);
    else if(actionString.equals("Set Background Color")) DoSetBackgroundColor(c_src);
    else if(actionString.equals("Toolbar")) DoHideShowToolbar();
    else if(actionString.equals("Search")) DoSearch();
    else if(actionString.equals("Scripting")) DoScripting();
    else if(actionString.equals("Dump")) ActiveRecordCache.saveCached();
    else if(actionString.equals("Forum")) MQFactory.getConcrete("browse").enqueue("http://forum.jbidwatcher.com");
    else if(actionString.equals("View Log")) DoViewLog();
    else if(actionString.equals("View Activity")) DoViewActivity();
    else if(actionString.equals("Report Bug")) MQFactory.getConcrete("browse").enqueue("http://jbidwatcher.lighthouseapp.com/projects/8037-jbidwatcher/tickets");
    else ErrorManagement.logDebug('[' + actionString + ']');
  }
}
