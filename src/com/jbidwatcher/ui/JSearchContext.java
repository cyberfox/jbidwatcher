package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.Constants;
import com.jbidwatcher.search.SearchManager;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class JSearchContext extends JBidMouse {
  private static SearchInfoDialog _searchDetail = null;

  private void addMenu(JPopupMenu p, String name, String cmd) {
    p.add(makeMenuItem(name, cmd)).addActionListener(this);
  }

  private void addMenu(JPopupMenu p, String name) {
    p.add(makeMenuItem(name)).addActionListener(this);
  }

  protected void internalDoubleClick(MouseEvent e) {
    if(!(e.getComponent() instanceof JComponent)) return;
    JComponent inComponent = (JComponent) e.getComponent();

    if(inComponent instanceof JTable) {
      JTable thisTable = (JTable) inComponent;
      int rowPoint = thisTable.rowAtPoint(new Point(e.getX(), e.getY()));
      Searcher whichSearch = (Searcher) thisTable.getValueAt(rowPoint, -1);

      whichSearch.execute();
    }
  }

  public void actionPerformed(ActionEvent ae) {
    String actionString = ae.getActionCommand();
    Searcher whichSearch = null;
    if(actionString.startsWith(Constants.NO_CONTEXT)) {
      actionString = actionString.substring(Constants.NO_CONTEXT.length());
    } else {
      if(_inTable != null) {
        int rowPoint = _inTable.rowAtPoint(new Point(getPopupX(), getPopupY()));
        whichSearch = (Searcher)_inTable.getValueAt(rowPoint, -1);
      }
    }

    DoAction(actionString, whichSearch);
  }

  protected final JPopupMenu constructTablePopup() {
    JPopupMenu pop = new JPopupMenu();

    addMenu(pop, "Do Search", "Execute");
    addMenu(pop, "Load Searches");
    addMenu(pop, "Edit Search");
    pop.add(new JPopupMenu.Separator());
    addMenu(pop, "Enable");
    addMenu(pop, "Disable");
    pop.add(new JPopupMenu.Separator());
    addMenu(pop, "Delete");

    return pop;
  }

  private void changeTable() {
    TableSorter tm = (TableSorter)_inTable.getModel();

    tm.tableChanged(new TableModelEvent(tm));
  }

  private void handleSave(boolean success) {
    if(success) {
      JOptionPane.showMessageDialog(null, "Searches Saved!", "Save Complete", JOptionPane.INFORMATION_MESSAGE);
    } else {
      String saveFile = JConfig.queryConfiguration("search.savefile", "searches.xml");
      saveFile = JConfig.getCanonicalFile(saveFile, "jbidwatcher", false);

      MQFactory.getConcrete("Swing").enqueue("ERROR Failed to save searches.  Check that the directory for\n" + saveFile + " exists, and is writable.");
    }
  }

  private static final int EXECUTE=0, ENABLE=1, DISABLE=2, EDIT=3, NEW=4;

  private void showEdit(Searcher s) {
    if(_searchDetail == null) {
      _searchDetail = new SearchInfoDialog();
    }
    _searchDetail.prepare(s);
    _searchDetail.pack();
    _searchDetail.setVisible(true);
    changeTable();
  }

  private void doSingle(Searcher s, int cmd) {
    switch(cmd) {
      case EXECUTE:
        s.execute();
        break;
      case ENABLE:
        s.enable();
        break;
      case DISABLE:
        s.disable();
        break;
      case EDIT:
        showEdit(s);
        break;
      case NEW:
        showEdit(s);
        break;
      default:
        break;
    }
  }

  private void doCommand(Searcher s, int cmd, String no_items_msg) {
    int[] rows = getPossibleRows();

    if(rows.length == 0 && s != null) {
      doSingle(s, cmd);
    } else {
      if(rows.length == 0) {
        JOptionPane.showMessageDialog(_inTable, no_items_msg, "No search(es) chosen", JOptionPane.INFORMATION_MESSAGE);
      } else {
        for(int i=0; i<rows.length; i++) {
          doSingle((Searcher)_inTable.getValueAt(rows[i], -1), cmd);
        }
      }
    }
    changeTable();
  }

  private void DoExecute(Searcher s) {
    doCommand(s, EXECUTE, "You must select at least one search to execute first.");
  }

  private void DoEnable(Searcher s) {
    doCommand(s, ENABLE, "You must select at least one search to enable first.");
  }

  private void DoDisable(Searcher s) {
    doCommand(s, DISABLE, "You must select at least one search to disable first.");
  }

  private void DoEdit(Searcher s) {
    doCommand(s, EDIT, "You must select at least one search to edit first.");
  }

  private void DoNew() {
    doSingle(null, NEW);
  }

  private void DoDelete(Searcher chosenSearch) {
    Searcher s = chosenSearch;
    int[] rows = getPossibleRows();
    String prompt;

    if( (rows.length <= 1) && s != null) {
      if(rows.length == 1) s = (Searcher)_inTable.getValueAt(rows[0], -1);

      prompt = "<HTML><BODY>Are you sure you want to remove this search?<br><b>" + s.getName() + "</b></body></html>";
      //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
      if(confirmDeletion(null, prompt)) {
        SearchManager.getInstance().deleteSearch(s);
      }
    } else {
      if(rows.length == 0) {
        JOptionPane.showMessageDialog(_inTable, "You must select what searches to delete first.", "No search", JOptionPane.INFORMATION_MESSAGE);
      } else {
        prompt = "Are you sure you want to remove all selected searches?";
        //  Use the right parent!  FIXME -- mrs: 17-February-2003 23:53
        if(confirmDeletion(null, prompt)) {
          ArrayList delList = new ArrayList();
          for(int i=0; i<rows.length; i++) {
            s = (Searcher)_inTable.getValueAt(rows[i], -1);
            delList.add(s);
          }
          for (int i = 0; i < delList.size(); i++) {
            Searcher del = (Searcher) delList.get(i);
            SearchManager.getInstance().deleteSearch(del);
          }
        }
      }
    }
    changeTable();
  }

  public void DoAction(String cmd, Object param) {
    Searcher search = (Searcher)param;

    if(cmd.equals("Execute")) DoExecute(search);
    else if(cmd.equals("Edit Search")) DoEdit(search);
    else if(cmd.equals("New")) DoNew();
    else if(cmd.equals("Delete")) DoDelete(search);
    else if(cmd.equals("Enable")) DoEnable(search);
    else if(cmd.equals("Disable")) DoDisable(search);
    else if(cmd.equals("Save All")) handleSave(SearchManager.getInstance().saveSearches());
    else if(cmd.equals("Load Searches")) { SearchManager.getInstance().loadSearches(); changeTable(); }
    else System.out.println("Cannot figure out what '" + cmd + "'.");
  }

  public JSearchContext() {
    localPopup = constructTablePopup();
  }

  protected void DoAction(Object src, String actionString, Object whichEntry) {
    DoAction(actionString, whichEntry);
  }
}
