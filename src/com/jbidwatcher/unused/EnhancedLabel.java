package com.jbidwatcher.unused;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import javax.swing.*;
import java.awt.*;

public class EnhancedLabel extends JLabel {
  private boolean underlined = false;
  //  private Graphics graphics = null;

  public void paint(Graphics g) {
    super.paint(g);
    //    graphics = g;
    if (underlined)
      underlineText(g);
  }

  protected void underlineText(Graphics g) {
    Insets i = getInsets();
    FontMetrics fm = g.getFontMetrics();

    Rectangle textRect = new Rectangle();
    Rectangle viewRect = new Rectangle(i.left, i.top, getWidth() - (i.right + i.left), getHeight() - (i.bottom + i.top));

    SwingUtilities.layoutCompoundLabel(this, fm, getText(), getIcon(),
                                       getVerticalAlignment(), getHorizontalAlignment(),
                                       getVerticalTextPosition(), getHorizontalTextPosition(),
                                       viewRect, new Rectangle(), textRect,
                                       getText() == null ? 0 : ((Integer) UIManager.get("Button.textIconGap")).intValue());

    g.fillRect(textRect.x + ((Integer) UIManager.get("Button.textShiftOffset")).intValue() - 4,
               textRect.y + fm.getAscent() + ((Integer) UIManager.get("Button.textShiftOffset")).intValue() + 2,
               textRect.width, 1);
  }
}
