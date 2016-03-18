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

import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.js.Promises;
import org.eclipse.che.ide.api.project.node.AbstractTreeNode;
import org.eclipse.che.ide.api.project.node.HasAction;
import org.eclipse.che.ide.api.project.node.Node;
import org.eclipse.che.ide.project.shared.NodesResources;
import org.eclipse.che.ide.ui.smartTree.presentation.HasPresentation;
import org.eclipse.che.ide.ui.smartTree.presentation.NodePresentation;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Node Element used for setting it to TreeNodeStorage and viewing changed files.
 *
 * @author Igor Vinokur
 */
public class FolderChangedNode extends AbstractTreeNode implements HasPresentation, HasAction {

    private String           name;
    private NodePresentation nodePresentation;

    private final NodesResources nodesResources;

    /**
     * Create instance of ChangedNode.
     *
     * @param name
     *         name of the file that represents this node with its full path
     */
    public FolderChangedNode(String name, NodesResources nodesResources) {
        this.name = name;
        this.nodesResources = nodesResources;
    }

    @Override
    protected Promise<List<Node>> getChildrenImpl() {
        return this.getChildren(false);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }

    @Override
    public void updatePresentation(@NotNull NodePresentation presentation) {
        presentation.setPresentableText(name);
        presentation.setPresentableIcon(nodesResources.simpleFolder());
    }

    @Override
    public NodePresentation getPresentation(boolean update) {
        if (nodePresentation == null) {
            nodePresentation = new NodePresentation();
            updatePresentation(nodePresentation);
        }

        if (update) {
            updatePresentation(nodePresentation);
        }
        return nodePresentation;
    }

    @Override
    public void actionPerformed() {

    }
}
