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
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;

public class MainActivity extends AppCompatActivity implements StateObserver, NetworkObserver {

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {

                    ULX.addStateObserver(this);
                    ULX.addNetworkObserver(this);

                    ULX.setContext(getApplicationContext());
                    ULX.setAppIdentifier("12345678");
                    ULX.start();

                } else {
                    Log.i(getClass().getCanonicalName(), "ULX[APP] Permission rejected");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        ULX.start();
    }

    @Override
    public void onUlxStateChange() {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] changed state [new state is %s]", ULX.getState().toString()));
    }

    @Override
    public void onUlxInstanceFound(Instance instance) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] found instance [%s]", instance.getStringIdentifier()));
    }

    @Override
    public void onUlxInstanceLost(Instance instance, UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] lost instance [%s: %s]", instance.getStringIdentifier(), error.toString()));
    }
}
