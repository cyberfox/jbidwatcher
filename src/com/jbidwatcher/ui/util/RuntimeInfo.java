package com.jbidwatcher.ui.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.text.DecimalFormat;

public class RuntimeInfo extends JFrame
   implements ActionListener
{
   private static final double BYTES_PER_MB = 1024.0 * 1024.0;
   private static final String UPDATE_CMD = "update";
   private static final String GC_CMD = "gc";

   private static final DecimalFormat myValueFormatter
                                      = new DecimalFormat("##0.0");

   // instance variables
   private Runtime  myRuntime = Runtime.getRuntime();
   private JLabel   myMaxMemoryLbl;
   private JLabel   myMemoryUseLbl;

   public RuntimeInfo()
   {
       super();

       JPanel contentPane = (JPanel) getContentPane();
       contentPane.add(makeInfoPanel(), BorderLayout.CENTER);
       contentPane.add(makeBtnPanel(), BorderLayout.SOUTH);

       pack();
       setVisible(true);
   }

   private JPanel makeInfoPanel()
   {
       JPanel panel = new JPanel(new GridLayout(2, 2));

       panel.add(new JLabel("Max Memory:"));
       myMaxMemoryLbl = new JLabel( getMaxMemory());
       panel.add(myMaxMemoryLbl);

       panel.add(new JLabel("Memory use:"));
       myMemoryUseLbl = new JLabel(getMemoryUse());
       panel.add(myMemoryUseLbl);

       return panel;
   }

   private JPanel makeBtnPanel()
   {
       JButton gcBtn = new JButton("GC");
       gcBtn.setActionCommand(GC_CMD);
       gcBtn.addActionListener(this);

       JButton updateBtn = new JButton("Update");
       updateBtn.setActionCommand(UPDATE_CMD);
       updateBtn.addActionListener(this);

       JPanel panel = new JPanel();
       panel.add(gcBtn);
       panel.add(updateBtn);
       return panel;
   }

   private void updateInfo()
   {
       myMaxMemoryLbl.setText(getMaxMemory());
       myMemoryUseLbl.setText(getMemoryUse());
   }

   private long getBytesUsed()
   {
       long totalMem = myRuntime.totalMemory();
       long freeMem = myRuntime.freeMemory();
       return (totalMem - freeMem);
   }

   private String getMemoryUse()
   {
     long bytesUsed = getBytesUsed();
     double megabytesUsed = bytesUsed / BYTES_PER_MB;
     return myValueFormatter.format(megabytesUsed) + " MB";
   }

   private String getMaxMemory()
   {
       long maxBytes = myRuntime.maxMemory();
       double maxMegabytes = maxBytes / BYTES_PER_MB;
       return myValueFormatter.format(maxMegabytes) + " MB";
   }

   private void doGarbageCollection()
   {
       try
       {
           System.gc();
           Thread.sleep(100);
           System.runFinalization();
           Thread.sleep(100);
           System.gc();
       }
       catch (InterruptedException ex)
       {
           ex.printStackTrace();
       }
   }

   public void actionPerformed(ActionEvent e)
   {
       String cmd = e.getActionCommand();

       if (cmd.equals(UPDATE_CMD))
       {
           updateInfo();
       }
       else if (cmd.equals(GC_CMD))
       {
           doGarbageCollection();
           updateInfo();
       }
   }
}
