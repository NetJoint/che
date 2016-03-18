package org.eclipse.che.api.workspace.server.model.impl;

import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.workspace.WorkspaceRuntime;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;

import java.util.List;

/**
 * Data object for {@link WorkspaceRuntime}.
 *
 * <p>If constructor/method argument value is prohibited
 * by {@link WorkspaceRuntime} contract then either
 * {@link NullPointerException}(when required argument is null) or
 * {@link IllegalArgumentException}(when argument is not valid) is thrown.
 *
 * @author Yevhenii Voevodin
 */
public class WorkspaceRuntimeImpl implements WorkspaceRuntime {

    private final String activeEnv;

    private String            rootFolder;
    private MachineImpl       devMachine;
    private List<MachineImpl> machines;

    public WorkspaceRuntimeImpl(String activeEnv) {
        this.activeEnv = activeEnv;
    }

    public WorkspaceRuntimeImpl(WorkspaceRuntime runtime) {

    }

    @Override
    public String getActiveEnv() {
        return null;
    }

    @Override
    public String getRootFolder() {
        return null;
    }

    @Override
    public Machine getDevMachine() {
        return null;
    }

    public void setDevMachine(Machine machine) {
        this.devMachine = new MachineImpl(machine);
    }

    @Override
    public List<MachineImpl> getMachines() {
        return null;
    }
}
