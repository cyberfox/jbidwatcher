package com.jbidwatcher.ui;

import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.util.Scripting;

import javax.swing.*;
import java.awt.*;
import java.io.PipedInputStream;
import java.io.PrintStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.jruby.demo.TextAreaReadline;
import org.jruby.RubyInstanceConfig;
import org.jruby.Ruby;
import org.jruby.internal.runtime.ValueAccessor;
import org.jruby.javasupport.JavaUtil;
import org.jruby.javasupport.Java;
import org.jruby.runtime.builtin.IRubyObject;

public class ScriptManager implements MessageQueue.Listener
{
  private JFrame mFrame = null;

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
    tar.hookIntoRuntime(runtime);

    Scripting.setOutput(tar);
    Thread t2 = new Thread() {
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

  public void messageAction(Object deQ)
  {
    String msg = (String) deQ;
    if(mFrame == null) {
      mFrame = getNewScriptManager();
    }
    if(msg.equals("SHOW")) {
      mFrame.setVisible(true);
    } else if(msg.equals("HIDE")) {
      mFrame.setVisible(false);
    }
  }
}
