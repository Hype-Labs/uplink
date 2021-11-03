package com.uplink.ulx.threading;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This class is a singleton pool of ExecutorService instances that are used to
 * dispatch a variety of work. It structures the executors by either transport
 * type or specific purpose, meaning that it offers default executors for the
 * specific work that relates with those queues.
 */
public class ExecutorPool {

    private static HashMap<Integer, ExecutorService> executors;
    private static HashMap<Integer, ScheduledExecutorService> scheduledExecutors;
    private static ExecutorService mainExecutor;
    private static ExecutorService coreExecutor;
    private static ExecutorService internetExecutor;
    private static ExecutorService driverManagerExecutor;

    /**
     * Private constructor prevents instantiation.
     */
    private ExecutorPool() {
    }

    /**
     * Getter for the pool of ExecutorService instances that have already been
     * created by the implementation. This will include a mapping of such
     * executors according to the transport type they are associated.
     * @return The pool of transport-specific ExecutorService instances.
     */
    private static HashMap<Integer, ExecutorService> getExecutors() {
        if (ExecutorPool.executors == null) {
            ExecutorPool.executors = new HashMap<>();
        }
        return ExecutorPool.executors;
    }

    /**
     * Getter for the pool of ScheduledExecutorService instances, associated
     * with the given transport types. These executors can dispatched delayed
     * work, and will be used according to the specific transport that is
     * mapped to them.
     * @return The pool of transport-specific ScheduledExecutorService instances.
     */
    private static HashMap<Integer, ScheduledExecutorService> getScheduledExecutors() {
        if (ExecutorPool.scheduledExecutors == null) {
            ExecutorPool.scheduledExecutors = new HashMap<>();
        }
        return ExecutorPool.scheduledExecutors;
    }

    /**
     * Returns an executor according to the transport type that is given as an
     * argument. An executor will be created for the given transport type if
     * one does not exist, and subsequent calls will always return the same
     * executor. Executors returned by this method will be single threaded.
     * This means that operations within the context of a given transport type
     * will run on the same thread.
     * @param transportType The transport type.
     * @return An ExecutorService for the given transport type.
     */
    public static ExecutorService getExecutor(int transportType) {
        ExecutorService executorService = getExecutors().get(transportType);

        if (executorService == null) {
            executorService = Executors.newSingleThreadExecutor();
            getExecutors().put(transportType, executorService);
        }

        return executorService;
    }

    /**
     * Returns an executor specifically allocated for the given transport type.
     * The executor will be created for the given transport type if one does
     * not exist, and subsequent calls will always return the same executor.
     * Executors returned by this method will be single threaded, and allow for
     * delayed dispatching. This means that operations within the context of a
     * given transport type will run on the same thread.
     * @param transportType The transport type.
     * @return A ScheduledExecutorService for the given transport type.
     */
    public static ScheduledExecutorService getScheduledExecutor(int transportType) {
        ScheduledExecutorService scheduledExecutorService = getScheduledExecutors().get(transportType);

        // This is wrong, since the normal ExecutorServices will run in parallel
        // with these, which means that it's possible to achieve parallelism
        // within any given transport type. This defeats the purpose of using
        // single-threaded executors and must be reviewed.
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            getScheduledExecutors().put(transportType, scheduledExecutorService);
        }

        return scheduledExecutorService;
    }

    /**
     * Returns a general purpose ExecutorService. This will be used by
     * operations that are not related with any of the other executors, and
     * should be considered as a sort of "main thread" of the SDK.
     * @return A general purpose ExecutorService.
     */
    public static ExecutorService getMainExecutor() {
        if (ExecutorPool.mainExecutor == null) {
            ExecutorPool.mainExecutor = Executors.newSingleThreadExecutor();
        }
        return ExecutorPool.mainExecutor;
    }

    /**
     * This method returns the executor were "core" work is dispatched; this
     * will correspond to the work that sits in the Session, Transport and
     * Network layers, and is thus transport-agnostic. It may even be dispatched
     * through JNI, meaning that it's implemented natively. All of the work that
     * is dispatched here is dispatched in the same thread, so that there's no
     * issues with concurrency in the core.
     * @return The core ExecutorService.
     */
    public static ExecutorService getCoreExecutor() {
        if (ExecutorPool.coreExecutor == null) {
            ExecutorPool.coreExecutor = Executors.newSingleThreadExecutor();
        }
        return coreExecutor;
    }

    /**
     * Returns the ExecutorService used by the DriverManager. This will
     * correspond to the work that is dispatched with the intent of managing
     * drivers. This is a single-threaded executor, meaning that driver
     * management tasks will not overlap.
     * @return The ExecutorService for the DriverManager.
     */
    public static ExecutorService getDriverManagerExecutor() {
        if (ExecutorPool.driverManagerExecutor == null) {
            ExecutorPool.driverManagerExecutor = Executors.newSingleThreadExecutor();
        }
        return ExecutorPool.driverManagerExecutor;
    }

    public static ExecutorService getInternetExecutor() {
        if (ExecutorPool.internetExecutor == null) {
            ExecutorPool.internetExecutor = Executors.newSingleThreadExecutor();
        }
        return ExecutorPool.internetExecutor;
    }
}
