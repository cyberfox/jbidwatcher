package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2006 CyberFOX Software, Inc. All Rights Reserved.
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

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jan 17, 2006
 * Time: 4:35:53 AM
 * To change this template use File | Settings | File Templates.
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
    PrintStream ps = null;

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
      if(ps != null) try { ps.close(); } catch(Exception ignored) { /* Ignore close errors */ }
      //  Log the exceptions for now, and just return false.
      ErrorManagement.handleException("Failed to write CSV file.", ioe);
      return false;
    }

    return true;
  }
}
