package org.eclipse.che.api.core.model.workspace;

import org.eclipse.che.commons.annotation.Nullable;

import java.util.Map;

/**
 * Defines a contract for workspace instance.
 *
 * <p>Workspace instance defines all the attributes related to the
 * certain workspace plus configuration used to create instance plus its runtime.
 *
 * @author Yevhenii Voevodin
 */
public interface Workspace {

    /**
     * Returns the identifier of this workspace instance.
     * It is mandatory and unique.
     */
    String getId();

    /**
     * Returns the owner of the current workspace instance.
     * The combination of owner and workspace name
     * ({@code getConfig()}{@link WorkspaceConfig#getName() .getName()})
     * is unique.
     */
    String getOwner();

    /**
     * Returns the status of the current workspace instance.
     *
     * <p>All the workspaces which don't have runtime
     * are considered {@link WorkspaceStatus#STOPPED}.
     */
    WorkspaceStatus getStatus();

    /**
     * Returns workspace instance attributes (e.g. last modification date).
     * Workspace attributes must not contain null keys or values.
     */
    Map<String, String> getAttributes();

    /**
     * Returns true if this workspace is temporary, and false otherwise.
     * Temporary workspace exists only in runtime so {@link #getRuntime()}
     * will never return null for temporary workspace as well as {@link #getStatus()}
     * will never return {@link WorkspaceStatus#STOPPED}.
     */
    boolean isTemporary();

    /**
     * Returns a configuration of this workspace instance.
     * Workspace is always created from the configuration so the configuration
     * is mandatory for every workspace instance.
     */
    WorkspaceConfig getConfig();

    /**
     * Returns the runtime of this workspace instance.
     * If status of this workspace instance is either {@link WorkspaceStatus#RUNNING}
     * or {@link WorkspaceStatus#STARTING}, or {@link WorkspaceStatus#STOPPING} then
     * returned value is not null, otherwise it is.
     */
    @Nullable
    WorkspaceRuntime getRuntime();
}
