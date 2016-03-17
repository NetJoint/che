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

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.ide.api.project.node.HasStorablePath;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.api.project.node.interceptor.NodeInterceptor;
import org.eclipse.che.ide.ext.git.client.GitLocalizationConstant;
import org.eclipse.che.ide.ext.git.client.GitResources;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private ActionDelegate delegate;
    private Tree           tree;
    private Button         btnCompare;

    private final GitLocalizationConstant locale;

    @Inject
    protected ChangedListViewImpl(GitResources resources,
                                  GitLocalizationConstant locale) {
        this.res = resources;
        this.locale = locale;

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
                if (!event.getSelection().isEmpty()) {
                    delegate.onNodeSelected(event.getSelection().get(0));
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
    public void setChanges(@NotNull Map<String, String> items) {
        tree.getNodeStorage().clear();


        List<String> files = new ArrayList<>(items.keySet());
        List<String> paths = new ArrayList<>();
        for (String file : files) {
            String path = file.substring(0, file.lastIndexOf("/"));
            if (!paths.contains(path)) {
                paths.add(path);
            }
        }

        Map<String, Node> nodes = new HashMap<>();
        for (int i = getMaxNestedLevel(files); i > 0; i--) {
            Map<String, List<Node>> nodeFiles = new HashMap<>();
            for (String item : files) {
                if (item.split("/").length != i) {
                    continue;
                }
                String path = item.substring(0, item.lastIndexOf("/"));
                Node file = new ChangedNode(item.substring(item.lastIndexOf("/") + 1), "");
                if (nodeFiles.keySet().contains(path)) {
                    nodeFiles.get(path).add(file);
                } else {
                    List<Node> listFiles = new ArrayList<>();
                    listFiles.add(file);
                    nodeFiles.put(path, listFiles);
                }
            }
            for (String path : nodeFiles.keySet()) {
                Node folder = new FolderNode(getFolderName(paths, path));
                folder.setChildren(nodeFiles.get(path));
                nodes.put(path, folder);
            }
            List<String> keySet = new ArrayList<>(nodes.keySet());
            for (String item : keySet) {
                List<Node> toAdd = new ArrayList<>();
                for (String nestedItem : keySet) {
                    if (!item.equals(nestedItem) && nestedItem.startsWith(item)) {
                        toAdd.add(nodes.remove(nestedItem));
                    }    
                }
                if (!toAdd.isEmpty()) {
                    toAdd.addAll(nodeFiles.get(item));
                    nodes.get(item).setChildren(toAdd);
                }
            }
        }

        Node lastNode = null;
        String subPathAgg;

//        for (String item : paths) {
//            Node childNode = new ChangedNode(item, files.get(item)) {
//                @Override
//                public void actionPerformed() {
//                    delegate.onCompareClicked();
//                }
//            };
//            childNodes.add(childNode);
//        }

        tree.getNodeStorage().add(Collections.singletonList(nodes.get("")));

        if (this.tree.getSelectionModel().getSelectedNodes() == null) {
            delegate.onNodeUnselected();
        }
    }
    
    private String getFolderName(List<String> paths, String comparedPath) {
        String[] segments = comparedPath.split("/");
        String trimedPath = comparedPath;
        for (int i = segments.length; i>0; i--) {
            trimedPath = trimedPath.replace("/" + segments[i-1], "");
            for (String path : paths) {
                if (path.equals(trimedPath)) {
                    return comparedPath.replace(trimedPath + "/", "");
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
    }
}