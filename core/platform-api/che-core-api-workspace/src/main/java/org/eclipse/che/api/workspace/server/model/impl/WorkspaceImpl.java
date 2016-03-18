/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.workspace.server.model.impl;

import org.eclipse.che.api.core.model.workspace.Workspace;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.core.model.workspace.WorkspaceRuntime;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.commons.lang.NameGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.eclipse.che.api.core.model.workspace.WorkspaceStatus.STOPPED;

/**
 * Data object for {@link Workspace}.
 *
 * <p>If constructor/method argument value is prohibited
 * by {@link Workspace} contract then either
 * {@link NullPointerException}(when required argument is null) or
 * {@link IllegalArgumentException}(when argument is not valid) is thrown.
 *
 * @author Yevhenii Voevodin
 */
public class WorkspaceImpl implements Workspace {

    public static WorkspaceImplBuilder builder() {
        return new WorkspaceImplBuilder();
    }

    private final String              id;
    private final String              namespace;
    private final WorkspaceConfigImpl config;

    private boolean              isTemporary;
    private WorkspaceStatus      status;
    private Map<String, String>  attributes;
    private WorkspaceRuntimeImpl runtime;

    public WorkspaceImpl(String id, String namespace, WorkspaceConfig config) {
        this.id = requireNonNull(id, "Non-null id required");
        this.namespace = requireNonNull(namespace, "Non-null namespace required");
        this.config = new WorkspaceConfigImpl(config);
        this.status = STOPPED;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public WorkspaceStatus getStatus() {
        return status;
    }

    public void setStatus(WorkspaceStatus status) {
        this.status = requireNonNull(status, "Non-null status required");
    }

    @Override
    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    @Override
    public boolean isTemporary() {
        return isTemporary;
    }

    public void setTemporary(boolean isTemporary) {
        this.isTemporary = isTemporary;
    }

    @Override
    public WorkspaceConfigImpl getConfig() {
        return config;
    }

    @Override
    @Nullable
    public WorkspaceRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(@Nullable WorkspaceRuntimeImpl runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WorkspaceImpl)) return false;
        final WorkspaceImpl other = (WorkspaceImpl)obj;
        return id.equals(other.id)
               && namespace.equals(other.namespace)
               && status.equals(other.status)
               && isTemporary == other.isTemporary
               && getAttributes().equals(other.getAttributes())
               && config.equals(other.config)
               && Objects.equals(runtime, other.runtime);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + id.hashCode();
        hash = 31 * hash + namespace.hashCode();
        hash = 31 * hash + status.hashCode();
        hash = 31 * hash + config.hashCode();
        hash = 31 * hash + getAttributes().hashCode();
        hash = 31 * hash + Boolean.hashCode(isTemporary);
        hash = 31 * hash + Objects.hashCode(runtime);
        return hash;
    }

    @Override
    public String toString() {
        return "WorkspaceImpl{" +
               "id='" + id + '\'' +
               ", namespace='" + namespace + '\'' +
               ", config=" + config +
               ", isTemporary=" + isTemporary +
               ", status=" + status +
               ", attributes=" + attributes +
               ", runtime=" + runtime +
               '}';
    }

    /**
     * Helps to build complex {@link WorkspaceImpl users workspace instance}.
     *
     * @see WorkspaceImpl#builder()
     */
    public static class WorkspaceImplBuilder {

        private String              id;
        private String              namespace;
        private boolean             isTemporary;
        private WorkspaceStatus     status;
        private WorkspaceConfigImpl config;

        private WorkspaceImplBuilder() {}

        public WorkspaceImpl build() {
            final WorkspaceImpl workspace = new WorkspaceImpl(id,
                                                              namespace,
                                                              config);
            workspace.setStatus(status);
            workspace.setTemporary(isTemporary);
            return workspace;
        }

        public WorkspaceImplBuilder generateId() {
            id = NameGenerator.generate("workspace", 16);
            return this;
        }

        public WorkspaceImplBuilder fromConfig(WorkspaceConfig workspaceConfig) {
            this.config = new WorkspaceConfigImpl(workspaceConfig);
            return this;
        }

        public WorkspaceImplBuilder setId(String id) {
            this.id = id;
            return this;
        }

        public WorkspaceImplBuilder setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public WorkspaceImplBuilder setTemporary(boolean isTemporary) {
            this.isTemporary = isTemporary;
            return this;
        }

        public WorkspaceImplBuilder setStatus(WorkspaceStatus status) {
            this.status = status;
            return this;
        }

        public WorkspaceImplBuilder setConfig(WorkspaceConfigImpl workspaceConfig) {
            this.config = workspaceConfig;
            return this;
        }
    }
}
