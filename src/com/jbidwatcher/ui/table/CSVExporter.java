package com.jbidwatcher.ui.table;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jan 17, 2006
 * Time: 4:35:53 AM
 * 
 * Manage a comma separated value export.  Quotes (") will be replaced by doubled-quotes ("").
 */
public class CSVExporter {
  private JTable m_table;

  public CSVExporter(JTable inTable) {
    m_table = inTable;
  }

  private static void addCSV(StringBuffer sb, Object value, boolean commaPrepend) {
    String canon = value.toString();
    //  Replace double-quotes in the text with doubled-double-quotes.
    canon = canon.replaceAll("\"", "\"\"");
    if(commaPrepend) sb.append(',');
    sb.append('"');
    sb.append(canon);
    sb.append('"');
  }

  public boolean export(String fname) {
    PrintStream ps;

    try {
      StringBuffer outBuf = new StringBuffer();

      for(int i=0; i<m_table.getColumnModel().getColumnCount(); i++) {
        addCSV(outBuf, m_table.getColumnModel().getColumn(i).getHeaderValue(), i!=0);
      }
      outBuf.append('\n');

      for(int i=0; i<m_table.getRowCount(); i++) {
        for(int j=0; j<m_table.getColumnCount(); j++) {
          Object output = m_table.getValueAt(i, j);
          if(output instanceof Icon) {
            addCSV(outBuf, "", j!=0);
          } else {
            if(output != null) addCSV(outBuf, output, j!=0);
          }
        }
        outBuf.append('\n');
      }

      ps = new PrintStream(new FileOutputStream(fname));
      ps.println(outBuf);
      ps.close();
    } catch(IOException ioe) {
      //  Log the exceptions for now, and just return false.
      JConfig.log().handleException("Failed to write CSV file.", ioe);
      return false;
    }

    return true;
  }
}
