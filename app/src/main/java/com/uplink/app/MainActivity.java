package com.uplink.app;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.uplink.ulx.ULX;
import com.uplink.ulx.UlxError;
import com.uplink.ulx.model.Instance;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.observers.MessageObserver;
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;

import java.nio.charset.StandardCharsets;
import java.util.Timer;

public class MainActivity extends AppCompatActivity implements StateObserver, NetworkObserver, MessageObserver {

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
                    Log.e(getClass().getCanonicalName(), "ULX[APP] Permission rejected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request permissions (ACCESS_COARSE_LOCATION)
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    @Override
    public void onUlxStart() {
        Log.i(getClass().getCanonicalName(), "ULX[APP] has started");
    }

    @Override
    public void onUlxStop(UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] has stopped [%s]", error.toString()));
    }

    @Override
    public void onUlxFailedStarting(UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] has failed starting [%s]", error.toString()));
    }

    @Override
    public void onUlxReady() {
        Log.i(getClass().getCanonicalName(), "ULX[APP] became ready");

        // The adapter was probably turned off and then on again; just keep
        // trying to start for as long as the SDK tells us that we should.
        ULX.start();
    }

    @Override
    public void onUlxInstanceFound(Instance instance) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] found instance [%s]", instance.getStringIdentifier()));

        sendMessage(instance);
    }

    @Override
    public void onUlxInstanceLost(Instance instance, UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] lost instance [%s: %s]", instance.getStringIdentifier(), error.toString()));
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

        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] is sending %d bytes", text.getBytes().length));
        Log.i(getClass().getCanonicalName(), "ULX[APP] sending message");

        // Send "Hello World"
        ULX.send(data, instance);
    }

    @Override
    public void onUlxMessageReceived(byte[] data, Instance origin) {
        String text = new String(data, StandardCharsets.UTF_8);

        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] message received: from %s: %s", origin.getStringIdentifier(), text));
    }

    @Override
    public void onUlxMessageFailedSending(MessageInfo messageInfo, UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] failed to send message [%s]", error.toString()));
    }

    @Override
    public void onUlxMessageSent(MessageInfo messageInfo) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] message [%d] was sent", messageInfo.getIdentifier()));

        // DO NOT USE THIS METHOD
    }

    @Override
    public void onUlxMessageDelivered(MessageInfo messageInfo) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] message [%d] was delivered", messageInfo.getIdentifier()));

        // Flood the network
        sendMessage(messageInfo.getDestination());
    }
}
