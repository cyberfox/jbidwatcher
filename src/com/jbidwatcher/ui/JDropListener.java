package com.jbidwatcher.ui;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.ui.util.JDropHandler;

import java.io.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.*;
import java.awt.datatransfer.Clipboard;

public class JDropListener implements DropTargetListener {
  private JDropHandler handler;

  private boolean _windows = false;
  private DataFlavor _isoFlavor = null;
  private DataFlavor _ascFlavor = null;
  private DataFlavor _plainFlavor = null;
  private DataFlavor _utf8HtmlFlavor = null;
  private DataFlavor _thtmlFlavor = null;
  private DataFlavor _pl2Flavor = null;
  private DataFlavor _htmlFlavor = null;
  private DataFlavor _urlFlavor = null;

  private static final String[][] _str_flavors = {
    { "UTF8Html", "text/html; class=java.io.InputStream; charset=UTF-8" },
    { "isoFlavor", "text/plain; class=java.io.InputStream; charset=iso8859-1" },
    { "utfFlavor", "text/plain; class=java.io.InputStream; charset=UTF-8" },
    { "ascFlavor", "text/plain; class=java.io.InputStream; charset=ascii" },
    { "pl2Flavor", "text/plain; class=java.io.InputStream" },
    { "thtmlFlavor", "text/html" },
    { "htmlFlavor", "text/html; class=java.io.Reader; charset=Unicode" },
    { "urlFlavor", "application/x-url; class=java.io.InputStream" }
  };

  private static final int ALL_ACTIONS = DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK;

  public JDropListener(JDropHandler inHandler) {
    super();

    setupFlavors();
    handler = inHandler;
  }

  private DataFlavor getDataFlavor(DataFlavor inFlavor, String whichFlavor) {
    if(inFlavor != null) return inFlavor;

    for (String[] _str_flavor : _str_flavors) {
      if (whichFlavor.equals(_str_flavor[0])) {
        DataFlavor df;
        try {
          df = new DataFlavor(_str_flavor[1]);
        } catch (ClassNotFoundException e) {
          JConfig.log().logDebug("Failed to initialize " + whichFlavor);
          df = null;
        }
        return df;
      }
    }
    return null;
  }

  private void setupFlavors() {
    //  If it's NOT Windows
	  _windows = System.getProperty("os.name").indexOf("indows") != -1;

    //  Deprecated, generally unused, but deprecation didn't provide
    // a useful alternative w/o rewriting a lot of code, so I'm keeping it for now.
    if(_plainFlavor == null) {
//      _plainFlavor = DataFlavor.plainTextFlavor;
      _plainFlavor = DataFlavor.getTextPlainUnicodeFlavor();
    }

    _isoFlavor = getDataFlavor(_isoFlavor, "isoFlavor");
    _ascFlavor = getDataFlavor(_ascFlavor, "ascFlavor");
    _pl2Flavor = getDataFlavor(_pl2Flavor, "pl2Flavor");
    _htmlFlavor= getDataFlavor(_htmlFlavor,"htmlFlavor");
    _utf8HtmlFlavor = getDataFlavor(_utf8HtmlFlavor,"UTF8Html");
    _thtmlFlavor = getDataFlavor(_thtmlFlavor,"thtmlFlavor");
    _urlFlavor = getDataFlavor(_urlFlavor, "urlFlavor");
  }

  private void dumpDataFlavors(DataFlavor[] dfa) {
    if(dfa != null) {
      if(dfa.length == 0) {
        System.err.println("Length is still zero!");
      }
      for(int j = 0; j<dfa.length; j++) {
        System.err.println("Flavah " + j + " == " + dfa[j].getHumanPresentableName());
        System.err.println("Flavah/mime " + j + " == " + dfa[j].getMimeType());
      }
    } else {
      System.err.println("Flavahs supported: none!\n");
    }
  }

  private boolean testFlavor(DataFlavor inFlavor, Transferable t) {
    if(inFlavor != null) {
      if(t.isDataFlavorSupported(inFlavor)) {
        JConfig.log().logVerboseDebug("Accepting(2): " + inFlavor.getMimeType());
        return true;
      }
    }
    return false;
  }

  private boolean testFlavor(DataFlavor inFlavor, DropTargetDragEvent t) {
    if(inFlavor != null) {
      if(t.isDataFlavorSupported(inFlavor)) {
        /*
         * I think this has been debugged enough.  This gets annoying.
         */
        JConfig.log().logVerboseDebug("Accepting(1): " + inFlavor.getMimeType());
        return true;
      }
    }
    return false;
  }

  private DataFlavor testAllFlavors(Transferable t) {
    if(testFlavor(_utf8HtmlFlavor, t)) return _utf8HtmlFlavor;
    if(testFlavor(_htmlFlavor, t)) return _htmlFlavor;
    if(testFlavor(_thtmlFlavor, t)) return _thtmlFlavor;

    if(testFlavor(_urlFlavor, t)) return _urlFlavor;
    if(_windows && testFlavor(_ascFlavor, t)) return _ascFlavor;

    if(testFlavor(_isoFlavor, t)) return _isoFlavor;
    if(testFlavor(_plainFlavor, t)) return _plainFlavor;
    if(testFlavor(_pl2Flavor, t)) return _pl2Flavor;

    if(testFlavor(DataFlavor.stringFlavor, t)) return DataFlavor.stringFlavor;

    return null;
  }

  private DataFlavor testAllFlavors(DropTargetDragEvent dtde) {
    if(testFlavor(_utf8HtmlFlavor, dtde)) return _utf8HtmlFlavor;
    if(testFlavor(_htmlFlavor, dtde)) return _htmlFlavor;
    if(testFlavor(_thtmlFlavor, dtde)) return _thtmlFlavor;

    if(testFlavor(_urlFlavor, dtde)) return _urlFlavor;
    if(_windows && testFlavor(_ascFlavor, dtde)) return _ascFlavor;

    if(testFlavor(_isoFlavor, dtde)) return _isoFlavor;
    if(testFlavor(_plainFlavor, dtde)) return _plainFlavor;
    if(testFlavor(_pl2Flavor, dtde)) return _pl2Flavor;

    if(testFlavor(DataFlavor.stringFlavor, dtde)) return DataFlavor.stringFlavor;

    return null;
  }

  private void acceptDrag(DropTargetDragEvent dtde) {
    int dragaction = dtde.getDropAction();

    if(dragaction != 0) {
      dtde.acceptDrag(dragaction);
    } else {
      dtde.acceptDrag(ALL_ACTIONS);
    }
  }

  private void acceptDrop(DropTargetDropEvent dtde) {
    int dragaction = dtde.getDropAction();

    if(dragaction != 0) {
      dtde.acceptDrop(dragaction);
    } else {
      dtde.acceptDrop(ALL_ACTIONS);
    }
  }

  private void checkDrag(DropTargetDragEvent dtde) {
    int da = dtde.getDropAction();
    if(dtde.getCurrentDataFlavors().length == 0) {
      JConfig.log().logVerboseDebug("Zero length accepted... (" + da + ")");
      acceptDrag(dtde);
      return;
    }
    if(testAllFlavors(dtde) != null) {
      JConfig.log().logVerboseDebug("Accepting drag! (" + da + ")");
      acceptDrag(dtde);
    } else {
      dtde.rejectDrag();
      JConfig.log().logVerboseDebug("Rejecting drag! (" + da + ")");
    }
  }

  public void dragEnter(DropTargetDragEvent dtde) {
    JConfig.log().logVerboseDebug("DragEnter!");
    checkDrag(dtde);
    if(JConfig.queryConfiguration("debug.uber", "false").equals("true") && JConfig.debugging) dumpDataFlavors(dtde.getCurrentDataFlavors());
  }

  public void dragOver(DropTargetDragEvent dtde) {
    checkDrag(dtde);
  }

  public void dragExit(DropTargetEvent dtde) { JConfig.log().logVerboseDebug("Drag exited!"); }
  public void dropActionChanged(DropTargetDragEvent dtde) {
    acceptDrag(dtde);
    JConfig.log().logVerboseDebug("Drag Action Changed!");
  }

  private void dumpFlavorsOld(Transferable t) {
    DataFlavor[] dfa = t.getTransferDataFlavors();

    if(dfa != null) {
      if(dfa.length == 0) {
        JConfig.log().logVerboseDebug("Trying a second attack...");
        try {
          Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
          Transferable t2 = sysClip.getContents(null);
          StringBuffer stBuff = getTransferData(t2);
          JConfig.log().logVerboseDebug("Check out: " + stBuff);
        } catch(Exception e) {
          JConfig.log().handleException("Caught: " + e, e);
        }
        JConfig.log().logVerboseDebug("Done trying a second attack...");
      }
    }
    dumpDataFlavors(dfa);
  }

  private BufferedReader useNewAPI(Transferable t, DataFlavor dtf) {
    Reader dropReader = null;

    try {
      //  Oddly enough, this appears to dump the text out to the
      //  console under win32-jre-1.4.0_01-b03
      dropReader = dtf.getReaderForText(t);
    } catch (UnsupportedFlavorException e) {
      JConfig.log().handleDebugException("Unable to read dropped data (bad flavor)", e);
    } catch (IOException e) {
      JConfig.log().handleDebugException("Unable to read dropped data (unspecific error)", e);
    }

    if (dropReader != null) {
      BufferedReader br = new BufferedReader(dropReader);
      return (br);
    }
    return null;
  }

  private StringBuffer getDataFromReader(Reader br) {
    StringBuffer xferData = null;

    try {
      char[] buf = new char[513];
      int charsRead;
      do {
        charsRead = br.read(buf, 0, 512);
        if(charsRead != -1) {
          JConfig.log().logVerboseDebug("Read: " + charsRead + " characters.");
          if(xferData == null) {
            xferData = new StringBuffer();
          }
          xferData.append(buf,0,charsRead);
        }
      } while(charsRead != -1);
      br.close();
    } catch(IOException e) {
      JConfig.log().logDebug("Caught an IO Exception trying to read the drag/drop data!");
      return null;
    }

    return xferData;
  }

  private StringBuffer getDataFromStream(InputStream br) {
    return(getDataFromReader(new InputStreamReader(br)));
  }

  private StringBuffer getInputStreamData(Transferable t, DataFlavor dtf, InputStream dropStream) {
    BufferedReader br = useNewAPI(t, dtf);
    StringBuffer xferData;
    try {

      //  If the 'new' API failed...
      if (br == null) {
        if (JConfig.queryConfiguration("debug.uber", "false").equals("true"))
          JConfig.log().logDebug("Non-getReaderForText: " + dropStream);
        try {
          InputStreamReader isr = new InputStreamReader(dropStream, "utf-16le");

          xferData = getDataFromStream(dropStream);
          if (xferData != null) {
            return xferData;
          } else {
            br = new BufferedReader(isr);
          }
        } catch (UnsupportedEncodingException uee) {
          JConfig.log().logDebug("Unicode encoding unsupported.");
          br = new BufferedReader(new InputStreamReader(dropStream));
        }
      }
      xferData = getDataFromReader(br);
    } finally {
      if (br != null) try { br.close(); } catch (IOException ignored) { }
    }

    return xferData;
  }

  public StringBuffer getTransferData(Transferable t) {
    DataFlavor dtf = testAllFlavors(t);

    JConfig.log().logVerboseDebug("dtf == " + dtf);

    Object dropped;
    try {
      if(dtf == _htmlFlavor || dtf == _utf8HtmlFlavor || dtf == _thtmlFlavor) {
        /*
         * Annoying.
         */
        if(JConfig.queryConfiguration("debug.uber", "false").equals("true") && JConfig.debugging) System.out.println("Ick: " + t.getTransferData(DataFlavor.getTextPlainUnicodeFlavor()));
      }
      dropped = t.getTransferData(dtf);
    } catch(IOException ioe) {
      try { dropped = t.getTransferData(DataFlavor.stringFlavor); } catch(Exception e) {
        JConfig.log().logDebug("I/O Exception: " + ioe);
        return null;
      }
    } catch(UnsupportedFlavorException ufe) {
      try { dropped = t.getTransferData(DataFlavor.stringFlavor); } catch(Exception e) {
        JConfig.log().logDebug("Unsupported flavor: " + dtf);
        return null;
      }
    }

    if(dropped != null) {
      StringBuffer xferData = null;
      if(dropped instanceof InputStream) {
        JConfig.log().logVerboseDebug("Dropped an InputStream");
        xferData = getInputStreamData(t, dtf, (InputStream)dropped);
      } else if(dropped instanceof Reader) {
        JConfig.log().logVerboseDebug("Dropped a Reader");
        xferData = getDataFromReader(new BufferedReader((Reader)dropped));
      } else if(dropped instanceof java.net.URL) {
        JConfig.log().logVerboseDebug("Dropped a URL");
        JConfig.log().logVerboseDebug("Got: " + dropped.toString());

        xferData = new StringBuffer(dropped.toString());
      } else if(dropped instanceof String) {
        JConfig.log().logVerboseDebug("Dropped a String");
        xferData = new StringBuffer((String)dropped);
      }

      return(xferData);
    }
    return null;
  }

  public void drop(DropTargetDropEvent dtde) {
    Transferable t = dtde.getTransferable();

    JConfig.log().logVerboseDebug("Dropping!");

    if(t.getTransferDataFlavors().length == 0) {
      Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
      Transferable t2 = sysClip.getContents(null);

      JConfig.log().logDebug("Dropped 0 data flavors, trying clipboard.");
      DataFlavor[] dfa2 = null;

      if(t2 != null) {
        JConfig.log().logVerboseDebug("t2 is not null: " + t2);
        dfa2 = t2.getTransferDataFlavors();
        JConfig.log().logVerboseDebug("Back from getTransferDataFlavors()!");
      } else {
        JConfig.log().logVerboseDebug("t2 is null!");
      }

      dumpAllFlavorsSupported(dfa2);
    }

    if(JConfig.queryConfiguration("debug.uber", "false").equals("true") && JConfig.debugging) dumpFlavorsOld(t);

    DataFlavor dtf = testAllFlavors(t);
    StringBuffer dropData = null;
    if(dtf != null) {
      JConfig.log().logVerboseDebug("Accepting!");
      acceptDrop(dtde);

      dropData = getTransferData(t);
      dtde.dropComplete(true);
      dtde.getDropTargetContext().dropComplete(true);
      if(dropData != null) {
        if(handler != null) {
          handler.receiveDropString(dropData);
        }
      }
    } else {
      JConfig.log().logVerboseDebug("Rejecting!");
      dtde.rejectDrop();
      handler.receiveDropString(dropData);
    }
  }

  private void dumpAllFlavorsSupported(DataFlavor[] dfa2) {
    if(JConfig.queryConfiguration("debug.uber", "false").equals("true")) {
      if(dfa2 != null) {
        if(dfa2.length == 0) {
          JConfig.log().logVerboseDebug("Length is still zero!");
        }
        for(int j = 0; j<dfa2.length; j++) {
          JConfig.log().logVerboseDebug("Flavah " + j + " == " + dfa2[j].getHumanPresentableName());
          JConfig.log().logVerboseDebug("Flavah/mime " + j + " == " + dfa2[j].getMimeType());
        }
      } else {
        JConfig.log().logVerboseDebug("Flavahs supported: none!\n");
      }
    }
  }
}
