package com.uplink.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.uplink.ulx.ULX;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.bridge.Bridge;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.observers.MessageObserver;
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements StateObserver, NetworkObserver, MessageObserver {

     class LogTree extends Timber.DebugTree {
        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {
            if (sdkLogsEnabled) {
                logSDK(message);
            }
        }
    }

    private BluetoothManager bluetoothManager;

    private InstancesAdapter adapter;

    private HashMap<String, Instance> instanceMap;
    private Queue<Instance> probeQueue;
    private boolean probing = false;
    private boolean appLogsEnabled = true;
    private boolean sdkLogsEnabled = false;
    private ScrollView logScrollView;
    private TextView logTextView;
    private ScrollView appLogScrollView;
    private TextView appLogTextView;
    private SimpleDateFormat dateFormatter;

    private void logInfo(String message) {
        Log.i(getClass().getCanonicalName(), message);
        if (appLogsEnabled) {
            logApp(message);
        }
    }

    private void logError(String message) {
        Log.e(getClass().getCanonicalName(), message);
        if (appLogsEnabled) {
            logApp(message);
        }
    }

    @SuppressLint("SetTextI18n")
    private void logSDK(String message) {
        String formattedMessage = String.format("%s: %s", dateFormatter.format(new Date()), message);
        this.runOnUiThread(() -> {
            String currentLogText =
                    logTextView.getText().toString().isEmpty() ? "Beginning of log." : logTextView.getText().toString();
            logTextView.setText(currentLogText + "\n" + formattedMessage);
            logScrollView.post(() -> { logScrollView.fullScroll(View.FOCUS_DOWN); });
        });
    }

    @SuppressLint("SetTextI18n")
    private void logApp(String message) {
        String formattedMessage = String.format("%s: %s", dateFormatter.format(new Date()), message);
        this.runOnUiThread(() -> {
            String currentLogText =
                    appLogTextView.getText().toString().isEmpty() ? "Beginning of log." : appLogTextView.getText().toString();
            appLogTextView.setText(currentLogText + "\n" + formattedMessage);
            appLogScrollView.post(() -> { appLogScrollView.fullScroll(View.FOCUS_DOWN); });
        });
    }

    private HashMap<String, Instance> getInstanceMap() {
        if (this.instanceMap == null) {
            this.instanceMap = new HashMap<>();
        }
        return this.instanceMap;
    }

    private Queue<Instance> getProbeQueue() {
        if (this.probeQueue == null) {
            this.probeQueue = new LinkedList<>();
        }
        return this.probeQueue;
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {

                    // Register all observers
                    ULX.addStateObserver(this);
                    ULX.addNetworkObserver(this);
                    ULX.addMessageObserver(this);

                    // Configure the SDK and start
                    ULX.setContext(getApplicationContext());
                    ULX.setAppIdentifier("12345678");
                    ULX.start();

                } else {

                    // We'll end up here if the permissions are not accepted.
                    // They must be accepted for the app to behave properly.
                    logError( "ULX[APP] Permission rejected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adapter = new InstancesAdapter();
        this.<RecyclerView>findViewById(R.id.rvInstances).setAdapter(adapter);
        logTextView = this.findViewById(R.id.log_text_view);
        logScrollView = this.findViewById(R.id.log_scroll_view);
        appLogTextView = this.findViewById(R.id.app_log_text_view);
        appLogScrollView = this.findViewById(R.id.app_log_scroll_view);
        dateFormatter = new SimpleDateFormat("MMM d, HH:mm:ss", Locale.US);
        bluetoothManager = (BluetoothManager) getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);

        Timber.plant(new LogTree());

        // Request permissions (ACCESS_COARSE_LOCATION)
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Override
    public void onUlxStart() {
        logInfo("ULX[APP] has started. Device ID: " + ULX.getHostInstance().getStringIdentifier());
        runOnUiThread(() -> this.<TextView>findViewById(R.id.my_id).setText(ULX.getHostInstance().getStringIdentifier()));
    }

    private URL makeUrl() throws MalformedURLException {
        return null;
    }

    private JSONObject makeObject() throws JSONException {
        JSONObject obj = new JSONObject();
        return obj;
    }

    @Override
    public void onUlxStop(UlxError error) {
        logInfo( String.format("ULX[APP] has stopped [%s]", error.toString()));
    }

    @Override
    public void onUlxFailedStarting(UlxError error) {
        logInfo( String.format("ULX[APP] has failed starting [%s]", error.toString()));
    }

    @Override
    public void onUlxReady() {
        logInfo( "ULX[APP] became ready");

        // The adapter was probably turned off and then on again; just keep
        // trying to start for as long as the SDK tells us that we should.
        ULX.start();
    }

    @Override
    public void onUlxInstanceFound(Instance instance) {
        logInfo( String.format("ULX[APP] found instance [%s]", instance.getStringIdentifier()));

        //sendMessage(instance);
        getInstanceMap().put(instance.getStringIdentifier(), instance);
        this.runOnUiThread(() -> adapter.updateInstancesList(new ArrayList<>(getInstanceMap().keySet())));
    }

    @Override
    public void onUlxInstanceLost(Instance instance, UlxError error) {
        logInfo( String.format("ULX[APP] lost instance [%s: %s]", instance.getStringIdentifier(), error.toString()));

        getInstanceMap().remove(instance.getStringIdentifier());
        this.runOnUiThread(() -> adapter.updateInstancesList(new ArrayList<>(getInstanceMap().keySet())));
    }


    private void sendMessage(Instance instance) {
        String text = "Lorem ipsum dolor sit amet, consectetur adipiscing " +
                "elit, sed do eiusmod tempor incididunt ut labore et dolore " +
                "magna aliqua. Augue neque gravida in fermentum et sollicitudin " +
                "ac orci phasellus. Magna fermentum iaculis eu non diam " +
                "phasellus vestibulum. Tempor commodo ullamcorper a lacus " +
                "vestibulum sed arcu non odio. Massa tincidunt dui ut ornare " +
                "lectus sit amet. Mauris augue neque gravida in. Consequat " +
                "interdum varius sit amet mattis. Rhoncus dolor purus non enim " +
                "praesent elementum facilisis. Non tellus orci ac auctor augue. " +
                "Urna neque viverra justo nec. Egestas quis ipsum suspendisse " +
                "ultrices. Commodo viverra maecenas accumsan lacus vel facilisis " +
                "volutpat est. Rhoncus mattis rhoncus urna neque viverra justo " +
                "nec ultrices. Sollicitudin aliquam ultrices sagittis orci a. " +
                "Eget magna fermentum iaculis eu non. Ut sem viverra aliquet " +
                "eget sit amet. Volutpat consequat mauris nunc congue nisi vitae " +
                "suscipit. " +
                "Ultricies mi eget mauris pharetra et. Malesuada bibendum arcu " +
                "vitae elementum curabitur vitae. Sed augue lacus viverra vitae " +
                "congue eu consequat ac felis. Ullamcorper morbi tincidunt " +
                "ornare massa eget egestas purus. Orci a scelerisque purus " +
                "semper eget duis at tellus at. Nisl rhoncus mattis rhoncus urna " +
                "neque viverra justo. Leo duis ut diam quam nulla porttitor " +
                "massa. Mi bibendum neque egestas congue quisque egestas diam " +
                "in. Sagittis eu volutpat odio facilisis mauris sit amet massa. " +
                "Tortor pretium viverra suspendisse potenti nullam ac tortor. " +
                "Diam in arcu cursus euismod quis viverra nibh cras pulvinar. " +
                "Tellus cras adipiscing enim eu turpis egestas. Eu lobortis " +
                "elementum nibh tellus molestie nunc. Auctor augue mauris augue " +
                "neque gravida in fermentum et sollicitudin. Blandit massa enim " +
                "nec dui nunc mattis enim. Tincidunt vitae semper quis lectus. " +
                "Lectus mauris ultrices eros in cursus turpis massa tincidunt " +
                "dui. Non enim praesent elementum facilisis. Varius morbi enim " +
                "nunc faucibus. Lacus sed viverra tellus in hac habitasse platea " +
                "dictumst vestibulum. "
                ;

        // Encode the data using UTF-8
        byte[] data = text.getBytes(StandardCharsets.UTF_8);

        logInfo( String.format("ULX[APP] is sending %d bytes", text.getBytes().length));
        logInfo( "ULX[APP] sending message");

        // Send "Hello World"
        ULX.send(data, instance);
    }

    @Override
    public void onUlxMessageReceived(byte[] data, Instance origin) {
        String text = new String(data, StandardCharsets.UTF_8);

        logInfo( String.format("ULX[APP] message received: from %s: %s", origin.getStringIdentifier(), text));
    }

    @Override
    public void onUlxMessageFailedSending(MessageInfo messageInfo, UlxError error) {
        logInfo( String.format("ULX[APP] failed to send message [%s]", error.toString()));
    }

    @Override
    public void onUlxMessageSent(MessageInfo messageInfo) {
        if (messageInfo != null) {
            logInfo( String.format("ULX[APP] message [%d] was sent", messageInfo.getIdentifier()));
        }

        // DO NOT USE THIS METHOD
    }

    @Override
    public void onUlxMessageDelivered(MessageInfo messageInfo) {
        logInfo( String.format("ULX[APP] message [%d] was delivered", messageInfo.getIdentifier()));

        // Probe?
        dequeue();
    }

    @Override
    public void onUlxInternetResponse(int code, String content) {
        logInfo( String.format("ULX[APP] Server response is: %s  code: %d ", content, code));
    }

    @Override
    public void onUlxInternetResponseFailure(UlxError error) {
        logInfo( String.format("ULX[APP] failed sending an Internet request [%s]", error.toString()));
    }

    public void onSendTransactionClick(View view) {
        logInfo( "ULX[APP] Sending transaction");

        try {
            ULX.sendInternet(makeUrl(), makeObject(), 2);
        } catch (MalformedURLException | JSONException e) {
            throw new RuntimeException("Malformed URL or JSON exception");
        }
    }

    public void onSendMessageClick(View view) {

        if (getInstanceMap().isEmpty()) {
            logError( "ULX[APP] No known instances");
            return;
        }

        Set<String> keySet = getInstanceMap().keySet();
        Instance instance = getInstanceMap().get(keySet.iterator().next());

        if (instance == null) {
            logError( "ULX[APP] Instance is null");
            return;
        }

        logInfo( String.format("ULX[APP] Sending message to %s", instance.getStringIdentifier()));

        byte[] data = "message".getBytes(StandardCharsets.UTF_8);

        ULX.send(data, instance);
    }

    public void onProbeClick(View view) {

        probing = true;

        // Send "probe" to all instances
        for (Map.Entry<String, Instance> entry : getInstanceMap().entrySet()) {
            getProbeQueue().add(entry.getValue());
        }

        dequeue();
    }

    public void onPrintConnectedDevicesClick(View view) {
        @SuppressLint("MissingPermission") List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        logApp("Logging connected devices " + devices.toString());
    }

    public void onPrintRoutingTableClick(View view) {
        @SuppressLint("MissingPermission") List<BluetoothDevice> devices = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        Bridge.getInstance().printRoutingTable();
    }

    public void onSDKLogClick(View view) {
        this.sdkLogsEnabled = !this.sdkLogsEnabled;

        if (sdkLogsEnabled) {
            this.<Button>findViewById(R.id.sdk_log_button).setText("Disable SDK Log");
        } else {
            this.<Button>findViewById(R.id.sdk_log_button).setText("Enable SDK Log");
        }
    }

    public void onAppLogClick(View view) {
        this.appLogsEnabled = !this.appLogsEnabled;

        if (appLogsEnabled) {
            this.<Button>findViewById(R.id.app_log_button).setText("Disable App Log");
        } else {
            this.<Button>findViewById(R.id.app_log_button).setText("Enable App Log");
        }
    }

    private void dequeue() {

        if (!probing) {
            return;
        }

        Instance instance = getProbeQueue().poll();

        if (instance == null) {
            logInfo( "ULX[APP] Done probing");
            probing = false;
            return;
        }

        logInfo( String.format("ULX[APP] Probing instance %s", instance.getStringIdentifier()));

        byte[] data = "probe".getBytes(StandardCharsets.UTF_8);
        ULX.send(data, instance);
    }
}
