package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.*;
import java.io.*;
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan Schweers
 * Date: Mar 24, 2006
 * Time: 1:47:44 AM
 *
 */
public class AuctionTransformer implements ErrorListener, URIResolver {
  public static String getTimeLeft(String auctionId) {
    AuctionEntry ae = AuctionEntry.findByIdentifier(auctionId);
    return (ae == null)?"(unknown)" : ae.getTimeLeft();
  }

  private static SimpleDateFormat dateFmt = new SimpleDateFormat("dd-MMM-yy HH:mm:ss zzz");

  public static String formatDate(String when) {
    try {
      return dateFmt.format(new Date(Long.parseLong(when)));
    } catch (NumberFormatException e) {
      return "(unknown)";
    }
  }

  public static StringBuffer outputHTML(String loadFile) {
    return outputHTML(loadFile, null);
  }

  public static StringBuffer outputHTML(String loadFile, String xmlOutputFile) {
    InputStream xmlIn = null;
    InputStream xslIn = null;
    Source xmlSource;
    FileOutputStream htmlOut = null;

    // create the XML content input source
    // can be a DOM node, SAX stream, or any
    // Java input stream/reader
    try {
      //String xmlInputFile = "myXMLinput.xml";
      try {
        xmlIn = new FileInputStream(loadFile);
        xmlSource = new StreamSource(xmlIn);
      } catch(FileNotFoundException fnfe) {
        //noinspection deprecation
        Reader xmlReader = new StringReader(
            "<?xml version=\"1.0\"?>\n" +
            "\n" +
            "<!DOCTYPE auctions SYSTEM \"http://www.jbidwatcher.com/auctions.dtd\">\n" +
            "<jbidwatcher format=\"0101\">" +
            "<auctions count=\"0\">" +
            "</auctions>" +
            "</jbidwatcher>"
        );
        xmlSource = new StreamSource(xmlReader);
      }

      // create the XSLT Stylesheet input source
      // can be a DOM node, SAX stream, or a
      // java input stream/reader
      String xsltInputFile = "auctionTransform.xsl";
//      xslIn = new FileInputStream(xsltInputFile);
      xslIn = JConfig.bestSource(AuctionTransformer.class.getClassLoader(), xsltInputFile);
      Source xsltSource = new StreamSource(xslIn);

      // create the result target of the transformation
      // can be a DOM node, SAX stream, or a java out
      // stream/reader

      Result transResult;
      StringWriter sw = null;
      if(xmlOutputFile == null) {
        sw = new StringWriter();
        transResult = new StreamResult(sw);
      } else {
        htmlOut = new FileOutputStream(xmlOutputFile);
        transResult = new StreamResult(htmlOut);
      }

      // create the transformerfactory & transformer instance
      TransformerFactory tf = TransformerFactory.newInstance();
      //tf.setURIResolver(new AuctionTransformer());
      Transformer t = tf.newTransformer(xsltSource);
      t.setErrorListener(new AuctionTransformer());

      // execute transformation & fill result target object
      t.transform(xmlSource, transResult);

      //  If we're outputting a buffer, return it, otherwise we've output
      //  the file, and should just return null.
      if(xmlOutputFile == null) return sw.getBuffer();
    } catch(Exception ignored) {
      ignored.printStackTrace();
    } finally {
      try {
        if(xmlIn != null) xmlIn.close();
        if(xslIn != null) xslIn.close();
        if(htmlOut != null) htmlOut.close();
      } catch(IOException ignored) {
        //  Ignore exceptions on close.
      }
    }
    return null;
  }

  public void error(TransformerException exception) throws TransformerException {
    System.err.println("Error occurred @ " + exception.getMessageAndLocation());
  }

  public void fatalError(TransformerException exception) throws TransformerException {
    System.err.println("Fatal error @ " + exception.getMessageAndLocation());
  }

  public void warning(TransformerException exception) throws TransformerException {
    System.err.println("Warning @ " + exception.getMessageAndLocation());
  }

  public Source resolve(String href, String base) throws TransformerException {
    System.err.println("href == " + href + ", base == " + base);
    if(!href.contains("jar")) {
      return new StreamSource(JConfig.bestSource(AuctionTransformer.class.getClassLoader(), href));
    }
    return null;
  }
}
