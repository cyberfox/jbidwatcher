package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.util.*;
import javax.swing.ImageIcon;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;

public class IconFactory {
  private static Map<ImageIcon, Map<Object, ImageIcon>> _icons = new HashMap<ImageIcon, Map<Object, ImageIcon>>();

  /**
   * Create a combination of two images, and return it as a new image.
   * @param leftImage - The left image.
   * @param rightImage - The right image.
   *
   * @return - A combination image constructed of the two provided images.
   */
  public static ImageIcon getCombination(ImageIcon leftImage, ImageIcon rightImage) {
    if(leftImage == null) return rightImage;
    if(rightImage == null) return leftImage;

    // Check the cache to see if there's a set of combination icons with
    // this as the leftmost image.
    Map<Object, ImageIcon> combos = _icons.get(leftImage);
    // If we've never cached anything for this leftImage before, create
    // a map of (_icons[left])[right] -> combined icon.
    if(combos == null) {
      ImageIcon new_icon = appendIcons(leftImage, rightImage);
      combos = new HashMap<Object, ImageIcon>();
      combos.put(rightImage, new_icon);
      _icons.put(leftImage, combos);
      return new_icon;
    }

    // We know we've got a combination map, so get the combination for the
    // rightside icon.
    ImageIcon old_icon = combos.get(rightImage);
    // If we didn't have a combination for that pair, build it.
    if(old_icon == null) {
      old_icon = appendIcons(leftImage, rightImage);
      combos.put(rightImage, old_icon);
      // This will overwrite any previous combo collection with the new one.
      _icons.put(leftImage, combos);
    }

    return old_icon;
  }

  private static ImageIcon appendIcons(ImageIcon a, ImageIcon b) {
    int x=a.getIconWidth()+b.getIconWidth();
    int y=Math.max(a.getIconHeight(), b.getIconHeight());

    BufferedImage bi;

    if(a.getImage() instanceof BufferedImage) {
      bi = new BufferedImage(x, y, ((BufferedImage)a.getImage()).getType());
    } else {
      bi = new BufferedImage(x, y, BufferedImage.TYPE_4BYTE_ABGR);
    }

    Graphics ig = bi.getGraphics();
    ig.drawImage(a.getImage(), 0, 0, null);
    ig.drawImage(b.getImage(), a.getIconWidth(), 0, null);

    return new ImageIcon(bi);
  }

  private static BufferedImage createResizedCopy(Image originalImage,
                                                 int scaledWidth, int scaledHeight) {
    BufferedImage scaledBI = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = scaledBI.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    if (true) {
      g.setComposite(AlphaComposite.Src);
    }
    g.drawImage(originalImage, 0, 0, scaledWidth, scaledHeight, null);
    g.dispose();
    return scaledBI;
  }

  /**
   * Resize an image on disk to a new size, based on the scaleTo parameters.
   * If the image is wider than maxAcceptableX, it will be resized to
   * width equal to scaleToX, and the height will be automatically
   * calculated.  If the resultant height is still taller than the
   * maxAcceptableY, it will be scaled to height scaleToY, and the width
   * will be automatically calculated.
   *
   * @param inFile - The file name to load the to-be-scaled image from.
   * @param outFile - The file name to write the scaled image to.
   * @param maxAcceptableX - The largest width acceptable before scaling.
   * @param scaleToX - The width to scale the image to if larger than maxAcceptableX.
   * @param maxAcceptableY - The largest height acceptable before scaling.
   * @param scaleToY - The height to scale the image to, if the scaled Y is still larger than maxAcceptableY.
   *
   * @return - True if the image resize was successful, false if either
   * the image was not as wide as maxAcceptableX, or if the rescale operation
   * failed for some reason.
   */
  public static boolean resizeImage(String inFile, String outFile, int maxAcceptableX, int scaleToX, int maxAcceptableY, int scaleToY) {
    File input = new File(inFile);
    if(!input.exists()) return false;

    BufferedImage image;

    try {
      image = ImageIO.read(input);
    } catch (IOException ioe) {
      JConfig.log().handleException("Can't read " + inFile + " to create thumbnail.", ioe);
      return false;
    }

    if(image == null) return false;
    int x = image.getWidth();
    int y = image.getHeight();
    int new_x = -1;
    int new_y = -1;

    if(x > maxAcceptableX || y > maxAcceptableY) {
      new_x = scaleToX;
      new_y = (new_x * y / x);

      if (new_y > maxAcceptableY) {
        new_y = scaleToY;
        new_x = (new_y * x / y);
      }
    }

    if(new_x == -1 || new_y == -1) return false;

    try {
      FileOutputStream fos = new FileOutputStream(outFile);
      ImageIO.write(createResizedCopy(image, new_x, new_y), "jpeg", fos);
      fos.close();
    } catch (FileNotFoundException e) {
      JConfig.log().handleException("Can't write " + outFile + " to create thumbnail.", e);
      return false;
    } catch (IOException e) {
      JConfig.log().handleException("Can't generate image " + outFile + ".", e);
      return false;
    }
    return true;
  }
}
