package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.config.JConfigTab;
import com.jbidwatcher.ui.table.TableColumnController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Jan 11, 2006
 * Time: 2:05:32 AM
 *
 */
public class JTabProperties extends JConfigTab implements ActionListener {
  private String _tab;
  private JFrame _frame = null;

  private JComboBox cbWonTarget = null;
  private JComboBox cbLostTarget = null;
  private JComboBox cbOtherTarget = null;

  private String WON_TARGET;
  private String LOST_TARGET;
  private String OTHER_TARGET;

  private Map<String, JCheckBox> columns2Boxes = null;

  public JTabProperties(String tabName) {
    super.setLayout(new BorderLayout());
    _tab = tabName;
    WON_TARGET = _tab + ".won_target";
    LOST_TARGET= _tab + ".lost_target";
    OTHER_TARGET=_tab + ".other_target";

    JPanel jp = new JPanel();
    jp.setLayout(new BorderLayout());
    jp.add(panelPack(buildDropdownPanel()), BorderLayout.NORTH);
    jp.add(panelPack(buildColumnPanel()), BorderLayout.CENTER);
    super.add(jp, BorderLayout.NORTH);
  }

  public void actionPerformed(ActionEvent e) {
    String cmd = e.getActionCommand();
    if(cmd.equals("OK")) {
      apply();
      _frame.setVisible(false);
    } else {
      ListManager.getInstance().toggleField(_tab, cmd);
    }
  }

  public void setColumnStatus(String colName, boolean status) {
    JCheckBox jcb = columns2Boxes.get(colName);
    if(jcb != null) jcb.setSelected(status);
  }

  public void updateValues() {
    prepareComboBox(cbWonTarget);
    prepareComboBox(cbLostTarget);
    prepareComboBox(cbOtherTarget);

    cbWonTarget.setSelectedItem(JConfig.queryConfiguration(WON_TARGET, "complete"));
    cbLostTarget.setSelectedItem(JConfig.queryConfiguration(LOST_TARGET, "complete"));
    cbOtherTarget.setSelectedItem(JConfig.queryConfiguration(OTHER_TARGET, "complete"));
  }

  private JPanel buildDropdownPanel() {
    JPanel dropDowns = new JPanel();

    dropDowns.setBorder(BorderFactory.createTitledBorder("End-of-listing item targets"));
    dropDowns.setLayout(new BoxLayout(dropDowns, BoxLayout.Y_AXIS));

    cbWonTarget = new JComboBox();
    cbLostTarget = new JComboBox();
    cbOtherTarget = new JComboBox();

    dropDowns.add(makeLine(new JLabel("Send items won to: "), cbWonTarget));
    dropDowns.add(makeLine(new JLabel("Send items bid but not won to: "), cbLostTarget));
    dropDowns.add(makeLine(new JLabel("Send everything else to: "), cbOtherTarget));

    updateValues();

    return dropDowns;
  }

  private static void prepareComboBox(JComboBox target) {
    target.removeAllItems();
    List<String> tabs = ListManager.getInstance().allCategories();

    target.setEditable(true);
    if(tabs != null) {
      tabs.remove("selling");
      for (String tabName : tabs) {
        target.addItem(tabName);
      }
    }
  }

  private JPanel buildColumnPanel() {
    JPanel columnChecks = new JPanel();
    if(columns2Boxes == null) columns2Boxes = new TreeMap<String, JCheckBox>();

    columnChecks.setBorder(BorderFactory.createTitledBorder("Custom Column Settings"));

    JPanel internal = new JPanel();
    internal.setLayout(new GridLayout(0, 4, 2*10, 0));

    List<String> columns = ListManager.getInstance().getColumns(_tab);
    Object[] names = TableColumnController.getInstance().getColumnNames().toArray();
    Arrays.sort(names);
    for (Object name1 : names) {
      String s = (String) name1;
      if (s != null) {
        JCheckBox jch = new JCheckBox(s, columns.contains(s));
        jch.addActionListener(this);
        columns2Boxes.put(s, jch);
        internal.add(jch);
      }
    }

    columnChecks.add(internal, BorderLayout.CENTER);

    return columnChecks;
  }

  public String getTabName() {
    return _tab;
  }

  public void cancel() {
    // don't do anything...
  }

  public void apply() {
    String won = (String)cbWonTarget.getSelectedItem();
    String lost= (String)cbLostTarget.getSelectedItem();
    String other=(String)cbOtherTarget.getSelectedItem();

    JConfig.setConfiguration(WON_TARGET, won);
    JConfig.setConfiguration(LOST_TARGET, lost);
    JConfig.setConfiguration(OTHER_TARGET, other);

  }

  public void setFrame(JFrame frame) {
    _frame = frame;
  }
}
