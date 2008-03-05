package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.queue.MQFactory;
import com.jbidwatcher.util.queue.MessageQueue;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.ui.IconFactory;

import java.net.*;
import java.io.File;
import java.io.IOException;

/** @noinspection MagicNumber,Singleton*/
public class ThumbnailManager implements MessageQueue.Listener {
  private static ThumbnailManager _instance = new ThumbnailManager();
  private ThumbnailManager() {
    MQFactory.getConcrete("thumbnail").registerListener(this);
  }

  public void messageAction(Object deQ) {
    AuctionInfo ai = (AuctionInfo) deQ;

    ByteBuffer thumbnail = ai.getSiteThumbnail();
    //  eBay has started including a 64x64 image instead of the 96x96 ones they used to have,
    //  but it's named '*6464.jpg' instead of '*.jpg'.
    if(thumbnail == null) thumbnail = ai.getAlternateSiteThumbnail();
    //  If we retrieved 'something', but it was 0 bytes long, it's not a thumbnail.
    if(thumbnail != null && thumbnail.getLength() == 0) thumbnail = null;

    setThumbnail(ai, thumbnail);
  }

  public static void setThumbnail(AuctionInfo ai, ByteBuffer b) {
    String imgPath = getValidImagePath(ai, b);

    ai.setThumbnail(imgPath);
  }

  public static ByteBuffer downloadThumbnail(URL img) {
    ByteBuffer tmpThumb = Http.getURL(img);
    //  There's a specific image which is just 'click here to
    //  view item'.  Boring, and misleading.
    if(tmpThumb.getCRC() == 0xAEF9E727 ||
       tmpThumb.getCRC() == 0x3D7BF54E ||
       tmpThumb.getCRC() == 0x0E1AE309 ||
       tmpThumb.getCRC() == Long.parseLong(JConfig.queryConfiguration("thumbnail.crc", "0"), 16) ||
       tmpThumb.getCRC() == 0x5DAB591F) {
      tmpThumb = null;
    }
    return tmpThumb;
  }

  public static ThumbnailManager getInstance() {
    return _instance;
  }

  public static String getValidImagePath(AuctionInfo ai) {
    return getValidImagePath(ai, null);
  }

  private static String getValidImagePath(AuctionInfo ai, ByteBuffer buf) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    String basePath = outPath + System.getProperty("file.separator") + ai.getIdentifier();
    String thumbPath = basePath + "_t.jpg";
    String imgPath = thumbPath;
    if (buf != null) buf.save(basePath + ".jpg");
    File f = new File(thumbPath);

    if (!f.exists()) {
      File img = new File(basePath + ".jpg");
      if (!img.exists()) { return null; }
      String badConversionPath = basePath + "_b.jpg";
      File conversionAttempted = new File(badConversionPath);
      imgPath = basePath + ".jpg";

      if (!conversionAttempted.exists()) {
        String maxWidthString = JConfig.queryConfiguration("thumbnail.maxWidth", "256");
        String prefWidthString = JConfig.queryConfiguration("thumbnail.prefWidth", "128");
        String maxHeightString = JConfig.queryConfiguration("thumbnail.maxHeight", "256");
        String prefHeightString = JConfig.queryConfiguration("thumbnail.prefWidth", "128");
        int maxWidth = Integer.parseInt(maxWidthString);
        int prefWidth = Integer.parseInt(prefWidthString);
        int maxHeight = Integer.parseInt(maxHeightString);
        int prefHeight = Integer.parseInt(prefHeightString);
        if (IconFactory.resizeImage(imgPath, thumbPath, maxWidth, prefWidth, maxHeight, prefHeight)) {
          imgPath = thumbPath;
        } else {
          try {
            //  Create a mark file that notes that the thumbnail was
            //  attempted to be created, and failed.  It'll default to
            //  using the standard image file.
            conversionAttempted.createNewFile();
          } catch (IOException e) {
            com.jbidwatcher.util.config.ErrorManagement.handleException("Can't create 'bad' lock file.", e);
          }
        }
      }
    }
    return imgPath;
  }
}
