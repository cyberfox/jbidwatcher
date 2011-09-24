package com.jbidwatcher.auction;

import com.jbidwatcher.util.ByteBuffer;
import com.jbidwatcher.util.IconFactory;
import com.jbidwatcher.util.config.JConfig;

import java.io.File;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: Morgan
 * Date: Jun 19, 2008
 * Time: 4:58:45 PM
 *
 * Utility class to handle the thumbnail files, finding them, saving
 * them, and loading them.
 */
public class Thumbnail {
  public static String getValidImagePath(String identifier) {
    return getValidImagePath(identifier, null);
  }

  static String getValidImagePath(String identifier, ByteBuffer buf) {
    String outPath = JConfig.queryConfiguration("auctions.savepath");
    if(outPath == null || outPath.length() == 0) return null;

    String basePath = outPath + System.getProperty("file.separator") + identifier;
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
        String maxWidthString = JConfig.queryConfiguration("thumbnail.maxWidth", "512");
        String prefWidthString = JConfig.queryConfiguration("thumbnail.prefWidth", "256");
        String maxHeightString = JConfig.queryConfiguration("thumbnail.maxHeight", "512");
        String prefHeightString = JConfig.queryConfiguration("thumbnail.prefWidth", "256");
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
            JConfig.log().handleException("Can't create 'bad' lock file.", e);
          }
        }
      }
    }
    return imgPath;
  }
}
