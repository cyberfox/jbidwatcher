package com.jbidwatcher.ui;

import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.scripting.Scripting;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

import org.jruby.demo.readline.TextAreaReadline;
import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;

public class ScriptManager implements MessageQueue.Listener
{
  private JFrame mFrame = null;
  private Thread t2;

  private static final String EXECUTE = "EXECUTE ";

  public void show() {
    mFrame.setVisible(true);
    try { t2.join(); } catch(InterruptedException e) { /* ignore */ }
  }

  public ScriptManager() {
    MQFactory.getConcrete("scripting").registerListener(this);
  }

  public JFrame getNewScriptManager() {
    final JFrame console = new JFrame("JBidwatcher Scripting Console");

    console.getContentPane().setLayout(new BorderLayout());
    console.setSize(700, 600);

    JEditorPane text = new JTextPane();

    text.setMargin(new Insets(8, 8, 8, 8));
    text.setCaretColor(new Color(0xa4, 0x00, 0x00));
    text.setBackground(new Color(0xf2, 0xf2, 0xf2));
    text.setForeground(new Color(0xa4, 0x00, 0x00));
    Font font = findFont("Monospaced", Font.PLAIN, 14, new String[]{"Monaco", "Andale Mono"});

    text.setFont(font);
    JScrollPane pane = new JScrollPane();
    pane.setViewportView(text);
    pane.setBorder(BorderFactory.createLineBorder(Color.darkGray));
    console.getContentPane().add(pane);
    console.validate();

    final TextAreaReadline tar = new TextAreaReadline(text, " Welcome to the JBidwatcher IRB Scripting Console \n\n");

    final Ruby runtime = Scripting.getRuntime();
    RubyInstanceConfig config = runtime.getInstanceConfig();
    config.setObjectSpaceEnabled(true);
    Scripting.setOutput(tar.getOutputStream());
    Scripting.setInput(tar.getInputStream());
    tar.hookIntoRuntime(runtime);

    t2 = new Thread() {
      public void run() {
        console.setVisible(true);
        runtime.evalScriptlet("require 'irb'; require 'irb/completion'; IRB.start");
      }
    };
    t2.start();

    return console;
  }

  private Font findFont(String otherwise, int style, int size, String[] families) {
    String[] fonts = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames();
    Arrays.sort(fonts);
    Font font = null;
    for (String family : families) {
      if (Arrays.binarySearch(fonts, family) >= 0) {
        font = new Font(family, style, size);
        break;
      }
    }
    if (font == null) font = new Font(otherwise, style, size);
    return font;
  }

  private void prepFrame() {
    if (mFrame == null) {
      mFrame = getNewScriptManager();
    }
  }

  public void messageAction(Object deQ)
  {
    String msg = (String) deQ;
    if(msg.equals("SHOW")) {
      prepFrame();
      mFrame.setVisible(true);
    } else if(msg.equals("HIDE")) {
      prepFrame();
      mFrame.setVisible(false);
    } else if(msg.startsWith(EXECUTE)) {
      int firstSpace = msg.indexOf(' ', EXECUTE.length());
      if(firstSpace == -1) firstSpace = msg.length();
      String method = msg.substring(EXECUTE.length(), firstSpace);
      String body = "";
      if(firstSpace != msg.length()) body = msg.substring(firstSpace + 1);
      Scripting.rubyMethod(method, body);
    }
  }
}
