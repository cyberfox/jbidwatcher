package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.util.*;

import javax.swing.table.AbstractTableModel;
import java.util.*;
import java.text.SimpleDateFormat;

public class SearchTableModel extends AbstractTableModel
{
  private final SearchManager searchManager;
  String[] column_names = {
    "Name", "Type", "Search Value", "Site", "Repeat Time", "Next Run"
  };

  public int getRowCount() { return searchManager.getSearchCount(); }
  public int getColumnCount() { return column_names.length; }
  public String getColumnName(int index) { return column_names[index]; }

  public int getColumnNumber(String colName) {
    int i;

    for(i=0; i<column_names.length; i++) {
      if(colName.equals(column_names[i])) return i;
    }
    return -1;
  }

  public Class getColumnClass(int i) { return String.class; }

  public Class getSortByColumnClass(int i) {
    if(i==5) return Long.class; else
    if(i==4) return Integer.class; else
    return getColumnClass(i);
  }

  public Object getSortByValueAt(int i, int j) {
    Searcher s = searchManager.getSearchByIndex(i);

    switch(j) {
      case -1: return s;

      case 0: return s.getName();
      case 1: return s.getTypeName();
      case 2: return s.getSearch();
      case 3: return s.getServer();
      case 4: return s.getPeriod();
      case 5: return s.getLastRun() + (s.getPeriod() * Constants.ONE_HOUR);
    }

    return null;
  }

  public Object getValueAt(int i, int j) {
    Searcher s = searchManager.getSearchByIndex(i);

    switch(j) {
      case -1:
      case 0:
      case 1:
      case 2:
      case 3: return getSortByValueAt(i, j);
      case 4:
        return formattedPeriod(s);
      case 5: return dateFormat(s);
      default:
        return null;
    }
  }

  private Object formattedPeriod(Searcher s) {
    String result;
    int period;

    period = s.getPeriod();
    if(period == -1) return "None";
    if(period < 24) {
      return period + " hour" + ((period != 1)?"s":"");
    }
    if( (period % 24) == 0) {
      return (period / 24) + " day" + ((period != 24)?"s":"");
    }
    result = (period / 24) + " day" + ((period > 48)?"s":"");
    result += ", " + (period % 24) + " hour" + (((period % 24)!=1)?"s":"");
    return result;
  }

  public String dateFormat(Searcher s) {
    if(s.getPeriod() == -1) return "Never";
    if(!s.isEnabled()) return "Disabled";

    SimpleDateFormat sdf = new SimpleDateFormat("MM.dd/yyyy @ hh:mm");
    long base = s.getLastRun();
    if(base == 0) base = System.currentTimeMillis();
    base += s.getPeriod() * Constants.ONE_HOUR;
    return sdf.format(new Date(base));
  }

  public SearchTableModel(SearchManager searchManager) {
    super();
    this.searchManager = searchManager;
  }

  public boolean isCellEditable(int row, int column) {
    return false;
  }

  public void delete(int row) {
    searchManager.deleteSearch(searchManager.getSearchByIndex(row));
  }

  public int insert(Object newObj) {
    searchManager.addSearch((Searcher)newObj);
    return getRowCount()-1;
  }
}
