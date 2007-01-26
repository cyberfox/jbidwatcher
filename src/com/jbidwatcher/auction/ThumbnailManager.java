package com.jbidwatcher.auction;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.config.JConfig;
import com.jbidwatcher.queue.MQFactory;
import com.jbidwatcher.queue.MessageQueue;
import com.jbidwatcher.util.http.Http;
import com.jbidwatcher.util.ByteBuffer;

import java.net.*;

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
    ai.setThumbnail(thumbnail);
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
}
