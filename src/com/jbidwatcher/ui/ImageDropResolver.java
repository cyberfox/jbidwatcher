package com.jbidwatcher.ui;

import java.awt.Point;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 9/10/11
 * Time: 1:42 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ImageDropResolver {
  public void handle(String imgUrl, Point location);
}
