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
    public void setChanges(@NotNull Map<String, String> files) {
        tree.getNodeStorage().clear();


        List<Node> childNodes = new ArrayList<>();
        List<String> paths = new ArrayList<>(files.keySet());


        String commonPath = commonPath(paths);
        for (String item : paths) {
            Node childNode = new ChangedNode(item.replace(commonPath, ""), files.get(item)) {
                @Override
                public void actionPerformed() {
                    delegate.onCompareClicked();
                }
            };
            childNodes.add(childNode);
        }

        Node folder = new FolderNode(commonPath(paths));
        folder.setChildren(childNodes);

        tree.getNodeStorage().add(folder);

        if (this.tree.getSelectionModel().getSelectedNodes() == null) {
            delegate.onNodeUnselected();
        }
    }

    private Node node(List<String> paths) {
        String commonPath = commonPath(paths);
        List<Node> childNodes = new ArrayList<>();
        List<String> otherPaths = new ArrayList<>();
        Node parentNode = new FolderNode(commonPath);
        for (String item : paths) {
            String path = item.replace(commonPath, "");
            if (path.contains("/")) {
                otherPaths.add(path);
            } else {
                Node childNode = new ChangedNode(item.replace(commonPath, ""), "") {
                    @Override
                    public void actionPerformed() {
                        delegate.onCompareClicked();
                    }
                };
                childNodes.add(childNode);
            }
        }

        if (!otherPaths.isEmpty()) {
            parentNode.setChildren(Collections.singletonList(node(otherPaths)));
            parentNode.setChildren(childNodes);
        }

        return parentNode;
    }

    private static String commonPath(List<String> paths){
        String commonPath = "";
        String[][] folders = new String[paths.size()][];
        for(int i = 0; i < paths.size(); i++){
            folders[i] = paths.get(i).split("/"); //split on file separator
        }
        for(int j = 0; j < folders[0].length; j++){
            String thisFolder = folders[0][j]; //grab the next folder name in the first path
            boolean allMatched = true; //assume all have matched in case there are no more paths
            for(int i = 1; i < folders.length && allMatched; i++){ //look at the other paths
                if(folders[i].length < j){ //if there is no folder here
                    allMatched = false; //no match
                    break; //stop looking because we've gone as far as we can
                }
                //otherwise
                allMatched &= folders[i][j].equals(thisFolder); //check if it matched
            }
            if(allMatched){ //if they all matched this folder name
                commonPath += thisFolder + "/"; //add it to the answer
            }else{//otherwise
                break;//stop looking
            }
        }
        return commonPath;
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