/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.ext.git.client.compare.changedList;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.ide.api.project.node.HasStorablePath;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.node.interceptor.NodeInterceptor;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.GitResources;
import org.eclipse.che.ide.project.shared.NodesResources;
import org.eclipse.che.ide.ui.smartTree.NodeUniqueKeyProvider;
import org.eclipse.che.ide.ui.smartTree.Tree;
import org.eclipse.che.ide.ui.smartTree.NodeLoader;
import org.eclipse.che.ide.ui.smartTree.NodeStorage;
import org.eclipse.che.ide.ui.smartTree.SelectionModel;
import org.eclipse.che.ide.ui.smartTree.event.SelectionChangedEvent;
import org.eclipse.che.ide.ui.window.Window;

import javax.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ChangedListView}.
 *
 * @author Igor Vinokur
 */
@Singleton
public class ChangedListViewImpl extends Window implements ChangedListView {
    interface ChangedListViewImplUiBinder extends UiBinder<Widget, ChangedListViewImpl> {
    }

    private static ChangedListViewImplUiBinder ourUiBinder = GWT.create(ChangedListViewImplUiBinder.class);

    @UiField
    DockLayoutPanel changedFilesPanel;
    @UiField(provided = true)
    final GitResources res;

    private ActionDelegate      delegate;
    private Tree                tree;
    private Button              btnCompare;
    private boolean             groupByDirectory;
    private Map<String, String> items;

    private final GitLocalizationConstant locale;
    private final NodesResources nodesResources;

    @Inject
    protected ChangedListViewImpl(GitResources resources,
                                  GitLocalizationConstant locale,
                                  NodesResources nodesResources) {
        this.res = resources;
        this.locale = locale;
        this.nodesResources = nodesResources;

        Widget widget = ourUiBinder.createAndBindUi(this);

        this.setTitle(locale.changeListTitle());
        this.setWidget(widget);

        NodeStorage nodeStorage = new NodeStorage(new NodeUniqueKeyProvider() {
            @NotNull
            @Override
            public String getKey(@NotNull Node item) {
                if (item instanceof HasStorablePath) {
                    return ((HasStorablePath)item).getStorablePath();
                } else {
                    return String.valueOf(item.hashCode());
                }
            }
        });
        NodeLoader nodeLoader = new NodeLoader(Collections.<NodeInterceptor>emptySet());
        tree = new Tree(nodeStorage, nodeLoader);
        tree.getSelectionModel().setSelectionMode(SelectionModel.Mode.SINGLE);
        tree.getSelectionModel().addSelectionChangedHandler(new SelectionChangedEvent.SelectionChangedHandler() {
            @Override
            public void onSelectionChanged(SelectionChangedEvent event) {
                List<Node> selection = event.getSelection();
                if (!selection.isEmpty() && !(selection.get(0) instanceof FolderChangedNode)) {
                    delegate.onFileNodeSelected(event.getSelection().get(0));
                } else {
                    delegate.onFileNodeUnselected();
                }
            }
        });
        changedFilesPanel.add(tree);
        createButtons();

        SafeHtmlBuilder shb = new SafeHtmlBuilder();

        shb.appendHtmlConstant("<table height =\"20\">");
        shb.appendHtmlConstant("<tr height =\"3\"></tr><tr>");
        shb.appendHtmlConstant("<td width =\"20\" bgcolor =\"dodgerBlue\"></td>");
        shb.appendHtmlConstant("<td>modified</td>");
        shb.appendHtmlConstant("<td width =\"20\" bgcolor =\"red\"></td>");
        shb.appendHtmlConstant("<td>deleted</td>");
        shb.appendHtmlConstant("<td width =\"20\" bgcolor =\"green\"></td>");
        shb.appendHtmlConstant("<td>added</td>");
        shb.appendHtmlConstant("<td width =\"20\" bgcolor =\"purple\"></td>");
        shb.appendHtmlConstant("<td>has conflicts</td>");
        shb.appendHtmlConstant("</tr></table>");

        getFooter().add(new HTML(shb.toSafeHtml()));
    }

    /** {@inheritDoc} */
    @Override
    public void setDelegate(ActionDelegate delegate) {
        this.delegate = delegate;
    }

    /** {@inheritDoc} */
    @Override
    public void setChanges(@NotNull Map<String, String> items, boolean groupByDirectory) {
        this.groupByDirectory = groupByDirectory;
        this.items = items;
        tree.getNodeStorage().clear();

        List<String> allFiles = new ArrayList<>(items.keySet());
        if (groupByDirectory) {
            for (Node node : getGroupedNodes(allFiles)) {
                tree.getNodeStorage().add(node);
            }
        } else {
            for (String file : allFiles) {
                tree.getNodeStorage().add(new FileChangedNode(file, items.get(file), nodesResources) {
                    @Override
                    public void actionPerformed() {
                        delegate.onCompareClicked();
                    }
                });
            }
        }
        if (this.tree.getSelectionModel().getSelectedNodes() == null) {
            delegate.onFileNodeUnselected();
        }
    }

    private List<Node> getGroupedNodes(List<String> allFiles) {
        List<String> allPaths = new ArrayList<>();
        for (String file : allFiles) {
            String path = file.substring(0, file.lastIndexOf("/"));
            if (!allPaths.contains(path)) {
                allPaths.add(path);
            }
        }

        Map<String, Node> preparedNodes = new HashMap<>();
        List<Node> nodesToView = new ArrayList<>();
        for (int i = getMaxNestedLevel(allFiles); i > 0; i--) {
            Map<String, List<Node>> currentNodes = new HashMap<>();
            for (String file : allFiles) {
                if (file.split("/").length != i) {
                    continue;
                }
                String path = file.substring(0, file.lastIndexOf("/"));
                String name = file.substring(file.lastIndexOf("/") + 1);
                Node fileNode = new FileChangedNode(name, items.get(path.isEmpty() ? name : path + "/" + name), nodesResources) {
                    @Override
                    public void actionPerformed() {
                        delegate.onCompareClicked();
                    }
                };
                ((FileChangedNode)fileNode).setPath(path);
                if (currentNodes.keySet().contains(path)) {
                    currentNodes.get(path).add(fileNode);
                } else {
                    List<Node> listFiles = new ArrayList<>();
                    listFiles.add(fileNode);
                    currentNodes.put(path, listFiles);
                }
            }
            for (String path : currentNodes.keySet()) {
                Node folder = new FolderChangedNode(getFolderName(allPaths, path), nodesResources);
                folder.setChildren(currentNodes.get(path));
                preparedNodes.put(path, folder);
            }
            List<String> currentPaths = new ArrayList<>(preparedNodes.keySet());
            for (String currentPath : currentPaths) {
                List<Node> toAdd = new ArrayList<>();
                for (String nestedItem : currentPaths) {
                    if (!currentPath.equals(nestedItem) && nestedItem.startsWith(currentPath)) {
                        toAdd.add(preparedNodes.remove(nestedItem));
                    }
                }
                if (!toAdd.isEmpty()) {
                    toAdd.addAll(currentNodes.get(currentPath));
                    preparedNodes.get(currentPath).setChildren(toAdd);
                    nodesToView = toAdd;
                }
            }
            String s= "";
        }
        return nodesToView;
    }
    
    private String getFolderName(List<String> paths, String comparedPath) {
        String[] segments = comparedPath.split("/");
        String trimmedPath = comparedPath;
        for (int i = segments.length; i>0; i--) {
            trimmedPath = trimmedPath.replace("/" + segments[i-1], "");
            for (String path : paths) {
                if (path.equals(trimmedPath)) {
                    return comparedPath.replace(trimmedPath + "/", "");
                }
            }
        }
        return comparedPath;
    }

    private int getMaxNestedLevel(List<String> items) {
        int level = 0;
        for (String item : items) {
            int currentLevel = item.split("/").length;
            level = currentLevel > level ? currentLevel : level;
        }
        return level;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        this.hide();
    }

    /** {@inheritDoc} */
    @Override
    public void changeView() {
        groupByDirectory = !groupByDirectory;
        setChanges(items, groupByDirectory);
    }

    /** {@inheritDoc} */
    @Override
    public void showDialog() {
        this.show();
    }

    /** {@inheritDoc} */
    @Override
    public void setEnableCompareButton(boolean enabled) {
        btnCompare.setEnabled(enabled);
    }

    private void createButtons() {
        Button btnClose = createButton(locale.buttonClose(), "git-compare-btn-close", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                delegate.onCloseClicked();
            }
        });
        addButtonToFooter(btnClose);

        btnCompare = createButton(locale.buttonCompare(), "git-compare-btn-compare", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                delegate.onCompareClicked();
            }
        });
        addButtonToFooter(btnCompare);

        Button btnViewMode = createButton("view", "git-compare-btn-view", new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                delegate.onChangeViewClicked();
            }
        });
        addButtonToFooter(btnViewMode);
    }
}