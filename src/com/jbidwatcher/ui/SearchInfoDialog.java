package com.jbidwatcher.ui;

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.search.Searcher;
import com.jbidwatcher.search.SearchManager;
import com.jbidwatcher.FilterManager;

import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class SearchInfoDialog extends JDialog {
  private JPanel contentPane;
  private JButton buttonOK;
  private JButton buttonCancel;
  private JComboBox searchTypeBox;
  private JTextField searchNameField;
  private JComboBox periodList;
  private JComboBox currencyBox = null;
  private JCheckBox periodEnabled;
  private JComboBox tabList;
  private JTextField searchField;
  private boolean cancelled=false;
  private Searcher curSearch;
  private Map<String, String> curToId = null;

  private static final String[] _periods = {
    "Every hour",
    "Every 6 hours",
    "Every 12 hours",
    "Once a day",
    "Every other day",
    "Once a week",
    "Only on command"
  };

  private static final String[] _search_types = {
    "Text Search",
    "Title Only",
    "Seller Search",
    "URL Load",
    "My Items" };

  private static final int[] _hours = {
    1, 6, 12, 24, 48, 168, -1
  };

  public String getType() { return (String)searchTypeBox.getSelectedItem(); }
  public String getName() { return searchNameField.getText(); }
  public String getSearch(){return searchField.getText(); }
  public String getPeriod(){return (String)periodList.getSelectedItem(); }
  public String getTab()  { return (String)tabList.getSelectedItem(); }
  public boolean doPeriodic() { return periodEnabled.isSelected(); }
  public String getCurrency() {
    if(curToId == null) return null;

    return curToId.get(currencyBox.getSelectedItem().toString());
  }

  public SearchInfoDialog() {
    setContentPane(contentPane);
    setModal(true);
    setLocationRelativeTo(null);
    getRootPane().setDefaultButton(buttonOK);

    buttonOK.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onOK();
      }
    });

    buttonCancel.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    });

    // call onCancel() when cross is clicked
    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });

    // call onCancel() on ESCAPE
    contentPane.registerKeyboardAction(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        onCancel();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  private void onOK() {
    cancelled = false;
    if(curSearch == null) {
      curSearch = SearchManager.getInstance().buildSearch(System.currentTimeMillis(), getType(), getName(), getSearch(), "ebay", getCurrency(), -1);
      SearchManager.getInstance().addSearch(curSearch);
    } else {
      curSearch.setName(getName());
      curSearch.setSearch(getSearch());
      curSearch.setCurrency(getCurrency());
    }
    curSearch.setPeriod(_hours[periodList.getSelectedIndex()]);
    curSearch.setCategory(getTab());
    if(doPeriodic()) {
      curSearch.enable();
    } else {
      curSearch.disable();
    }
    dispose();
  }

  private void onCancel() {
    cancelled = true;
    dispose();
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void prepare(Searcher s) {
    buildTabList();
    if(s == null) {
      tabList.setSelectedItem("current");
      searchField.setText("");
      searchNameField.setText("");
      periodList.setSelectedItem("Only on command");
      periodEnabled.setSelected(false);
      searchTypeBox.setEnabled(true);
      searchTypeBox.setSelectedIndex(0);
      currencyBox.setEnabled(true);
      currencyBox.setSelectedIndex(0);
    } else {
      tabList.setSelectedItem(s.getCategory());
      searchField.setText(s.getSearch());
      searchNameField.setText(s.getName());
      //  You can not change the type of a search after creation.
      searchTypeBox.setSelectedItem(s.getTypeName());
      searchTypeBox.setEnabled(false);
      String oldCur = s.getCurrency();
      if(oldCur == null) oldCur = "0";
      currencyBox.setSelectedItem(JConfig.queryConfiguration("ebay.currencySearch." + oldCur, "All"));
      if(s.getTypeName().equals("Text") || s.getTypeName().equals("Title")) {
        currencyBox.setEnabled(true);
      } else {
        currencyBox.setEnabled(false);
      }
      periodEnabled.setSelected(s.isEnabled());
      periodList.setSelectedIndex(0);
      for(int index = 0; index<_hours.length; index++) {
        if(s.getPeriod() == _hours[index]) {
          periodList.setSelectedIndex(index);
          break;
        }
      }
    }
    curSearch = s;
  }

  public static void main(String[] args) {
    SearchInfoDialog dialog = new SearchInfoDialog();
    dialog.pack();
    dialog.setVisible(true);
    System.exit(0);
  }

  {
    JConfig.setConfiguration("ebay.currencySearch.0", "All");
    JConfig.setConfiguration("ebay.currencySearch.1", "U.S. dollar");
    JConfig.setConfiguration("ebay.currencySearch.2", "Canadian dollar");
    JConfig.setConfiguration("ebay.currencySearch.3", "Pound Sterling");
    JConfig.setConfiguration("ebay.currencySearch.5", "Australian dollar");
    JConfig.setConfiguration("ebay.currencySearch.7", "Euro");
    JConfig.setConfiguration("ebay.currencySearch.44", "Indian Rupee");
    JConfig.setConfiguration("ebay.currencySearch.41", "New Taiwan dollar");
    JConfig.setConfiguration("ebay.currencySearch.13", "Swiss franc");

    setupUI();
  }

  private JPanel boxUp(Component a, Component b) {
    JPanel newBox = new JPanel();
    newBox.setLayout(new BoxLayout(newBox, BoxLayout.X_AXIS));
    newBox.add(a);
    newBox.add(b);

    return newBox;
  }

  private void setupUI() {
    contentPane = new JPanel(new BorderLayout());

    final JPanel panel3 = new JPanel();
    panel3.setLayout(new BorderLayout());

    buttonOK = new JButton();
    buttonOK.setText("OK");

    buttonCancel = new JButton();
    buttonCancel.setText("Cancel");

    panel3.add(boxUp(buttonOK, buttonCancel), BorderLayout.EAST);
    contentPane.add(panel3, BorderLayout.SOUTH);

    final JLabel label1 = new JLabel("Search Name: ");
    searchNameField = new JTextField(12);

    final JLabel label2 = new JLabel(" Search Type: ");
    searchTypeBox = new JComboBox(_search_types);

    periodList = new JComboBox(_periods);
    periodEnabled = new JCheckBox("Enable Repeated Search");

    tabList = new JComboBox();
    buildTabList();

    currencyBox = new JComboBox();
    buildCurrencyList(currencyBox);

    final JLabel searchLabel = new JLabel("Search: ");
    searchField = new JTextField(40);

    final JLabel tabLabel = new JLabel("Destination Tab: ");

    final JLabel curLabel = new JLabel("Currency: ");

    JPanel form = new JPanel();
    form.setLayout(new SpringLayout());
    form.add(label1);
    form.add(boxUp(boxUp(searchNameField, label2), searchTypeBox));
    form.add(searchLabel);
    form.add(searchField);
    form.add(tabLabel);
    form.add(tabList);
    form.add(curLabel);
    form.add(currencyBox);
    form.add(new JLabel("Repeat every: "));
    form.add(boxUp(periodList, periodEnabled));
    SpringUtilities.makeCompactGrid(form, 5, 2, 6, 6, 6, 3);
    contentPane.add(form, BorderLayout.CENTER);
  }

  private void buildCurrencyList(JComboBox cBox) {
    cBox.removeAllItems();
    cBox.setEditable(false);

    List<String> tmpList = JConfig.getMatching("ebay.currencySearch.");
    Collections.sort(tmpList);
    for (String o : tmpList) {
      if (o != null) {
        String s = JConfig.queryConfiguration("ebay.currencySearch." + o);
        if (s != null) {
          cBox.addItem(s);

          if (curToId == null) curToId = new TreeMap<String, String>();
          curToId.put(s, o);
        }
      }
    }
  }

  private void buildTabList() {
    tabList.removeAllItems();
    tabList.setEditable(true);

    List<String> tabs = FilterManager.getInstance().allCategories();
    if(tabs != null) {
      tabs.remove("complete");
      tabs.remove("selling");
      for (String tabName : tabs) {
        tabList.addItem(tabName);
      }
    }
  }
}
