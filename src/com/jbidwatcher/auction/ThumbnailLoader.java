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

import java.net.*;

/** @noinspection MagicNumber,Singleton*/
public class ThumbnailLoader implements MessageQueue.Listener {
  private static ThumbnailLoader sInstance = null;
  private ThumbnailLoader() { }

  public void messageAction(Object deQ) {
    AuctionInfo ai = (AuctionInfo) deQ;

    String thumbnail = ai.getThumbnailURL();
    //  eBay has started including a 64x64 image instead of the 96x96 ones they used to have,
    //  but it's named '*6464.jpg' instead of '*.jpg'.
    if(thumbnail == null) thumbnail = ai.getAlternateSiteThumbnail();

    ByteBuffer thumbnailImage = getThumbnailByURL(thumbnail);

    //  If we retrieved 'something', but it was 0 bytes long, it's not a thumbnail.
    if(thumbnailImage != null && thumbnailImage.getLength() == 0) thumbnailImage = null;

    String imgPath = Thumbnail.getValidImagePath(ai.getIdentifier(), thumbnailImage);

    ai.setThumbnail(imgPath);
    MQFactory.getConcrete("redraw").enqueue(ai.getIdentifier());
  }

  private ByteBuffer getThumbnailByURL(String url) {
    ByteBuffer tmpThumb;
    try {
      tmpThumb = downloadThumbnail(JConfig.getURL(url));
    } catch (Exception ignored) {
      tmpThumb = null;
    }
    return tmpThumb;
  }

  public static ByteBuffer downloadThumbnail(URL img) {
    ByteBuffer tmpThumb = Http.net().getURL(img);
    //  There's a specific image which is just 'click here to
    //  view item'.  Boring, and misleading.
    if(tmpThumb.getCRC() == 0xAEF9E727 ||
       tmpThumb.getCRC() == 0x3D7BF54E ||
       tmpThumb.getCRC() == 0x076AE9FB ||
       tmpThumb.getCRC() == 0x0E1AE309 ||
       tmpThumb.getCRC() == Long.parseLong(JConfig.queryConfiguration("thumbnail.crc", "0"), 16) ||
       tmpThumb.getCRC() == 0x5DAB591F) {
      tmpThumb = null;
    }
    return tmpThumb;
  }

  public static void start() {
    if(sInstance == null) {
      MQFactory.getConcrete("thumbnail").registerListener(sInstance = new ThumbnailLoader());
    }
  }
}
