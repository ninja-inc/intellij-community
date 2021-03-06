// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.settings;

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatesUtils;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixChangedBuiltinTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixEditableTemplateProvider;
import com.intellij.codeInsight.template.postfix.templates.editable.PostfixTemplateEditor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;

public class PostfixTemplatesCheckboxTree extends CheckboxTree implements Disposable {
  private static final Factory<Set<PostfixTemplateCheckedTreeNode>> myNodesComparator = () ->
    new TreeSet<>((o1, o2) -> Comparing.compare(o1.getTemplate().getPresentableName(), o2.getTemplate().getPresentableName()));
  @NotNull
  private final CheckedTreeNode myRoot;
  @NotNull
  private final DefaultTreeModel myModel;
  @NotNull
  private final Map<PostfixTemplateProvider, String> myProviderToLanguage;
  private final boolean canAddTemplate;

  public PostfixTemplatesCheckboxTree(@NotNull Map<PostfixTemplateProvider, String> providerToLanguage) {
    super(getRenderer(), new CheckedTreeNode(null));
    myProviderToLanguage = providerToLanguage;
    canAddTemplate = ContainerUtil.find(providerToLanguage.keySet(), p -> StringUtil.isNotEmpty(p.getPresentableName())) != null;
    myModel = (DefaultTreeModel)getModel();
    myRoot = (CheckedTreeNode)myModel.getRoot();
    TreeSelectionListener selectionListener = new TreeSelectionListener() {
      @Override
      public void valueChanged(@NotNull TreeSelectionEvent event) {
        selectionChanged();
      }
    };
    getSelectionModel().addTreeSelectionListener(selectionListener);
    Disposer.register(this, () -> getSelectionModel().removeTreeSelectionListener(selectionListener));

    DoubleClickListener doubleClickListener = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        if (canEditSelectedTemplate()) {
          editSelectedTemplate();
          return true;
        }
        return false;
      }
    };
    doubleClickListener.installOn(this);
    Disposer.register(this, () -> doubleClickListener.uninstall(this));
    setRootVisible(false);
    setShowsRootHandles(true);
  }

  @Override
  public void dispose() {
    UIUtil.dispose(this);
  }

  @NotNull
  private static CheckboxTreeCellRenderer getRenderer() {
    return new CheckboxTreeCellRenderer() {
      @Override
      public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (!(value instanceof CheckedTreeNode)) return;
        CheckedTreeNode node = (CheckedTreeNode)value;

        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        PostfixTemplateCheckedTreeNode templateNode = ObjectUtils.tryCast(node, PostfixTemplateCheckedTreeNode.class);
        SimpleTextAttributes attributes;
        if (templateNode != null) {
          Color fgColor = templateNode.isChanged() || templateNode.isNew() ? JBColor.BLUE : null;
          attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fgColor);
        }
        else {
          attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        }
        getTextRenderer().append(StringUtil.notNullize(value.toString()),
                                 new SimpleTextAttributes(background, attributes.getFgColor(), JBColor.RED, attributes.getStyle()));

        if (templateNode != null) {
          String example = templateNode.getTemplate().getExample();
          if (StringUtil.isNotEmpty(example)) {
            getTextRenderer().append("  " + example, new SimpleTextAttributes(SimpleTextAttributes.STYLE_SMALLER, JBColor.GRAY), false);
          }
        }
      }
    };
  }

  protected void selectionChanged() {

  }

  public void initTree(@NotNull MultiMap<PostfixTemplateProvider, PostfixTemplate> providerToTemplates) {
    myRoot.removeAllChildren();
    Map<String, Set<PostfixTemplateCheckedTreeNode>> languageToNodes = new HashMap<>();
    for (Map.Entry<PostfixTemplateProvider, Collection<PostfixTemplate>> entry : providerToTemplates.entrySet()) {
      PostfixTemplateProvider provider = entry.getKey();
      String languageId = myProviderToLanguage.get(provider);
      Set<PostfixTemplateCheckedTreeNode> nodes = ContainerUtil.getOrCreate(languageToNodes, languageId, myNodesComparator);
      for (PostfixTemplate template : entry.getValue()) {
        nodes.add(new PostfixTemplateCheckedTreeNode(template, provider.getId(), false));
      }
    }
    for (Map.Entry<String, Set<PostfixTemplateCheckedTreeNode>> entry : languageToNodes.entrySet()) {
      DefaultMutableTreeNode languageNode = findOrCreateLanguageNode(entry.getKey());
      for (PostfixTemplateCheckedTreeNode node : entry.getValue()) {
        languageNode.add(new PostfixTemplateCheckedTreeNode(node.getTemplate(), node.getProviderId(), false));
      }
    }

    myModel.nodeStructureChanged(myRoot);
    TreeUtil.expandAll(this);
  }

  @Nullable
  public PostfixTemplate getSelectedTemplate() {
    TreePath path = getSelectionModel().getSelectionPath();
    return getTemplateFromPath(path);
  }

  @Nullable
  private static PostfixTemplate getTemplateFromPath(@Nullable TreePath path) {
    if (path == null || !(path.getLastPathComponent() instanceof PostfixTemplateCheckedTreeNode)) {
      return null;
    }
    return ((PostfixTemplateCheckedTreeNode)path.getLastPathComponent()).getTemplate();
  }

  @NotNull
  public MultiMap<PostfixEditableTemplateProvider, PostfixTemplate> getEditableTemplates() {
    MultiMap<PostfixEditableTemplateProvider, PostfixTemplate> result = MultiMap.createSet();
    visitTemplateNodes(node -> {
      PostfixTemplate template = node.getTemplate();
      PostfixTemplateProvider provider = template.getProvider();
      if (isEditable(template) && provider instanceof PostfixEditableTemplateProvider &&
          (!template.isBuiltin() || template instanceof PostfixChangedBuiltinTemplate)) {
        result.putValue((PostfixEditableTemplateProvider)provider, template);
      }
    });
    return result;
  }

  @NotNull
  public Map<String, Set<String>> getDisabledTemplatesState() {
    final Map<String, Set<String>> result = ContainerUtil.newHashMap();
    visitTemplateNodes(template -> {
      if (!template.isChecked()) {
        Set<String> templatesForProvider =
          ContainerUtil.getOrCreate(result, template.getProviderId(), PostfixTemplatesSettings.SET_FACTORY);
        templatesForProvider.add(template.getTemplate().getId());
      }
    });

    return result;
  }

  public void setDisabledTemplatesState(@NotNull final Map<String, Set<String>> providerToDisabledTemplates) {
    TreeState treeState = TreeState.createOn(this, myRoot);
    visitTemplateNodes(template -> {
      Set<String> disabledTemplates = providerToDisabledTemplates.get(template.getProviderId());
      String key = template.getTemplate().getId();
      if (disabledTemplates != null && disabledTemplates.contains(key)) {
        template.setChecked(false);
        return;
      }

      template.setChecked(true);
    });

    myModel.nodeStructureChanged(myRoot);
    treeState.applyTo(this);
    TreeUtil.expandAll(this);
  }

  public void selectTemplate(@NotNull final PostfixTemplate postfixTemplate, @NotNull final PostfixTemplateProvider provider) {
    visitTemplateNodes(template -> {
      if (provider.getId().equals(template.getProviderId()) && postfixTemplate.getKey().equals(template.getTemplate().getKey())) {
        TreeUtil.selectInTree(template, true, this, true);
      }
    });
  }

  private void visitTemplateNodes(@NotNull Consumer<PostfixTemplateCheckedTreeNode> consumer) {
    Enumeration languages = myRoot.children();
    while (languages.hasMoreElements()) {
      CheckedTreeNode langNode = (CheckedTreeNode)languages.nextElement();
      Enumeration templates = langNode.children();
      while (templates.hasMoreElements()) {
        Object template = templates.nextElement();
        if (template instanceof PostfixTemplateCheckedTreeNode) {
          consumer.consume((PostfixTemplateCheckedTreeNode)template);
        }
      }
    }
  }

  public boolean canAddTemplate() {
    return canAddTemplate;
  }

  public void addTemplate(@NotNull AnActionButton button) {
    DefaultActionGroup group = new DefaultActionGroup();
    for (Map.Entry<PostfixTemplateProvider, String> entry : myProviderToLanguage.entrySet()) {
      String languageId = entry.getValue();
      PostfixEditableTemplateProvider provider = ObjectUtils.tryCast(entry.getKey(), PostfixEditableTemplateProvider.class);
      if (provider != null) {
        String providerName = provider.getPresentableName();
        if (StringUtil.isEmpty(providerName)) continue;
        group.add(new DumbAwareAction(providerName) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            Project project = getProject();
            PostfixTemplateEditor editor = provider.createEditor(project);
            if (editor != null) {
              PostfixEditTemplateDialog dialog =
                new PostfixEditTemplateDialog(PostfixTemplatesCheckboxTree.this, editor, providerName, null);
              if (dialog.showAndGet()) {
                String templateKey = dialog.getTemplateKey();
                String templateId = PostfixTemplatesUtils.generateTemplateId(templateKey, provider);
                PostfixTemplate createdTemplate = editor.createTemplate(templateId, templateKey);

                PostfixTemplateCheckedTreeNode createdNode = new PostfixTemplateCheckedTreeNode(createdTemplate, languageId, true);
                DefaultMutableTreeNode languageNode = findOrCreateLanguageNode(languageId);
                languageNode.add(createdNode);
                myModel.nodeStructureChanged(languageNode);
                TreeUtil.selectNode(PostfixTemplatesCheckboxTree.this, createdNode);
              }
            }
          }
        });
      }
    }
    DataContext context = DataManager.getInstance().getDataContext(button.getContextComponent());
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(null, group, context,
                                                                          JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING, true, null);
    popup.show(ObjectUtils.assertNotNull(button.getPreferredPopupPoint()));
  }

  public boolean canEditSelectedTemplate() {
    TreePath[] selectionPaths = getSelectionModel().getSelectionPaths();
    return (selectionPaths == null || selectionPaths.length <= 1) && isEditable(getSelectedTemplate());
  }

  public void editSelectedTemplate() {
    TreePath path = getSelectionModel().getSelectionPath();
    if (!(path.getLastPathComponent() instanceof PostfixTemplateCheckedTreeNode)) return;
    PostfixTemplateCheckedTreeNode lastPathComponent = (PostfixTemplateCheckedTreeNode)path.getLastPathComponent();
    PostfixTemplate template = lastPathComponent.getTemplate();
    PostfixTemplateProvider provider = template.getProvider();
    if (isEditable(template) && provider instanceof PostfixEditableTemplateProvider) {
      PostfixTemplate templateToEdit = template instanceof PostfixChangedBuiltinTemplate
                                       ? ((PostfixChangedBuiltinTemplate)template).getDelegate()
                                       : template;
      Project project = getProject();
      PostfixTemplateEditor editor = ((PostfixEditableTemplateProvider)provider).createEditor(project);
      if (editor != null) {
        String providerName = StringUtil.notNullize(provider.getPresentableName());
        PostfixEditTemplateDialog dialog = new PostfixEditTemplateDialog(this, editor, providerName, templateToEdit);
        if (dialog.showAndGet()) {
          PostfixTemplate newTemplate = editor.createTemplate(template.getId(), dialog.getTemplateKey());
          if (template.isBuiltin()) {
            PostfixTemplate builtin = template instanceof PostfixChangedBuiltinTemplate
                                      ? ((PostfixChangedBuiltinTemplate)template).getBuiltinTemplate()
                                      : template;
            lastPathComponent.setTemplate(new PostfixChangedBuiltinTemplate(newTemplate, builtin));
          }
          else {
            lastPathComponent.setTemplate(newTemplate);
          }
          myModel.nodeStructureChanged(lastPathComponent);
        }
      }
    }
  }

  public boolean canRemoveSelectedTemplates() {
    TreePath[] paths = getSelectionModel().getSelectionPaths();
    if (paths == null) {
      return false;
    }
    for (TreePath path : paths) {
      PostfixTemplate template = getTemplateFromPath(path);
      if (isEditable(template) && (!template.isBuiltin() || template instanceof PostfixChangedBuiltinTemplate)) {
        return true;
      }
    }
    return false;
  }

  public void removeSelectedTemplates() {
    TreePath[] paths = getSelectionModel().getSelectionPaths();
    if (paths == null) {
      return;
    }
    for (TreePath path : paths) {
      PostfixTemplateCheckedTreeNode lastPathComponent = ObjectUtils.tryCast(path.getLastPathComponent(),
                                                                             PostfixTemplateCheckedTreeNode.class);
      if (lastPathComponent == null) continue;
      PostfixTemplate template = lastPathComponent.getTemplate();
      if (template instanceof PostfixChangedBuiltinTemplate) {
        lastPathComponent.setTemplate(((PostfixChangedBuiltinTemplate)template).getBuiltinTemplate());
      }
      else if (isEditable(template) && !template.isBuiltin()) {
        TreeUtil.removeLastPathComponent(this, path);
      }
    }
  }

  @Nullable
  private Project getProject() {
    // todo: retrieve proper project
    DataProvider dataProvider = DataManager.getDataProvider(this);
    return dataProvider != null ? CommonDataKeys.PROJECT.getData(dataProvider) : null;
  }

  private static boolean isEditable(@Nullable PostfixTemplate template) {
    return template != null && template.isEditable() && template.getKey().startsWith(".");
  }

  @NotNull
  private DefaultMutableTreeNode findOrCreateLanguageNode(String languageId) {
    DefaultMutableTreeNode find = TreeUtil.findNode(myRoot, n ->
      n instanceof LangTreeNode && languageId.equals(((LangTreeNode)n).getLanguageId()));
    if (find != null) {
      return find;
    }

    Language language = Language.findLanguageByID(languageId);
    String languageName = language != null ? language.getDisplayName() : languageId;
    CheckedTreeNode languageNode = new LangTreeNode(languageName, languageId);
    myRoot.add(languageNode);
    return languageNode;
  }

  private static class LangTreeNode extends CheckedTreeNode {
    @NotNull private final String myLanguageId;

    public LangTreeNode(@NotNull String languageName, @NotNull String languageId) {
      super(languageName);
      myLanguageId = languageId;
    }

    @NotNull
    public String getLanguageId() {
      return myLanguageId;
    }
  }
}
