// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content;

import com.intellij.openapi.ui.popup.ActiveIcon;
import com.intellij.ui.EngravedTextGraphics;
import com.intellij.ui.Gray;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.BaseButtonBehavior;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimedDeadzone;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

class ContentTabLabel extends BaseLabel {
  private final ActiveIcon closeIcon = new ActiveIcon(JBUI.CurrentTheme.ToolWindow.closeTabIcon(true),
                                                      JBUI.CurrentTheme.ToolWindow.closeTabIcon(false));
  private final Content myContent;
  private final TabContentLayout myLayout;

  protected static final int ICONS_GAP = 3;

  private final List<AdditionalIcon> additionalIcon = new ArrayList<>();

  private final AdditionalIcon closeTabIcon = new AdditionalIcon(closeIcon) {
    @NotNull
    @Override
    public Rectangle getRectangle() {
      return new Rectangle(getX(), 0, getIconWidth(), getHeight());
    }

    @Override
    public boolean getActive() {
      return mouseOverIcon(this);
    }

    @Override
    public boolean getAvailable() {
      return canBeClosed();
    }
  };

  BaseButtonBehavior behavior = new BaseButtonBehavior(this) {
    protected void execute(final MouseEvent e) {
      if (canBeClosed() && mouseOverIcon(closeTabIcon)) {
        contentManager().removeContent(getContent(), true);
      }
      else {
        selectContent();
      }
    }
  };

  protected final boolean mouseOverIcon(AdditionalIcon icon) {
    if (!isHovered()) return false;

    Point point = MouseInfo.getPointerInfo().getLocation();
    SwingUtilities.convertPointFromScreen(point, this);
    return icon.contains(point);
  }

  public ContentTabLabel(@NotNull Content content, @NotNull TabContentLayout layout) {
    super(layout.myUi, true);
    myLayout = layout;
    myContent = content;

    fillIcons(additionalIcon);

    behavior.setActionTrigger(MouseEvent.MOUSE_PRESSED);
    behavior.setMouseDeadzone(TimedDeadzone.NULL);

    myContent.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        final String property = event.getPropertyName();
        if (Content.IS_CLOSABLE.equals(property)) {
          repaint();
        }
      }
    });
  }

  protected void fillIcons(List<AdditionalIcon> icons) {
    icons.add(closeTabIcon);
  }

  @Override
  protected void processMouseMotionEvent(MouseEvent event) {
    super.processMouseMotionEvent(event);

    if (isHovered() && invalid()) {
      repaint();
    }
  }

  protected boolean invalid() {
    return additionalIcon.stream().anyMatch(icon -> icon.getAvailable());
  }

  public final boolean canBeClosed() {
    return myContent.isCloseable() && contentManager().canCloseContents();
  }

  protected void selectContent() {
    final ContentManager mgr = contentManager();
    if (mgr.getIndexOfContent(myContent) >= 0) {
      mgr.setSelectedContent(myContent, true);
    }
  }

  public void update() {
    if (!myLayout.isToDrawTabs()) {
      setHorizontalAlignment(SwingConstants.LEFT);
      setBorder(null);
    }
    else {
      if (additionalIcon.stream().anyMatch(icon -> icon.getAvailable())) {
        setHorizontalAlignment(SwingConstants.LEFT);
        setBorder(JBUI.Borders.empty(0, 12, 0, 7));
      }
      else {
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(JBUI.Borders.empty(0, 12));
      }
    }

    updateTextAndIcon(myContent, isSelected());
  }

  @Override
  public Dimension getPreferredSize() {
    final Dimension size = super.getPreferredSize();
    int w = size.width;

    for (AdditionalIcon icon : additionalIcon) {
      if (icon.getAvailable()) {
        icon.setX(w + ICONS_GAP - getInsets().right);
        w += icon.getIconWidth() + ICONS_GAP;
      }
    }

    return new Dimension(w, size.height);
  }

  @Override
  protected boolean allowEngravement() {
    return isSelected() || (myUi != null && myUi.myWindow.isActive());
  }

  @Override
  protected Color getActiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.foreground(selected);
    }

    return super.getActiveFg(selected);
  }

  @Override
  protected Color getPassiveFg(boolean selected) {
    if (contentManager().getContentCount() > 1) {
      return JBUI.CurrentTheme.Label.disabledForeground(selected);
    }

    return super.getPassiveFg(selected);
  }

  private void paintIcons(final Graphics g) {
    for (AdditionalIcon icon : additionalIcon) {
      if (icon.getAvailable()) {
        icon.paintIcon(this, g);
      }
    }
  }

  protected void paintComponent(final Graphics g) {
    super.paintComponent(g);
    paintIcons(g);
  }

  public boolean isSelected() {
    return contentManager().isSelected(myContent);
  }

  public boolean isHovered() {
    return behavior.isHovered();
  }

  @Override
  protected Graphics _getGraphics(Graphics2D g) {
    if (isSelected() && contentManager().getContentCount() > 1) {
      return new EngravedTextGraphics(g, 1, 1, Gray._0.withAlpha(myUi.myWindow.isActive() ? 120 : 130));
    }

    return super._getGraphics(g);
  }

  private ContentManager contentManager() {
    return myUi.myWindow.getContentManager();
  }

  @NotNull
  @Override
  public Content getContent() {
    return myContent;
  }
}
