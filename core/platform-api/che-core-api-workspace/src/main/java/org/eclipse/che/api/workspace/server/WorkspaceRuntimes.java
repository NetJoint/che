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
package org.eclipse.che.api.workspace.server;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Striped;

import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ConflictException;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.workspace.WorkspaceRuntime;
import org.eclipse.che.api.core.model.workspace.WorkspaceStatus;
import org.eclipse.che.api.machine.server.MachineManager;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.exception.SnapshotException;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceRuntimeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.String.format;

/**
 * Defines an internal API for managing {@link WorkspaceRuntimeImpl} instances.
 *
 * <p>This component implements {@link WorkspaceStatus} spec.
 *
 * <p>All the operations performed by this component are synchronous.
 *
 * <p>The implementation is thread-safe and guarded by {@link ReentrantReadWriteLock rwLock}.
 *
 * <p>The implementation doesn't validate parameters.
 * Parameters should be validated by caller of methods of this class.
 *
 * @author Yevhenii Voevodin
 * @author Alexander Garagatyi
 */
@Singleton
public final class WorkspaceRuntimes {

    private static final int    DEFAULT_STRIPED_SIZE = 16;
    private static final Logger LOG                  = LoggerFactory.getLogger(WorkspaceRuntimes.class);

    private final Striped<ReadWriteLock>                rwLocks;
    private final ReadWriteLock                         rwLock;
    private final Map<String, RuntimeDescriptor>        descriptors;
    private final Map<String, Queue<MachineConfigImpl>> startQueues;
    private final MachineManager                        machineManager;

    private volatile boolean isPostConstructInvoked;

    @Inject
    public WorkspaceRuntimes(MachineManager machineManager) {
        this.machineManager = machineManager;
        this.rwLocks = Striped.readWriteLock(DEFAULT_STRIPED_SIZE);
        this.rwLock = new ReentrantReadWriteLock();
        this.descriptors = new HashMap<>();
        this.startQueues = new HashMap<>();
    }

    /**
     * Returns the runtime descriptor describing currently starting/running/stopping
     * workspace runtime, throws {@link NotFoundException} if workspace with
     * such id wasn't started and doesn't have runtime in any of
     * starting/running/stopping states.
     *
     * <p>Note that the {@link RuntimeDescriptor#getRuntime()} method
     * returns {@link Optional} which describes just a snapshot copy of
     * a real {@code WorkspaceRuntime} object, which means that any
     * runtime copy modifications won't affect the real object and also
     * it means that copy won't be affected with modifications applied
     * to the real runtime workspace object state.
     *
     * @param workspaceId
     *         the id of the workspace to get its runtime
     * @return descriptor which describes current state of the workspace runtime
     * @throws NotFoundException
     *         when workspace with given {@code workspaceId} doesn't have runtime
     */
    public RuntimeDescriptor get(String workspaceId) throws NotFoundException {
        rwLock(workspaceId).readLock().lock();
        try {
            final RuntimeDescriptor descriptor = descriptors.get(workspaceId);
            if (descriptor == null) {
                throw new NotFoundException("Workspace with id '" + workspaceId + "' is not running.");
            }
            return new RuntimeDescriptor(descriptor);
        } finally {
            rwLock(workspaceId).readLock().unlock();
        }
    }

    /**
     * Starts all machines from specified workspace environment,
     * creates workspace runtime instance based on that environment.
     *
     * <p>Dev-machine always starts before the other machines.
     * If dev-machine start failed then method throws appropriate
     * {@link ServerException}. During the start starting workspace
     * runtime is visible with {@link WorkspaceStatus#STARTING} status.
     *
     * <p>Note that it doesn't provide any events for
     * machines start, Machine API is responsible for it.
     *
     * @param workspace
     *         workspace which environment should be started
     * @param envName
     *         the name of the environment to start
     * @return the workspace runtime instance with machines set.
     * @throws ConflictException
     *         when workspace is already running or any other
     *         conflict error occurs during environment start
     * @throws NotFoundException
     *         whe any not found exception occurs during environment start
     *         //     * @throws ServerException
     *         //     *         when registry {@link #isPostConstructInvoked is stopped} other error occurs during environment start
     *         //     * @see MachineManager#createMachineSync(MachineConfig, String, String)
     *         //     * @see WorkspaceStatus#STARTING
     */
    public RuntimeDescriptor start(WorkspaceImpl workspace, String envName, boolean recover) throws ServerException,
                                                                                                    ConflictException,
                                                                                                    NotFoundException {
        ensurePostConstructIsNotExecuted();
        rwLock.writeLock().lock();
        try {
            ensurePostConstructIsNotExecuted();
            final RuntimeDescriptor descriptor = descriptors.get(workspace.getId());
            if (descriptor != null) {
                throw new ConflictException(format("Could not start workspace '%s' because its status is '%s'",
                                                   workspace.getConfig().getName(),
                                                   descriptor.getRuntimeStatus()));
            }
            descriptors.put(workspace.getId(), new RuntimeDescriptor(new WorkspaceRuntimeImpl(envName)));
            final EnvironmentImpl env = workspace.getConfig().getEnvironment(envName).get();
            startQueues.put(workspace.getId(), new ArrayDeque<>(env.getMachineConfigs()));
        } finally {
            rwLock.writeLock().unlock();
        }
        startQueue(workspace.getId(), recover);
        return get(workspace.getId());
    }

    /**
     * Stops running workspace.
     *
     * <p>Stops all running machines} one by one,
     * non-dev machines first. During the workspace stopping the workspace
     * will still be accessible with {@link WorkspaceStatus#STOPPING stopping} status.
     * Workspace may be stopped only if its status is {@link WorkspaceStatus#RUNNING}.
     *
     * <p>Note that it doesn't provide any events for machines stop, Machine API is responsible for it.
     *
     * @param workspaceId
     *         identifier of workspace which should be stopped
     * @throws NotFoundException
     *         when workspace with specified identifier is not running
     * @throws ServerException
     *         when any error occurs during workspace stopping
     * @throws ConflictException
     *         when running workspace status is different from {@link WorkspaceStatus#RUNNING}
     * @see MachineManager#destroy(String, boolean)
     * @see WorkspaceStatus#STOPPING
     */
    public void stop(String workspaceId) throws NotFoundException, ServerException, ConflictException {
//        ensurePostConstructIsNotExecuted();
//        rwLock.writeLock().lock();
//        final  workspace;
//        try {
//            checkRegistryIsNotStopped();
//            workspace = idToWorkspaces.get(workspaceId);
//            if (workspace == null) {
//                throw new NotFoundException("Workspace with id " + workspaceId + " is not running.");
//            }
//            if (workspace.getStatus() != RUNNING) {
//                throw new ConflictException(format("Couldn't stop '%s' workspace because its status is '%s'",
//                                                   workspace.getConfig().getName(),
//                                                   workspace.getStatus()));
//            }
//            workspace.setStatus(STOPPING);
//        } finally {
//            lock.writeLock().unlock();
//        }
//        stopMachines(workspace);
    }

    /**
     * Stops workspace destroying all its machines and removing it from in memory storage.
     */
    private void stopMachines(WorkspaceRuntime workspace) throws NotFoundException, ServerException {
//        final List<MachineImpl> machines = new ArrayList<>(workspace.getMachines());
//        // destroying all non-dev machines
//        for (MachineImpl machine : machines) {
//            if (machine.getConfig().isDev()) {
//                continue;
//            }
//            try {
//                machineManager.destroy(machine.getId(), false);
//            } catch (NotFoundException | MachineException ex) {
//                LOG.error(format("Could not destroy machine '%s' of workspace '%s'",
//                                 machine.getId(),
//                                 machine.getWorkspaceId()),
//                          ex);
//            }
//        }
//        // destroying dev-machine
//        try {
//            machineManager.destroy(workspace.getDevMachine().getId(), false);
//        } finally {
//            doRemoveWorkspace(workspace.getId());
//        }
    }

    private void startQueue(String workspaceId, boolean recover) {

    }

    private <T extends MachineConfigImpl> List<T> devFirst(List<T> machines) {
        T devMachine = null;
        for (final Iterator<T> it = machines.iterator(); it.hasNext() && devMachine == null; ) {
            final T next = it.next();
            if (next.isDev()) {
                devMachine = next;
                it.remove();
            }
        }
        machines.add(0, devMachine);
        return machines;
    }


    private void startEnv(EnvironmentImpl environment, String workspaceId, boolean recover) throws ServerException,
                                                                                                   NotFoundException,
                                                                                                   ConflictException {
        final List<? extends MachineConfig> machineConfigs = new ArrayList<>(environment.getMachineConfigs());
        final MachineConfig devCfg = findDev(machineConfigs);

        // Starting workspace dev machine
        final MachineImpl devMachine;
        try {
            devMachine = createMachine(devCfg, workspaceId, environment.getName(), recover);
        } catch (RuntimeException | MachineException | NotFoundException | SnapshotException | ConflictException ex) {
            doRemoveDescriptor(workspaceId);
            throw ex;
        }

        // Try to add dev-machine to the list of runtime workspace machines.
        // If runtime workspace doesn't exist it means only that
        // 'stopRegistry' was performed and workspace was removed by 'stopRegistry',
        // in that case dev-machine must not be destroyed(MachineManager is responsible for it)
        // and another machines must not be started.
        if (!addRunningMachine(devMachine)) {
            throw new ServerException("Workspace '" + workspaceId + "' had been stopped before its dev-machine was started");
        }

        // Try to start all the other machines different from the dev one.
        machineConfigs.remove(devCfg);
        for (MachineConfig nonDevCfg : machineConfigs) {
            try {
                final MachineImpl nonDevMachine = createMachine(nonDevCfg, workspaceId, environment.getName(), recover);
                if (!addRunningMachine(nonDevMachine)) {
                    // Non dev machine was started but workspace doesn't exist
                    // it means that either registry was stopped or runtime workspace
                    // was stopped by client. In the case when it was stopped by
                    // client we should destroy newly started non-dev machine
                    // as it wasn't destroyed by 'stop' method. When 'stopRegistry' was performed we
                    // must not destroy machine as MachineManager is responsible for it.
                    if (!isPostConstructInvoked) {
                        machineManager.destroy(nonDevMachine.getId(), false);
                    }
                    throw new ServerException("Workspace '" + workspaceId + "' had been stopped before all its machines were started");
                }
            } catch (RuntimeException | MachineException | NotFoundException | SnapshotException | ConflictException ex) {
                LOG.error(format("Error while creating machine '%s' in workspace '%s', environment '%s'",
                                 nonDevCfg.getName(),
                                 workspaceId,
                                 environment.getName()),
                          ex);
            }
        }
    }

    /**
     * Creates or recovers machine based on machine config.
     */
    private MachineImpl createMachine(MachineConfig machine,
                                      String workspaceId,
                                      String envName,
                                      boolean recover) throws MachineException,
                                                              SnapshotException,
                                                              NotFoundException,
                                                              ConflictException {
        try {
            if (recover) {
                return machineManager.recoverMachine(machine, workspaceId, envName);
            } else {
                return machineManager.createMachineSync(machine, workspaceId, envName);
            }
        } catch (BadRequestException brEx) {
            // TODO fix this in machineManager
            throw new IllegalArgumentException(brEx.getLocalizedMessage(), brEx);
        }
    }

    /**
     * Adds given machine to the running workspace, if the workspace exists.
     * Sets up this machine as dev-machine if it is dev.
     *
     * @return true if machine was added to the workspace(workspace exists) and false otherwise
     */
    @VisibleForTesting
    boolean addRunningMachine(MachineImpl machine) throws ServerException {
        rwLock.writeLock().lock();
        try {
            final RuntimeDescriptor descriptor = descriptors.get(machine.getWorkspaceId());
            if (descriptor != null) {
                final WorkspaceRuntimeImpl runtime = descriptor.getRuntime();
                if (machine.getConfig().isDev()) {
                    runtime.setDevMachine(machine);
                }
                runtime.getMachines().add(machine);
            }
            return descriptor != null;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes all descriptors from the in-memory storage, while
     * {@link MachineManager#cleanup()} is responsible for machines destroying.
     */
    @PostConstruct
    @VisibleForTesting
    void cleanup() {
        isPostConstructInvoked = true;
        rwLock.writeLock().lock();
        try {
            descriptors.clear();
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void doRemoveDescriptor(String workspaceId) {
        rwLock.writeLock().lock();
        try {
            descriptors.remove(workspaceId);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private <T extends MachineConfig> T findDev(List<T> machines) {
        for (T machine : machines) {
            if (machine.isDev()) {
                return machine;
            }
        }
        return null;
    }

    private ReadWriteLock rwLock(String wsId) {
        return rwLocks.get(wsId);
    }

    private void ensurePostConstructIsNotExecuted() throws ServerException {
        if (isPostConstructInvoked) {
            throw new ServerException("Could not perform operation while registry is stopping workspaces");
        }
    }

    /**
     * Wrapper for the {@link WorkspaceRuntime} instance.
     * Knows the state of the started workspace runtime,
     * helps to postpone {@code WorkspaceRuntime} instance creation to
     * the time when all the machines from the workspace are created.
     */
    public static class RuntimeDescriptor {

        private WorkspaceRuntimeImpl runtime;
        private boolean              isStopping;

        private RuntimeDescriptor(WorkspaceRuntimeImpl runtime) {
            this.runtime = runtime;
        }

        private RuntimeDescriptor(RuntimeDescriptor descriptor) {
            this(descriptor.runtime);
            this.isStopping = descriptor.isStopping;
        }

        /**
         * Returns an {@link Optional} describing a started {@link WorkspaceRuntime},
         * if the runtime is in starting state then an empty {@code Optional} will be returned.
         */
        public WorkspaceRuntimeImpl getRuntime() {
            return runtime;
        }

        /**
         * Returns the status of the started workspace runtime.
         * The relation between {@link #getRuntime()} and this method
         * is pretty clear, whether workspace is in starting state, then
         * {@code getRuntime()} will return an empty optional, otherwise
         * the optional describing a running or stopping workspace runtime.
         */
        public WorkspaceStatus getRuntimeStatus() {
            if (isStopping) {
                return WorkspaceStatus.STOPPING;
            }
            if (runtime.getDevMachine() == null) {
                return WorkspaceStatus.STARTING;
            }
            return WorkspaceStatus.RUNNING;
        }

        private void setStopping() {
            isStopping = true;
        }
    }
}
