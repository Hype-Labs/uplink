package com.uplink.ulx;

import android.content.Context;

import com.uplink.ulx.drivers.commons.StateManager;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.model.State;
import com.uplink.ulx.observers.MessageObserver;
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;
import com.uplink.ulx.service.Service;
import com.uplink.ulx.threading.ExecutorPool;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import timber.log.Timber;

/**
 * The Implementation class bridges the facade with the actual implementation
 * of the SDK. The main purpose of implementing things here instead of the
 * main facade class is to prevent users from having to deal with the singleton
 * instance, making calls such as ULX.start() instead of
 * ULX.getInstance().start(). The ULX class, thus, provides syntactic sugar as
 * an abstraction over this implementation.
 *
 * The Implementation class maintains the collections of observers and known
 * instances, meaning that it's the main in-memory storage point ƒor classes
 * that interact with the SDK.
 *
 * From a functional perspective, it initializes and starts the background
 * service, interacting with it by propagating calls and responses from the
 * delegates. This makes it a facade implementation as well, since it exposes
 * an API that enables consuming all features within the SDK.
 */
public class Implementation implements
        StateManager.Delegate,
        Service.StateDelegate,
        Service.NetworkDelegate,
        Service.MessageDelegate
{

    private static Implementation instance;

    private WeakReference<Context> context;
    private Instance hostInstance;
    private String appIdentifier;

    private List<StateObserver> stateObservers;
    private List<NetworkObserver> networkObservers;
    private List<MessageObserver> messageObservers;

    private StateManager stateManager;
    private Service service;

    private static final int MAX_MESSAGE_IDENTIFIER = 65536;
    private static int messageIdentifier = 0;

    /**
     * Private constructor prevents instantiation.
     */
    private Implementation() {
        this.service = null;
        this.stateManager = null;
    }

    /**
     * Protected accessor instantiates and returns the singleton instance.
     * @return The singleton instance.
     */
    protected static synchronized Implementation getInstance() {
        if (Implementation.instance == null) {
            Implementation.instance = new Implementation();
        }
        return Implementation.instance;
    }

    /**
     * Getter for the underlying background service. The service is initialized
     * by requesting activity from the SDK, until which point this method
     * returns null.
     * @return The service, if one has been initialized, and null otherwise.
     */
    private synchronized Service getService() {
        return this.service;
    }

    /**
     * Getter and factory for the StateManager managing the class's lifecycle.
     * @return The instance's StateManager.
     */
    private synchronized StateManager getStateManager() {
        if (this.stateManager == null) {
            this.stateManager = new StateManager(this);
        }
        return this.stateManager;
    }

    /**
     * This method is a getter for the framework's state. The state is read
     * from the Service, whose state corresponds to the DriverManager. In other
     * words, the state is a reflection of the DriverManager's state, and is
     * queried from that whenever needed. Until the service is bound, the
     * state will be determined according to whether the background service has
     * already been requested to initialize, reflecting the idea that the
     * framework is undergoing initialization.
     * @return The framework's state.
     */
    public synchronized State getState() {
        return getStateManager().getState();
    }

    /**
     * Setter for the host instance, called when the framework is first
     * initialized.
     * @param hostInstance The instance for the host device.
     */
    private void setHostInstance(Instance hostInstance) {
        this.hostInstance = hostInstance;
    }

    /**
     * Getter for the local Instance object. This getter returns the Instance
     * object associated with the domestic instance, created in the host device.
     * This returns null until the framework is first requested to start. This
     * object is then kept throughout the framework's lifecycle.
     * @return The local host instance or null, if not initialized yet.
     */
    public final synchronized Instance getHostInstance() {
        return this.hostInstance;
    }

    /**
     * This setter must be called before starting the framework, or an exception
     * will be raised. It only allows setting the context once. This method is
     * thread safe and in case several concurrent threads attempting to set a
     * context, only the first to get a lock will succeed. All others will be
     * no-ops. The context persists for the duration of the singleton instance.
     * @param context The context to set.
     */
    public final synchronized void setContext(Context context) {

        Objects.requireNonNull(context);

        if (this.context == null) {
            this.context = new WeakReference<>(context);
        }
    }

    /**
     * Getter for the Android Context. When called, if the context has not been
     * set, this method raises an exception. The context must be set before
     * anything else is called.
     * @return The Android app context.
     */
    private synchronized Context getContext() {
        if (this.context == null) {
            throw new RuntimeException("The context must be set before starting the services.");
        }
        return this.context.get();
    }

    /**
     * Setter for the app identifier, which will be used by the framework to
     * distinguish between different implementations on the network.
     * @param appIdentifier The app identifier to set.
     */
    public final synchronized void setAppIdentifier(String appIdentifier) {
        this.appIdentifier = appIdentifier;
    }

    /**
     * The App Identifier is a unique identifier that each app uses to
     * distinguish itself from other apps. This getter returns such identifier.
     * @return The app identifier in use by the framework.
     */
    public final String getAppIdentifier() {
        return this.appIdentifier;
    }

    /**
     * Returns a list of state observers, creating a new one if needed.
     * @return The list of StateObserver instances.
     * @see StateObserver
     */
    protected final List<StateObserver> getStateObservers() {
        if (this.stateObservers == null) {
            this.stateObservers = new ArrayList<>();
        }
        return this.stateObservers;
    }

    /**
     * Returns a list of message observers, creating a new one if needed.
     * @return The list of MessageObserver instances.
     * @see MessageObserver
     */
    protected final List<MessageObserver> getMessageObservers() {
        if (this.messageObservers == null) {
            this.messageObservers = new ArrayList<>();
        }
        return this.messageObservers;
    }

    /**
     * Returns a list of network observers, creating a new one if needed.
     * @return The list of NetworkObserver instances.
     * @see NetworkObserver
     */
    protected final List<NetworkObserver> getNetworkObservers() {
        if (this.networkObservers == null) {
            this.networkObservers = new ArrayList<>();
        }
        return this.networkObservers;
    }

    /**
     * Adds a message observer. After being added, the observer will get
     * notifications for message states and progress tracking. Notifications
     * will be triggered when messages are received, when they fail delivery,
     * or when a message is delivered. If the observer has previously already
     * been registered, it will not be registered twice, and the method will
     * do nothing.
     * @param messageObserver The message observer (MessageObserver) to add.
     * @see MessageObserver
     */
    public synchronized void addMessageObserver(MessageObserver messageObserver) {
        if (!getMessageObservers().contains(messageObserver)) {
            getMessageObservers().add(messageObserver);
        }
    }

    /**
     * This method removes a message observer (MessageObserver) that was
     * previously registered with `addMessageObserver`. If the observer was not
     * previously registered or has already been removed, this method does
     * nothing. After being removed, the observer will no longer get any
     * notifications from the SDK.
     * @param messageObserver The message observer (MessageObserver) to remove.
     * @see MessageObserver
     */
    public synchronized void removeMessageObserver(MessageObserver messageObserver) {
        getMessageObservers().remove(messageObserver);
    }

    /**
     * Adds a network observer. Network observers get notifications for network
     * events, such as instances being found and lost on the network. The
     * network observer (NetworkObserver) being added will get notifications
     * after a call to this method. If the observer has already been registered,
     * this method does nothing, and the observer will not get duplicated events.
     * @param networkObserver The network observer (NetworkObserver) to add.
     * @see NetworkObserver
     */
    public synchronized void addNetworkObserver(NetworkObserver networkObserver) {
        if (!getNetworkObservers().contains(networkObserver)) {
            getNetworkObservers().add(networkObserver);
        }
    }

    /**
     * This method removes a previously registered network observer
     * NetworkObserver). If the observer has not been registered or has already
     * been removed, this method does nothing. After being removed, the observer
     * will no longer get notifications from the SDK.
     * @param networkObserver The network observer (NetworkObserver) to remove.
     * @see NetworkObserver
     */
    public synchronized void removeNetworkObserver(NetworkObserver networkObserver) {
        getNetworkObservers().remove(networkObserver);
    }

    /**
     * Adds a state observer. State observers get notifications for state and
     * lifecycle events. If the observer has already been registered it will not
     * be registered twice, preventing the observer from getting duplicate
     * notifications.
     * @param stateObserver The state observer (StateObserver) to register.
     * @see StateObserver
     */
    public synchronized void addStateObserver(StateObserver stateObserver) {
        if (!getStateObservers().contains(stateObserver)) {
            getStateObservers().add(stateObserver);
        }
    }

    /**
     * This method removes a previously registered state observer
     * (StateObserver). If the observer is not present in the registry, because
     * it was not added or because it has already been removed, this method does
     * nothing. After being removed, the observer will no longer get
     * notifications from the SDK.
     * @param stateObserver The state observer (StateObserver) to remove.
     * @see StateObserver
     */
    public synchronized void removeStateObserver(StateObserver stateObserver) {
        getStateObservers().remove(stateObserver);
    }

    /**
     * Calling this method requests the framework to start its services, by
     * publishing itself on the network and browsing for other devices. Before
     * that happens, the framework must first initialize some configurations,
     * such as binding with the background service. These happen when this
     * method is called for the first time.
     */
    public synchronized void start() {
        getStateManager().start();
    }

    @Override
    public void requestStart(StateManager stateManager) {

        // This initialization shouldn't be here; when we have the service is
        // in place, the service will be constructed by the system. Instead,
        // this method should bind the service, and wait for the bind to occur
        // before proceeding with the initialization.
        this.service = new Service();
        this.service.setStateDelegate(this);
        this.service.setNetworkDelegate(this);
        this.service.setMessageDelegate(this);

        this.service.setContext(getContext());

        getService().initialize(getAppIdentifier());
    }

    @Override
    public void onInitialization(Service service, Instance hostInstance) {

        // Keep the initialized host instance
        setHostInstance(hostInstance);

        // After the service has been initialized, the driver manager is
        // requested to start. Any stop() requests that happened in the
        // meanwhile will still count towards reverting when the process is
        // complete (this is true until we notify the StateManager of the
        // result of the start process).
        getService().start();
    }

    @Override
    public void onStart(Service service) {
        getStateManager().notifyStart();
    }

    @Override
    public void onStop(Service service, UlxError error) {
        getStateManager().notifyStop(error);
    }

    @Override
    public void onFailedStart(Service service, UlxError error) {
        getStateManager().notifyFailedStart(error);
    }

    @Override
    public void onReady(Service service) {
        notifyReady();
    }

    @Override
    public void onStart(StateManager stateManager) {
        notifyStart();
    }

    /**
     * Runs through the list of StateObservers and notifies each that a start
     * request completed successfully, by calling the onUlxStart() delegate
     * method.
     */
    private void notifyStart() {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (StateObserver stateObserver : getStateObservers()) {
                stateObserver.onUlxStart();
            }
        });
    }

    /**
     * Runs through the list of StateObservers and notifies each that a previous
     * forced stoppage or failed start request may now be recoverable.
     */
    private void notifyReady() {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (StateObserver stateObserver : getStateObservers()) {
                stateObserver.onUlxReady();
            }
        });
    }

    /**
     * Calling this method requests the framework to stop its services by no
     * longer publishing itself on the network nor browsing for other instances.
     * This does not imply previously found instances to be lost; ongoing
     * operations should continue and communicating with known instances should
     * still be possible, but the framework will no longer find or be found by
     * other instances on direct link. However, instances may still be found in
     * mesh, when enabled.
     */
    public synchronized void stop() {
        getStateManager().stop();
    }

    @Override
    public void requestStop(StateManager stateManager) {
        // The driver manager stops first. When that's done, the service
        // should also stop and be destroyed, but the stoppage is done in
        // reverse order with respect to starting.
        getService().stop();
    }

    @Override
    public void onStop(StateManager stateManager, UlxError error) {
        notifyStop(error);
    }

    /**
     * Runs through the list of StateObservers and notifies each that a stop
     * request completed successfully, or that a forced stoppage occurred, in
     * which case an error is given. This is accomplished by calling the
     * delegate method onULXStop(ULXError).
     * @param error An error, which is present in case of a forced stoppage.
     */
    private void notifyStop(final UlxError error) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (StateObserver stateObserver : getStateObservers()) {
                stateObserver.onUlxStop(error);
            }
        });
    }

    @Override
    public void onFailedStart(StateManager stateManager, UlxError error) {
        notifyFailedStart(error);
    }

    /**
     * Runs through the list of StateObservers and notifies each registered
     * instance that a start request failed to complete. An error will indicate
     * the cause for the failure. This is accomplished by calling the
     * onULXFailedStarting(ULXError) delegate method.
     * @param error The error to propagate to the delegates.
     */
    private void notifyFailedStart(final UlxError error) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (StateObserver stateObserver : getStateObservers()) {
                stateObserver.onUlxFailedStarting(error);
            }
        });
    }

    @Override
    public void onStateChange(StateManager stateManager) {
        notifyStateChange();
    }

    /**
     * Notifies each registered StateObserver instance that a state change
     * has occurred. This is accomplished by calling the delegate method
     * onULXStateChange().
     */
    private void notifyStateChange() {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (StateObserver stateObserver : getStateObservers()) {
                stateObserver.onUlxStateChange();
            }
        });
    }

    @Override
    public void onInstanceFound(Service service, Instance instance) {
        Timber.i("ULX instance found %s", instance.getStringIdentifier());
        notifyInstanceFound(instance);
    }

    /**
     * Propagates the notification of an {@code Instance} being found to all
     * network observers.
     * @param instance The {@code Instance} that was found.
     */
    private void notifyInstanceFound(Instance instance) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (NetworkObserver networkObserver : getNetworkObservers()) {
                networkObserver.onUlxInstanceFound(instance);
            }
        });
    }

    @Override
    public void onInstanceLost(Service service, Instance instance, UlxError error) {
        Timber.i("ULX lost instance %s", instance.getStringIdentifier());
        notifyInstanceLost(instance, error);
    }

    /**
     * Propagates the notification of an {@code Instance} being lost to all
     * network observers.
     * @param instance The {@code Instance} that was lost.
     * @param error An error, describing the cause for the loss.
     */
    private void notifyInstanceLost(Instance instance, UlxError error) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (NetworkObserver networkObserver : getNetworkObservers()) {
                networkObserver.onUlxInstanceLost(instance, error);
            }
        });
    }

    /**
     * This method attempts to send a message to a given instance. The instance
     * must be a previously found and not lost instance, or else this method
     * fails with an error. It returns immediately (non blocking), queues the
     * data to be sent, and returns the message (Message) that was created for
     * wrapping the data.
     * @param data The data to be sent.
     * @param destination The destination instance.
     * @param acknowledge Whether to track delivery progress.
     * @return A message wrapper containing some metadata.
     */
    public Message send(byte [] data, Instance destination, boolean acknowledge) {

        Objects.requireNonNull(data);
        Objects.requireNonNull(destination);

        // Create the Message container
        int messageIdentifier = generateMessageIdentifier();
        MessageInfo messageInfo = new MessageInfo(messageIdentifier, destination);
        Message message = new Message(messageInfo, data);

        if (getState() == State.RUNNING) {
            getService().send(message);
        } else {

            // The service is not available
            UlxError error = new UlxError(
                    UlxErrorCode.NOT_CONNECTED,
                    "Failed to send data to the given destination.",
                    "The background service was not initialized or failed to start.",
                    "Try restarting the app."
            );

            // Notify the delegate
            notifyOnMessageSendFailed(messageInfo, error);
        }

        return message;
    }

    /**
     * Incrementally generates message identifiers. Everytime this is called, a
     * new message identifier is returned, equal to the previous one added by 1.
     * When the maximum message ID is reached, the values cycle back to zero,
     * effectively restarting the numeration sequence.
     * @return A new message identifier.
     */
    private static int generateMessageIdentifier() {

        Implementation.messageIdentifier++;

        if (Implementation.messageIdentifier == MAX_MESSAGE_IDENTIFIER) {
            Implementation.messageIdentifier = 0;
        }

        return Implementation.messageIdentifier;
    }

    @Override
    public void onMessageSent(Service service, MessageInfo messageInfo) {
        notifyOnMessageSent(messageInfo);
    }

    @Override
    public void onMessageSendFailed(Service service, MessageInfo messageInfo, UlxError error) {
        notifyOnMessageSendFailed(messageInfo, error);
    }

    @Override
    public void onMessageReceived(Service service, byte[] data, Instance origin) {
        notifyOnMessageReceived(data, origin);
    }

    @Override
    public void onMessageDelivered(Service service, MessageInfo messageInfo) {
        notifyOnMessageDelivered(messageInfo);
    }

    @Override
    public void onInternetResponse(Service service, int code, String content) {
        notifyOnInternetResponse(code, content);
    }

    @Override
    public void onInternetRequestFailure(Service service, UlxError error) {
        notifyOnInternetRequestFailure(error);
    }

    /**
     * Propagates a notification of a failed attempt to send a message to the
     * {@link MessageObserver} collection, by calling the corresponding event
     * method {@link
     * MessageObserver#onUlxMessageFailedSending(MessageInfo, UlxError)}.
     * @param messageInfo The {@link MessageInfo} for the failed {@link Message}.
     * @param error An error, describing the probable cause for the failure.
     */
    private void notifyOnMessageSendFailed(MessageInfo messageInfo, UlxError error) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxMessageFailedSending(messageInfo, error);
            }
        });
    }

    /**
     * Propagates a notification of a message being sent event to the {@link
     * MessageObserver} collection, by calling the corresponding event method
     * {@link MessageObserver#onUlxMessageSent(MessageInfo)}.
     * @param messageInfo The {@link MessageInfo} for the sent {@link Message}.
     */
    private void notifyOnMessageSent(MessageInfo messageInfo) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxMessageSent(messageInfo);
            }
        });
    }

    /**
     * Propagates a notification for an event of a message being received to
     * the {@link MessageObserver} collection, by calling the corresponding
     * event method {@link MessageObserver#onUlxMessageReceived(byte[], Instance)}.
     * @param data The data that was received.
     * @param origin The originating {@link Instance}.
     */
    private void notifyOnMessageReceived(byte[] data, Instance origin) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxMessageReceived(data, origin);
            }
        });
    }

    /**
     * Propagates a notification for an event of a message being delivered,
     * which corresponds to the destination having acknowledged the reception
     * of the contents in full. The propagation occurs by calling {@link
     * MessageObserver#onUlxMessageDelivered(MessageInfo)}.
     * @param messageInfo The {@link MessageInfo} meta data corresponding to
     *                    the original message.
     */
    private void notifyOnMessageDelivered(MessageInfo messageInfo) {

        Objects.requireNonNull(messageInfo);

        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxMessageDelivered(messageInfo);
            }
        });
    }

    private void notifyOnInternetResponse(int code, String content) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxInternetResponse(code, content);
            }
        });
    }

    private void notifyOnInternetRequestFailure(UlxError error) {
        ExecutorPool.getMainExecutor().execute(() -> {
            for (MessageObserver messageObserver : getMessageObservers()) {
                messageObserver.onUlxInternetResponseFailure(error);
            }
        });
    }

    public void sendInternet(URL url, JSONObject jsonObject, int test) {

        Objects.requireNonNull(url);
        Objects.requireNonNull(jsonObject);

        getService().sendInternet(url, jsonObject, test);
    }
}
