package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;

public class JPrintable implements Printable, Runnable {
  JTable tableView;
  protected int m_maxNumPage = 1;
  private static Thread m_thr = null;

  public JPrintable(JTable _myTable) {
    tableView = _myTable;
  }

  public void doPrint() {
    //SwingUtilities.invokeLater(this);
    m_thr = new Thread(this, "Printing");
    m_thr.start();
  }

  public void run() {
    myPrint();
  }

  public void myPrint() {
    PrinterJob pj = PrinterJob.getPrinterJob();
    PageFormat pf = pj.defaultPage();

    pf.setOrientation(PageFormat.LANDSCAPE);

    pj.setPrintable(this, pf);

    if (!pj.printDialog()) return;

    try {
      pj.print();
    } catch (Exception pe) {
      JConfig.log().handleException("Failed to print: " + pe, pe);
    }
  }

  /**
   * @brief Print out the associated table, using the graphics of the header and the text or icons in the body,
   * specifically NOT drawing the rest of the table using the JTable.paint() function.
   *
   * @param graphics - The graphics 'object' for the print code to draw to.
   * @param pageFormat - The format of the page to be printed.
   * @param pageIndex - The page number we're printing.
   * @return NO_SUCH_PAGE if the page number is beyond the max number of pages of data we have,
   * PAGE_EXISTS if we have such a page and it was successfully filled out.
   */
  public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
    if (pageIndex >= m_maxNumPage)
      return NO_SUCH_PAGE;

    Graphics2D g2 = (Graphics2D) graphics;
    g2.translate((int) pageFormat.getImageableX(), (int) pageFormat.getImageableY());
    int fontHeight = g2.getFontMetrics().getHeight();
    int fontDescent = g2.getFontMetrics().getDescent();
    double pageHeight = pageFormat.getImageableHeight() - fontHeight;
    double pageWidth = pageFormat.getImageableWidth();
    double tableWidth = (double) tableView.getColumnModel().getTotalColumnWidth();
    double scale = 1;

    if (tableWidth >= pageWidth) {
      scale = pageWidth / tableWidth;
    }

    g2.setFont(tableView.getFont());
    g2.setColor(Color.black);
    g2.drawString("Page: " + (pageIndex + 1), (int) pageWidth / 2 - 35, (int) (pageHeight + fontHeight - fontDescent));//bottom center

    TableColumnModel colModel = tableView.getColumnModel();
    int nColumns = colModel.getColumnCount();
    int[] x = new int[nColumns];
    x[0] = 0;

    g2.scale(scale, scale);
    prepColumnWidths(nColumns, colModel, x);

    //  Use the same font as the existing table.
    g2.setFont(tableView.getFont());
    FontMetrics fm = g2.getFontMetrics();
    int h = fm.getHeight();

    double headerHeightOnPage = tableView.getTableHeader().getHeight() * scale;
    double tableWidthOnPage = tableWidth * scale;
    double oneRowHeight = (tableView.getRowHeight() + tableView.getRowMargin()) * scale;
    int numRowsOnAPage = (int) ((pageHeight - headerHeightOnPage) / oneRowHeight);

    //  Start one font-line down from the drawn headers.  Use getMaxAscent as it's tighter
    //  than getHeight() which includes the descent (the font part under the baseline), and
    //  it manages to avoid characters jutting up into the header, by using the max ascent.
    //  Add the leading amount onto that, to make the space between lines look like the normal
    //  inter-line spacing, and it's all good.
    int y = (int)headerHeightOnPage+fm.getMaxAscent() + fm.getLeading();
    m_maxNumPage = (int) Math.ceil(((double) tableView.getRowCount()) / numRowsOnAPage);

    //  Theoretically unnecessary, as it gets checked at the top, but a good idea anyway,
    //  in case the answer is '0', and this is page 1?
    if (pageIndex >= m_maxNumPage) {
      return NO_SUCH_PAGE;
    }

    int iniRow = pageIndex * numRowsOnAPage;
    int endRow = Math.min(tableView.getRowCount(), iniRow + numRowsOnAPage);

    StringBuffer sbuf = new StringBuffer();
    Rectangle r = new Rectangle(0, 0, 0, 0);

    for (int nRow = iniRow; nRow < endRow; nRow++) {
      for (int nCol = 0; nCol < nColumns; nCol++) {
        //int col = tableView.getColumnModel().getColumn(nCol).getModelIndex();
        Object obj = tableView.getValueAt(nRow, nCol);
        int width = tableView.getColumnModel().getColumn(nCol).getWidth();
        //  The two things we support rendering are objects which can
        //  be turned to text via toString() and Icons.  Extend this to
        //  extend the renderable components.
        if(obj instanceof Icon) {
          Icon drawme = (Icon)obj;
          int trueX = x[nCol] + ((width / 2) - (drawme.getIconWidth()/2));
          g2.setClip(trueX, y, drawme.getIconWidth(), drawme.getIconHeight());
          drawme.paintIcon(tableView, g2, trueX, y);
        } else {
          sbuf.setLength(0);
          if(obj != null) {
            sbuf.append(obj.toString());
          }

          r.setBounds(x[nCol], y, width, h);

          g2.setClip(r);
          g2.drawString(sbuf.toString(), x[nCol], y+fm.getMaxAscent());
        }
      }
      y += h;
    }

    g2.scale(1 / scale, 1 / scale);
    g2.setClip(0, 0, (int) Math.ceil(tableWidthOnPage), (int) Math.ceil(headerHeightOnPage));
    g2.scale(scale, scale);
    tableView.getTableHeader().paint(g2);//paint header at top

    System.gc();
    return PAGE_EXISTS;
  }

  private static void prepColumnWidths(int nColumns, TableColumnModel colModel, int[] x) {
    //  Generate table widths and start-X locations.
    for (int nCol = 0; nCol < nColumns; nCol++) {
      TableColumn tk = colModel.getColumn(nCol);
      int width = tk.getWidth();

      if (nCol + 1 < nColumns)
        x[nCol + 1] = x[nCol] + width;
    }
  }
}
