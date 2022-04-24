package com.uplink.ulx.utils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

/**
 * This class is similar to a serial {@link Executor} in that it executes tasks sequentially, but
 * the tasks can be asynchronous {@link #enqueue(Task)} returns a {@link Completable} which can be
 * marked as complete either during {@link Task#run(Completable)} or at any other moment. This is
 * especially useful when a task runs a request, for which we will get a response via some callback
 * later. Sample usage:
 * <pre> {@code
 * SerialOperationsManager manager = new SerialOperationsManager(
 *      new HandlerExecutor(new Handler(Looper.getMainLooper()))
 * );
 *
 * // ...
 *
 * this.completable = manager.enqueue(completable -> gatt.writeCharacteristic(characteristic));
 *
 * //...
 *
 * public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
 *      this.completable.markAsComplete();
 *      // Handle the result
 * }
 *
 * } </pre>
 * The class is thread-safe as long as the {@link Executor} passed to its constructor and the one
 * returned by {@link Executors#newSingleThreadExecutor()} are
 */
public class SerialOperationsManager {
    /**
     * The executor which will run the submitted operations.
     */
    private final Executor operationsExecutor;
    /**
     * The executor which will run the queue and wait for the tasks to complete
     */
    private final ExecutorService queueExecutor;

    /**
     * Constructor.
     *
     * @param executor an executor which will run the operations submitted to {@link
     *                 #enqueue(Task)}. In case of BLE, it will most likely be using the main
     *                 thread
     */
    public SerialOperationsManager(Executor executor) {
        this.operationsExecutor = executor;
        queueExecutor = Executors.newSingleThreadExecutor();
    }

    /**
     * Enqueue a new task for execution.
     *
     * @param task task to execute. It doesn't need to complete all of the work during its {@link
     *             Task#run(Completable)} method. The task is considered to be complete only after
     *             the {@link Completable#markAsComplete()} is called
     * @return {@link Completable} for marking the task as complete. It is the same object that will
     * be passed to {@link Task#run(Completable)}. {@link Completable#markAsComplete()} can be
     * called at any point - before, during or after {@link Task#run(Completable)} is called.
     * Although it usually doesn't make sense, completing before the task is run will make it not
     * run at all. {@link Completable#markAsComplete()} is idempotent
     */
    public Completable enqueue(Task task) {
        return enqueue(task, 0);
    }

    /**
     * Enqueue a new task for execution.
     *
     * @param task      task to execute. It doesn't need to complete all of the work during its
     *                  {@link Task#run(Completable)} method. The task is considered to be complete
     *                  only after the {@link Completable#markAsComplete()} is called
     * @param timeoutMs timeout, during which {@link Completable#markAsComplete()} is expected to be
     *                  called. Countdown starts after {@link Task#run(Completable)} exits. A value
     *                  of zero means no timeout
     * @return {@link Completable} for marking the task as complete. It is the same object that will
     * be passed to {@link Task#run(Completable)}. {@link Completable#markAsComplete()} can be
     * called at any point - before, during or after {@link Task#run(Completable)} is called.
     * Although it usually doesn't make sense, completing before the task is run will make it not
     * run at all. {@link Completable#markAsComplete()} is idempotent
     */
    public Completable enqueue(Task task, int timeoutMs) {
        final CountDownLatch completionLatch = new CountDownLatch(1);
        // Calling markComplete() will countdown the latch, thus allowing the queue to proceed
        final Completable completable = completionLatch::countDown;

        final Runnable operationRunner = () -> {

            final FutureTask<Void> future = new FutureTask<>(
                    () -> {
                        if (completionLatch.getCount() > 0) {
                            task.run(completable);
                        } // else the task is already complete
                    },
                    null
            );

            try {
                // Submit the task to the operations executor
                operationsExecutor.execute(future);
            } catch (RejectedExecutionException e) {
                Timber.w(e, "Failed to enqueue operation");
                // Do not try waiting for the task - it will never complete
                return;
            }

            try {
                try {
                    // Wait for the operation to run
                    future.get();
                } catch (ExecutionException e) {
                    Timber.w(e, "Failed to execute operation");
                    // Do not wait for the completion flag - the task has failed
                    return;
                }

                // Wait for the completion flag to be set
                final boolean isTimeout;
                if (timeoutMs > 0) {
                    // No timeout - wait as long as needed
                    completionLatch.await();
                    isTimeout = false;
                } else if (isTimeout = !completionLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    Timber.i("Timeout was hit while waiting for the operation's completion");
                }

                operationsExecutor.execute(() -> task.onComplete(isTimeout));

            } catch (InterruptedException e) {
                // This should only happen if destroy() was called. So we won't wait for the
                // operation to complete anymore
                Timber.d(e, "Queue executor's thread was interrupted");

                future.cancel(true);

                // Reset the interruption flag
                Thread.currentThread().interrupt();
            }
        };

        try {
            queueExecutor.execute(operationRunner);
        } catch (RejectedExecutionException e) {
            Timber.w(
                    e,
                    "Failed to enqueue operation, because the manager has already been destroyed"
            );
        }
        return completable;
    }

    /**
     * Halt processing of the pending tasks and attempt to stop the current one (if any.) Further
     * attempts to enqueue tasks will be no-ops
     */
    public void destroy() {
        final List<Runnable> pendingTasks = queueExecutor.shutdownNow();
        Timber.i(
                "SerialOperationsManager was shut down. %d task(s) were cancelled",
                pendingTasks.size()
        );
    }

    public interface Task {
        /**
         * Run the given task
         *
         * @param completable A {@link Completable} that needs to be marked as complete when the
         *                    task is finished. This does not need to happen inside the method
         */
        void run(Completable completable);

        /**
         * Called when the task is complete. The call is submitted to the executor passed to the
         * {@link SerialOperationsManager}'s constructor
         *
         * @param isTimeout whether the completion is by timeout
         */
        default void onComplete(boolean isTimeout) {
        }
    }
}
