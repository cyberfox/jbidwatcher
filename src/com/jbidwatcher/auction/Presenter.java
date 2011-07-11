package com.jbidwatcher.auction;

/**
 * Created by IntelliJ IDEA.
 * User: mrs
 * Date: 7/10/11
 * Time: 3:25 AM
 * To change this template use File | Settings | File Templates.
 */
public interface Presenter {
  String buildInfo(boolean includeEvents);

  String buildComment(boolean showThumbnail);
}
