package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.platform.Platform;
import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.Constants;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.ui.table.SearchTableModel;
import com.jbidwatcher.ui.table.TableSorter;
import com.jbidwatcher.ui.util.JBidFrame;

import java.awt.event.*;
import javax.swing.*;
import java.awt.*;

public class SearchFrame implements ActionListener {
  JFrame mainFrame;
  JComboBox newType;
  JTextField searchString;
  SearchTableModel _stm;
  TableSorter _ts;

  public SearchFrame() {
    mainFrame = createSearchFrame();
    Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();
    int height = Math.min(305, screensize.height / 2);
    int width = Math.min(566, screensize.width / 2);
    int screenx = (screensize.width - width) / 2;
    int screeny = (screensize.height - height) / 2;

    String s_x = JConfig.queryDisplayProperty("searches.x");
    String s_y = JConfig.queryDisplayProperty("searches.y");
    String s_w = JConfig.queryDisplayProperty("searches.width");
    String s_h = JConfig.queryDisplayProperty("searches.height");

    try { if(s_x != null) screenx = Integer.parseInt(s_x); } catch(NumberFormatException ignored) { /* Do nothing. */ }
    try { if(s_x != null) if(s_y != null) screeny = Integer.parseInt(s_y); } catch(NumberFormatException ignored) { /* Do nothing. */ }
    try { if(s_x != null) if(s_w != null) width = Integer.parseInt(s_w); } catch(NumberFormatException ignored) { /* Do nothing. */ }
    try { if(s_x != null) if(s_h != null) height = Integer.parseInt(s_h); } catch(NumberFormatException ignored) { /* Do nothing. */ }

    mainFrame.setLocation(screenx, screeny);
    mainFrame.setSize(width, height);

    mainFrame.setVisible(true);
  }

  private void savePosition() {
    JConfig.setAuxConfiguration("searches.x", Integer.toString(mainFrame.getX()));
    JConfig.setAuxConfiguration("searches.y", Integer.toString(mainFrame.getY()));
    JConfig.setAuxConfiguration("searches.width", Integer.toString(mainFrame.getWidth()));
    JConfig.setAuxConfiguration("searches.height", Integer.toString(mainFrame.getHeight()));
  }

  public JFrame createSearchFrame() {
    JPanel wholePanel = new JPanel(new BorderLayout(), true);
    JPanel subPanel = new JPanel(new BorderLayout(), true);
    JPanel buttonPanel = new JPanel(new BorderLayout(), true);
    Box buttonBox = Box.createHorizontalBox();
    JSearchContext jsc = new JSearchContext();

    final JFrame w = new JBidFrame("Search Manager");

    Container contentPane = w.getContentPane();
    contentPane.setLayout(new BorderLayout());

    wholePanel.setBorder(BorderFactory.createTitledBorder("Saved Searches"));
    wholePanel.add(buildSearchTable(jsc), BorderLayout.CENTER);

    //subPanel.setBorder(BorderFactory.createTitledBorder("Add & Execute New Search"));
    //subPanel.add(buildAdditionalPanel(), BorderLayout.SOUTH);

    buttonBox.add(jsc.makeButton("Search", "Execute"));
    buttonBox.add(jsc.makeButton("New"));
    buttonBox.add(jsc.makeButton("Edit", "Edit Search"));
    buttonBox.add(jsc.makeButton("Enable"));
    buttonBox.add(jsc.makeButton("Disable"));
    buttonBox.add(Box.createHorizontalGlue());
    buttonBox.add(jsc.makeButton("Delete"));

    buttonPanel.setBorder(BorderFactory.createLoweredBevelBorder());
    buttonPanel.add(buttonBox, BorderLayout.SOUTH);

    wholePanel.add(buttonPanel, BorderLayout.SOUTH);

    contentPane.add(wholePanel, BorderLayout.CENTER);
    contentPane.add(subPanel, BorderLayout.SOUTH);
    w.pack();
    w.setResizable(true);
    w.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);

    w.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent we) {
        savePosition();
        SearchManager.getInstance().saveSearches();
      }

      public void windowIconified(WindowEvent we) {
        savePosition();
        if(Platform.isWindows() && Platform.isTrayEnabled()) {
          if(JConfig.queryConfiguration("windows.tray", "true").equals("true") &&
             JConfig.queryConfiguration("windows.minimize", "true").equals("true")) {
            w.setVisible(false);
          }
        }
      }

      public void windowDeiconified(WindowEvent we) {
        if(Platform.isWindows() && Platform.isTrayEnabled()) {
          if(JConfig.queryConfiguration("windows.tray", "true").equals("true") &&
             JConfig.queryConfiguration("windows.minimize", "true").equals("true")) {
            w.setState(JFrame.NORMAL);
            w.setVisible(true);
          }
        }
      }
    });

    //  Handle escape key to close the dialog
    KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false);
    Action escapeAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        savePosition();
        w.setVisible(false);
      }
    };
    w.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, "ESCAPE");
    w.getRootPane().getActionMap().put("ESCAPE", escapeAction);
    return w;
  }

  private JScrollPane buildSearchTable(JSearchContext jsc) {
    _stm = new SearchTableModel();
    _ts = new TableSorter("search", "Name", _stm);
    JTable searchTable = new JTable(_ts);
    searchTable.addMouseListener(jsc);
    searchTable.setShowGrid(false);
    searchTable.setIntercellSpacing(new Dimension(0, 0));
    searchTable.setDoubleBuffered(true);
    searchTable.setShowHorizontalLines(true);
    searchTable.setToolTipText("Double-click on a search to execute it!");
    searchTable.getTableHeader().setReorderingAllowed(false);
    _ts.addMouseListenerToHeaderInTable(searchTable);
    JScrollPane jsp = new JScrollPane(searchTable, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    jsp.getViewport().setBackground(UIManager.getColor("window"));

    jsc.setTable(searchTable);

    return jsp;
  }

  String[] search_types = {
    "Text Search",
    "Seller Search",
    "URL Load",
    "My Items" };

  public void show() {
    mainFrame.setState(JFrame.NORMAL);
    mainFrame.setVisible(true);
  }

  private Searcher add(String type, String name, String search, String server) {
    int inc=0;
    String curName = name + Integer.toString(inc);
    SearchManager sm = SearchManager.getInstance();
    Searcher s;

    s = sm.buildSearch(System.currentTimeMillis(), type, curName, search, server, null, -1);
    _ts.insert(s);

    return s;
  }

  public void actionPerformed(ActionEvent ae) {
    String act = ae.getActionCommand();

    if(act.equals("Add") || act.equals("Search")) {
      String text = searchString.getText();
      Searcher s = add((String)newType.getSelectedItem(), "New Search ", text, Constants.EBAY_SERVER_NAME);

      _stm.fireTableDataChanged();
      newType.setSelectedIndex(0);
      searchString.setText("");

      if(act.equals("Search")) {
        if(JConfig.debugging) System.out.println("Doing a " + newType.getSelectedItem() + " for " + text);
        s.execute();
      }
    }
  }
}
