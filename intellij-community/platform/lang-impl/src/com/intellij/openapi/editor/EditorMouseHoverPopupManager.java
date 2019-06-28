// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.tooltips.TooltipActionProvider;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.documentation.QuickDocUtil;
import com.intellij.codeInsight.hint.LineTooltipRenderer;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.hint.TooltipRenderer;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.event.EditorMouseMotionListener;
import com.intellij.openapi.editor.ex.*;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.reference.SoftReference;
import com.intellij.ui.*;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.PopupPositionManager;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class EditorMouseHoverPopupManager implements EditorMouseMotionListener {
  private static final TooltipGroup EDITOR_INFO_GROUP = new TooltipGroup("EDITOR_INFO_GROUP", 0);
  private static final int BORDER_TOLERANCE_PX = 5;

  private final Alarm myAlarm;
  private Point myPrevMouseLocation;
  private WeakReference<AbstractPopup> myPopupReference;
  private Context myContext;
  private ProgressIndicator myCurrentProgress;

  public EditorMouseHoverPopupManager(Application application, EditorFactory editorFactory) {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, application);
    editorFactory.getEventMulticaster().addEditorMouseMotionListener(this);
  }

  @Override
  public void mouseMoved(@NotNull EditorMouseEvent e) {
    if (!Registry.is("editor.new.mouse.hover.popups")) return;

    Point currentMouseLocation = e.getMouseEvent().getLocationOnScreen();
    boolean movesTowardsPopup = ScreenUtil.isMovementTowards(myPrevMouseLocation, currentMouseLocation, getCurrentHintBounds());
    myPrevMouseLocation = currentMouseLocation;
    if (movesTowardsPopup) return;

    myAlarm.cancelAllRequests();
    if (myCurrentProgress != null) {
      myCurrentProgress.cancel();
      myCurrentProgress = null;
    }

    Editor editor = e.getEditor();
    int targetOffset = getTargetOffset(e);
    if (targetOffset < 0) {
      closeHint();
      return;
    }
    Context context = createContext(editor, targetOffset);
    if (context == null) {
      closeHint();
      return;
    }
    Context.Relation relation = isHintShown() ? context.compareTo(myContext) : Context.Relation.DIFFERENT;
    if (relation == Context.Relation.SAME) {
      return;
    }
    else if (relation == Context.Relation.DIFFERENT) {
      closeHint();
    }

    ProgressIndicatorBase progress = new ProgressIndicatorBase();
    myCurrentProgress = progress;
    myAlarm.addRequest(() -> ProgressManager.getInstance().executeProcessUnderProgress(() -> {
      Info info = context.calcInfo(editor);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (progress != myCurrentProgress) return;
        myCurrentProgress = null;
        if (info != null && !EditorMouseHoverPopupControl.arePopupsDisabled(editor) && editor.getContentComponent().isShowing()) {
          PopupBridge popupBridge = new PopupBridge();
          JComponent component = info.createComponent(editor, popupBridge);
          if (component == null) {
            closeHint();
          }
          else {
            if (relation == Context.Relation.SIMILAR && isHintShown()) {
              updateHint(component, popupBridge);
            }
            else {
              AbstractPopup hint = createHint(component, popupBridge);
              showHintInEditor(hint, editor, context);
              myPopupReference = new WeakReference<>(hint);
            }
            myContext = context;
          }
        }
      });
    }, progress), context.getShowingDelay());
  }

  private Rectangle getCurrentHintBounds() {
    JBPopup popup = SoftReference.dereference(myPopupReference);
    if (popup == null || !popup.isVisible()) return null;
    Dimension size = popup.getSize();
    if (size == null) return null;
    Rectangle result = new Rectangle(popup.getLocationOnScreen(), size);
    result.grow(BORDER_TOLERANCE_PX, BORDER_TOLERANCE_PX);
    return result;
  }

  private void showHintInEditor(AbstractPopup hint, Editor editor, Context context) {
    closeHint();
    editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, context.getPopupPosition(editor));
    try {
      PopupPositionManager.positionPopupInBestPosition(hint, editor, null);
    }
    finally {
      editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
    }
    Window window = hint.getPopupWindow();
    if (window != null) window.setFocusableWindowState(true);
  }

  private static AbstractPopup createHint(JComponent component, PopupBridge popupBridge) {
    WrapperPanel wrapper = new WrapperPanel(component);
    AbstractPopup popup = (AbstractPopup)JBPopupFactory.getInstance()
      .createComponentPopupBuilder(wrapper, component)
      .setResizable(true)
      .createPopup();
    popupBridge.setPopup(popup);
    return popup;
  }

  private void updateHint(JComponent component, PopupBridge popupBridge) {
    AbstractPopup popup = SoftReference.dereference(myPopupReference);
    if (popup != null) {
      WrapperPanel wrapper = (WrapperPanel)popup.getComponent();
      wrapper.setContent(component);
      validatePopupSize(popup);
      popupBridge.setPopup(popup);
    }
  }

  private static void validatePopupSize(@NotNull AbstractPopup popup) {
    JComponent component = popup.getComponent();
    if (component != null) popup.setSize(component.getPreferredSize());
  }

  private static void closePopup(AbstractPopup popup) {
    popup.cancel();

    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    AWTEvent currentEvent = eventQueue.getTrueCurrentEvent();
    if (currentEvent instanceof MouseEvent && currentEvent.getID() == MouseEvent.MOUSE_PRESSED) { // e.g. on link activation
      // this is to prevent mouse released (and dragged, dispatched due to some reason) event to be dispatched into editor
      // alternative solution would be to activate links on mouse release, not on press
      eventQueue.blockNextEvents((MouseEvent)currentEvent);
    }
  }

  private static int getTargetOffset(EditorMouseEvent event) {
    Editor editor = event.getEditor();
    if (editor instanceof EditorEx &&
        editor.getProject() != null &&
        event.getArea() == EditorMouseEventArea.EDITING_AREA &&
        event.getMouseEvent().getModifiers() == 0 &&
        !EditorMouseHoverPopupControl.arePopupsDisabled(editor) &&
        LookupManager.getActiveLookup(editor) == null) {
      Point point = event.getMouseEvent().getPoint();
      VisualPosition visualPosition = editor.xyToVisualPosition(point);
      LogicalPosition logicalPosition = editor.visualToLogicalPosition(visualPosition);
      int offset = editor.logicalPositionToOffset(logicalPosition);
      if (editor.offsetToLogicalPosition(offset).equals(logicalPosition) && // not virtual space
          ((EditorEx)editor).getFoldingModel().getFoldingPlaceholderAt(point) == null &&
          editor.getInlayModel().getElementAt(point) == null) {
        return offset;
      }
    }
    return -1;
  }

  private static Context createContext(Editor editor, int offset) {
    Project project = Objects.requireNonNull(editor.getProject());

    HighlightInfo info = null;
    if (!Registry.is("ide.disable.editor.tooltips")) {
      info = ((DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project))
        .findHighlightByOffset(editor.getDocument(), offset, false);
    }

    PsiElement elementForQuickDoc = null;
    if (EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement()) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (psiFile != null) {
        elementForQuickDoc = psiFile.findElementAt(offset);
        if (elementForQuickDoc instanceof PsiWhiteSpace || elementForQuickDoc instanceof PsiPlainText) {
          elementForQuickDoc = null;
        }
      }
    }

    return info == null && elementForQuickDoc == null ? null : new Context(offset, info, elementForQuickDoc);
  }

  private void closeHint() {
    JBPopup popup = SoftReference.dereference(myPopupReference);
    if (popup != null) {
      popup.cancel();
    }
    myPopupReference = null;
    myContext = null;
  }

  private boolean isHintShown() {
    JBPopup popup = SoftReference.dereference(myPopupReference);
    return popup != null && popup.isVisible();
  }

  private static class Context {
    private final int targetOffset;
    private final WeakReference<HighlightInfo> highlightInfo;
    private final WeakReference<PsiElement> elementForQuickDoc;

    private Context(int targetOffset, HighlightInfo highlightInfo, PsiElement elementForQuickDoc) {
      this.targetOffset = targetOffset;
      this.highlightInfo = highlightInfo == null ? null : new WeakReference<>(highlightInfo);
      this.elementForQuickDoc = elementForQuickDoc == null ? null : new WeakReference<>(elementForQuickDoc);
    }

    private PsiElement getElementForQuickDoc() {
      return SoftReference.dereference(elementForQuickDoc);
    }

    private HighlightInfo getHighlightInfo() {
      return SoftReference.dereference(highlightInfo);
    }

    private Relation compareTo(Context other) {
      if (other == null) return Relation.DIFFERENT;
      HighlightInfo highlightInfo = getHighlightInfo();
      if (!Objects.equals(highlightInfo, other.getHighlightInfo())) return Relation.DIFFERENT;
      return Objects.equals(getElementForQuickDoc(), other.getElementForQuickDoc())
             ? Relation.SAME
             : highlightInfo == null ? Relation.DIFFERENT : Relation.SIMILAR;
    }

    private long getShowingDelay() {
      return getHighlightInfo() == null ? EditorSettingsExternalizable.getInstance().getQuickDocOnMouseOverElementDelayMillis()
                                        : Registry.intValue("ide.tooltip.initialDelay.highlighter");
    }

    @NotNull
    private VisualPosition getPopupPosition(Editor editor) {
      HighlightInfo highlightInfo = getHighlightInfo();
      if (highlightInfo == null) {
        int offset = targetOffset;
        PsiElement elementForQuickDoc = getElementForQuickDoc();
        if (elementForQuickDoc != null) {
          offset = elementForQuickDoc.getTextRange().getStartOffset();
        }
        return editor.offsetToVisualPosition(offset);
      }
      else {
        VisualPosition targetPosition = editor.offsetToVisualPosition(targetOffset);
        VisualPosition endPosition = editor.offsetToVisualPosition(highlightInfo.getEndOffset());
        if (endPosition.line <= targetPosition.line) return targetPosition;
        Point targetPoint = editor.visualPositionToXY(targetPosition);
        Point endPoint = editor.visualPositionToXY(endPosition);
        Point resultPoint = new Point(targetPoint.x, endPoint.x > targetPoint.x ? endPoint.y : editor.visualLineToY(endPosition.line - 1));
        return editor.xyToVisualPosition(resultPoint);
      }
    }

    @Nullable
    private Info calcInfo(Editor editor) {
      HighlightInfo info = getHighlightInfo();
      if (info != null && (info.getDescription() == null || info.getToolTip() == null)) {
        info = null;
      }

      String quickDocMessage = null;
      Ref<PsiElement> targetElementRef = new Ref<>();
      if (elementForQuickDoc != null) {
        PsiElement element = getElementForQuickDoc();
        QuickDocUtil.runInReadActionWithWriteActionPriorityWithRetries(() -> {
          if (element.isValid()) {
            targetElementRef.set(DocumentationManager.getInstance(editor.getProject()).findTargetElement(editor, targetOffset,
                                                                                                         element.getContainingFile(),
                                                                                                         element));
          }
        }, 5000, 100);

        if (!targetElementRef.isNull()) {
          quickDocMessage = DocumentationManager.getInstance(editor.getProject()).generateDocumentation(targetElementRef.get(), element);
        }
      }
      return info == null && quickDocMessage == null ? null : new Info(info, quickDocMessage, targetElementRef.get());
    }

    private enum Relation {
      SAME, // no need to update popup
      SIMILAR, // popup needs to be updated
      DIFFERENT // popup needs to be closed, and new one shown
    }
  }

  private static class Info {
    private final HighlightInfo highlightInfo;

    private final String quickDocMessage;
    private final WeakReference<PsiElement> quickDocElement;


    private Info(HighlightInfo highlightInfo, String quickDocMessage, PsiElement quickDocElement) {
      assert highlightInfo != null || quickDocMessage != null;
      this.highlightInfo = highlightInfo;
      this.quickDocMessage = quickDocMessage;
      this.quickDocElement = new WeakReference<>(quickDocElement);
    }

    private JComponent createComponent(Editor editor, PopupBridge popupBridge) {
      JComponent c1 = createHighlightInfoComponent(editor, quickDocMessage == null, popupBridge);
      JComponent c2 = createQuickDocComponent(editor, c1 != null, popupBridge);
      if (c1 == null && c2 == null) return null;
      JPanel p = new JPanel(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                                                    JBUI.emptyInsets(), 0, 0);
      if (c1 != null) p.add(c1, c);
      c.gridy = 1;
      c.weighty = 1;
      c.fill = GridBagConstraints.BOTH;
      if (c2 != null) p.add(c2, c);
      return p;
    }

    private JComponent createHighlightInfoComponent(Editor editor,
                                                    boolean highlightActions,
                                                    PopupBridge popupBridge) {
      if (highlightInfo == null) return null;
      TooltipAction action = TooltipActionProvider.calcTooltipAction(highlightInfo, editor);
      ErrorStripTooltipRendererProvider provider = ((EditorMarkupModel)editor.getMarkupModel()).getErrorStripTooltipRendererProvider();
      TooltipRenderer tooltipRenderer = provider.calcTooltipRenderer(Objects.requireNonNull(highlightInfo.getToolTip()), action, -1);
      if (!(tooltipRenderer instanceof LineTooltipRenderer)) return null;
      return createHighlightInfoComponent(editor, (LineTooltipRenderer)tooltipRenderer, highlightActions, popupBridge);
    }

    private static JComponent createHighlightInfoComponent(Editor editor,
                                                           LineTooltipRenderer renderer,
                                                           boolean highlightActions,
                                                           PopupBridge popupBridge) {
      Ref<WrapperPanel> wrapperPanelRef = new Ref<>();
      LightweightHint hint =
        renderer.createHint(editor, new Point(), false, EDITOR_INFO_GROUP, new HintHint().setAwtTooltip(true), highlightActions, expand -> {
          LineTooltipRenderer newRenderer = renderer.createRenderer(renderer.getText(), expand ? 1 : 0);
          JComponent newComponent = createHighlightInfoComponent(editor, newRenderer, highlightActions, popupBridge);
          AbstractPopup popup = popupBridge.getPopup();
          WrapperPanel wrapper = wrapperPanelRef.get();
          if (newComponent != null && popup != null && wrapper != null) {
            wrapper.setContent(newComponent);
            validatePopupSize(popup);
          }
        });
      if (hint == null) return null;
      bindHintHiding(hint, popupBridge);
      WrapperPanel wrapper = new WrapperPanel(hint.getComponent());
      wrapperPanelRef.set(wrapper);
      return wrapper;
    }

    private static void bindHintHiding(LightweightHint hint, PopupBridge popupBridge) {
      AtomicBoolean inProcess = new AtomicBoolean();
      hint.addHintListener(e -> {
        if (inProcess.compareAndSet(false, true)) {
          try {
            AbstractPopup popup = popupBridge.getPopup();
            if (popup != null) {
              closePopup(popup);
            }
          }
          finally {
            inProcess.set(false);
          }
        }
      });
      popupBridge.performOnCancel(() -> {
        if (inProcess.compareAndSet(false, true)) {
          try {
            hint.hide();
          }
          finally {
            inProcess.set(false);
          }
        }
      });
    }

    @Nullable
    private JComponent createQuickDocComponent(Editor editor,
                                               boolean deEmphasize,
                                               PopupBridge popupBridge) {
      if (quickDocMessage == null) return null;
      Project project = Objects.requireNonNull(editor.getProject());
      DocumentationManager documentationManager = DocumentationManager.getInstance(project);
      DocumentationComponent component = new DocumentationComponent(documentationManager, false) {
        @Override
        protected void showHint() {
          setPreferredSize(getOptimalSize());
          AbstractPopup popup = popupBridge.getPopup();
          if (popup != null) {
            validatePopupSize(popup);
          }
        }
      };
      if (deEmphasize) {
        component.setBackground(UIUtil.getToolTipActionBackground());
        if (component.needsToolbar()) {
          component.setBorder(IdeBorderFactory.createBorder(SideBorder.TOP));
        }
      }
      PsiElement element = quickDocElement.get();
      component.setData(element, quickDocMessage, null, null, null);
      component.setToolwindowCallback(() -> {
        documentationManager.createToolWindow(element, extractOriginalElement(element));
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DOCUMENTATION);
        if (toolWindow != null) {
          toolWindow.setAutoHide(false);
        }
        AbstractPopup popup = popupBridge.getPopup();
        if (popup != null) {
          closePopup(popup);
        }
      });
      popupBridge.performWhenAvailable(component::setHint);
      EditorUtil.disposeWithEditor(editor, component);
      return component;
    }
  }

  private static PsiElement extractOriginalElement(PsiElement element) {
    if (element == null) return null;
    SmartPsiElementPointer originalElementPointer = element.getUserData(DocumentationManager.ORIGINAL_ELEMENT_KEY);
    return originalElementPointer == null ? null : originalElementPointer.getElement();
  }

  private static class PopupBridge {
    private AbstractPopup popup;
    private List<Consumer<AbstractPopup>> consumers = new ArrayList<>();

    private void setPopup(@NotNull AbstractPopup popup) {
      assert this.popup == null;
      this.popup = popup;
      consumers.forEach(c -> c.accept(popup));
      consumers = null;
    }

    @Nullable
    private AbstractPopup getPopup() {
      return popup;
    }

    private void performWhenAvailable(@NotNull Consumer<AbstractPopup> consumer) {
      if (popup == null) {
        consumers.add(consumer);
      }
      else {
        consumer.accept(popup);
      }
    }

    private void performOnCancel(@NotNull Runnable runnable) {
      performWhenAvailable(popup -> popup.addListener(new JBPopupListener() {
        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          runnable.run();
        }
      }));
    }
  }

  private static class WrapperPanel extends JPanel {
    private WrapperPanel(JComponent content) {
      super(new BorderLayout());
      setBorder(null);
      setContent(content);
    }

    private void setContent(JComponent content) {
      removeAll();
      add(content, BorderLayout.CENTER);
    }
  }
}
