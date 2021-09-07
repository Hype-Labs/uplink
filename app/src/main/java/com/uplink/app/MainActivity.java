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
import com.uplink.ulx.model.Message;
import com.uplink.ulx.model.MessageInfo;
import com.uplink.ulx.observers.MessageObserver;
import com.uplink.ulx.observers.NetworkObserver;
import com.uplink.ulx.observers.StateObserver;

import java.nio.charset.StandardCharsets;

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
        String message = "Lorem ipsum dolor sit amet, consectetur adipiscing " +
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
                /*
                "Orci phasellus egestas tellus rutrum tellus pellentesque eu. " +
                "Massa enim nec dui nunc mattis enim ut. Duis at tellus at urna " +
                "condimentum mattis pellentesque id. Ut faucibus pulvinar " +
                "elementum integer enim neque volutpat ac tincidunt. Turpis " +
                "egestas integer eget aliquet nibh praesent tristique magna sit. " +
                "Morbi tincidunt ornare massa eget egestas purus viverra " +
                "accumsan in. Gravida rutrum quisque non tellus orci ac. Euismod " +
                "quis viverra nibh cras pulvinar mattis nunc. Adipiscing vitae " +
                "proin sagittis nisl rhoncus mattis rhoncus urna. Elementum " +
                "pulvinar etiam non quam lacus suspendisse faucibus interdum. " +
                "Lacus vestibulum sed arcu non odio euismod lacinia at quis. " +
                "Aenean et tortor at risus. Amet risus nullam eget felis eget. " +
                "Tellus in metus vulputate eu scelerisque felis. Nunc sed augue " +
                "lacus viverra vitae congue. Enim lobortis scelerisque fermentum " +
                "dui faucibus in ornare quam viverra. Ut ornare lectus sit amet. " +
                "Orci eu lobortis elementum nibh tellus molestie nunc non " +
                "blandit. Velit aliquet sagittis id consectetur purus ut. " +
                "Fermentum dui faucibus in ornare quam viverra orci sagittis eu. " +
                "Id semper risus in hendrerit gravida rutrum quisque non. " +
                "Integer vitae justo eget magna fermentum iaculis eu. Ut morbi " +
                "tincidunt augue interdum velit. Cursus turpis massa tincidunt " +
                "dui ut. Integer quis auctor elit sed vulputate mi sit amet. " +
                "Lectus vestibulum mattis ullamcorper velit sed ullamcorper " +
                "morbi tincidunt ornare. Phasellus faucibus scelerisque eleifend " +
                "donec pretium vulputate sapien nec sagittis. Sollicitudin " +
                "aliquam ultrices sagittis orci a scelerisque purus semper eget. " +
                "Donec enim diam vulputate ut pharetra. Ultrices in iaculis nunc " +
                "sed augue. Odio ut enim blandit volutpat maecenas volutpat " +
                "blandit aliquam. Dui nunc mattis enim ut tellus elementum " +
                "sagittis vitae. " +
                "Arcu ac tortor dignissim convallis aenean et tortor at risus. " +
                "Aliquet lectus proin nibh nisl condimentum id venenatis a. Et " +
                "magnis dis parturient montes nascetur ridiculus mus mauris " +
                "vitae. Diam vulputate ut pharetra sit amet aliquam id diam. " +
                "Porta lorem mollis aliquam ut porttitor leo a. Purus in mollis " +
                "nunc sed id semper risus in. Nibh sit amet commodo nulla " +
                "facilisi nullam vehicula. Viverra ipsum nunc aliquet bibendum " +
                "enim facilisis. Pellentesque dignissim enim sit amet venenatis " +
                "urna cursus eget. Sit amet tellus cras adipiscing enim. Elit " +
                "ullamcorper dignissim cras tincidunt lobortis feugiat vivamus " +
                "at augue. Ipsum dolor sit amet consectetur adipiscing elit ut. " +
                "Ac tortor dignissim convallis aenean et tortor at. Eget magna " +
                "fermentum iaculis eu non. Sagittis aliquam malesuada bibendum " +
                "arcu. Ac turpis egestas integer eget aliquet nibh praesent " +
                "tristique. " +
                "At consectetur lorem donec massa sapien. Sed augue lacus " +
                "viverra vitae congue eu consequat ac felis. Pulvinar elementum " +
                "integer enim neque. Rhoncus dolor purus non enim praesent " +
                "elementum. Tristique risus nec feugiat in fermentum posuere " +
                "urna nec tincidunt. Morbi tempus iaculis urna id. Elementum eu " +
                "facilisis sed odio. Egestas diam in arcu cursus euismod quis. " +
                "Aenean et tortor at risus viverra adipiscing. Sed lectus " +
                "vestibulum mattis ullamcorper. " +
                "Eget nulla facilisi etiam dignissim diam quis. Consectetur " +
                "lorem donec massa sapien faucibus et molestie. Venenatis tellus " +
                "in metus vulputate eu scelerisque felis imperdiet proin. Erat " +
                "pellentesque adipiscing commodo elit at imperdiet dui accumsan. " +
                "Arcu non sodales neque sodales ut. Placerat in egestas erat " +
                "imperdiet sed euismod. Dui nunc mattis enim ut tellus. In " +
                "cursus turpis massa tincidunt dui ut ornare. Amet purus gravida " +
                "quis blandit turpis cursus. Feugiat nisl pretium fusce id velit " +
                "ut. Orci phasellus egestas tellus rutrum tellus pellentesque eu " +
                "tincidunt tortor. Porttitor leo a diam sollicitudin tempor id " +
                "eu nisl nunc. Non odio euismod lacinia at quis risus sed " +
                "vulputate. Amet nisl purus in mollis nunc sed id semper risus. " +
                "Nibh sed pulvinar proin gravida hendrerit lectus a. Sed " +
                "adipiscing diam donec adipiscing. Facilisis volutpat est velit " +
                "egestas dui id ornare arcu odio. Feugiat in fermentum posuere " +
                "urna nec tincidunt. Aliquet lectus proin nibh nisl condimentum " +
                "id. Faucibus vitae aliquet nec ullamcorper sit amet. Cursus " +
                "eget nunc scelerisque viverra mauris in. Fames ac turpis " +
                "egestas sed tempus. Dictum non consectetur a erat nam at lectus " +
                "urna. Consectetur adipiscing elit duis tristique sollicitudin " +
                "nibh sit amet commodo. Pellentesque adipiscing commodo elit at " +
                "imperdiet dui accumsan sit. Enim ut sem viverra aliquet. " +
                "Hendrerit dolor magna eget est lorem ipsum. Aliquam etiam erat " +
                "velit scelerisque in dictum non consectetur a. Sem nulla " +
                "pharetra diam sit amet nisl suscipit adipiscing bibendum. " +
                "Semper viverra nam libero justo. Nec dui nunc mattis enim ut " +
                "tellus elementum. Sit amet volutpat consequat mauris nunc " +
                "congue nisi. Massa sapien faucibus et molestie ac feugiat sed. " +
                "Diam donec adipiscing tristique risus nec feugiat in fermentum. " +
                "Elementum nibh tellus molestie nunc non blandit massa enim. Ut " +
                "aliquam purus sit amet luctus venenatis lectus magna. Sed risus " +
                "ultricies tristique nulla aliquet enim tortor at auctor. " +
                "Commodo viverra maecenas accumsan lacus vel facilisis volutpat " +
                "est velit. Elementum nibh tellus molestie nunc non blandit " +
                "massa enim. Imperdiet sed euismod nisi porta lorem mollis " +
                "aliquam ut porttitor. Tincidunt lobortis feugiat vivamus at. " +
                "Tellus at urna condimentum mattis pellentesque id. Quis varius " +
                "quam quisque id diam. Praesent semper feugiat nibh sed pulvinar " +
                "proin gravida hendrerit. Aliquet risus feugiat in ante metus " +
                "dictum at tempor. Nisi est sit amet facilisis magna etiam " +
                "tempor orci eu. Bibendum neque egestas congue quisque egestas " +
                "diam in arcu. Neque laoreet suspendisse interdum consectetur " +
                "libero id. " +
                "Ut enim blandit volutpat maecenas volutpat. Morbi tristique " +
                "senectus et netus et malesuada. Ut sem viverra aliquet eget sit " +
                "amet tellus cras adipiscing. Morbi quis commodo odio aenean sed " +
                "adipiscing diam. Laoreet id donec ultrices tincidunt arcu non " +
                "sodales neque sodales. Et malesuada fames ac turpis egestas " +
                "maecenas pharetra. At tempor commodo ullamcorper a lacus " +
                "vestibulum sed arcu. Vehicula ipsum a arcu cursus vitae congue " +
                "mauris. Porta lorem mollis aliquam ut porttitor leo. Ligula " +
                "ullamcorper malesuada proin libero nunc consequat interdum. " +
                "Pellentesque elit ullamcorper dignissim cras tincidunt lobortis " +
                "feugiat vivamus at. Lobortis elementum nibh tellus molestie " +
                "nunc non blandit massa. " +
                "Gravida arcu ac tortor dignissim convallis aenean et. Facilisis " +
                "magna etiam tempor orci eu lobortis elementum nibh. Mollis " +
                "aliquam ut porttitor leo. Porttitor eget dolor morbi non arcu. " +
                "Semper risus in hendrerit gravida rutrum quisque non tellus. " +
                "Feugiat nisl pretium fusce id velit ut. In ante metus dictum at " +
                "tempor commodo ullamcorper. Viverra nam libero justo laoreet " +
                "sit amet. Odio ut sem nulla pharetra diam. Pellentesque elit " +
                "ullamcorper dignissim cras tincidunt lobortis feugiat vivamus " +
                "at. Aliquam sem et tortor consequat id porta. Porttitor leo a " +
                "diam sollicitudin. Orci a scelerisque purus semper eget duis at " +
                "tellus. A lacus vestibulum sed arcu. Id semper risus in " +
                "hendrerit. " +
                "Enim tortor at auctor urna nunc id cursus metus aliquam. Urna " +
                "duis convallis convallis tellus id. Imperdiet nulla malesuada " +
                "pellentesque elit eget gravida. Tristique senectus et netus et " +
                "malesuada fames ac turpis egestas. Tellus integer feugiat " +
                "scelerisque varius morbi enim nunc faucibus. Sed velit " +
                "dignissim sodales ut eu. Dolor sit amet consectetur adipiscing " +
                "elit pellentesque habitant morbi. Velit euismod in pellentesque " +
                "massa placerat duis ultricies. Purus non enim praesent " +
                "elementum. Scelerisque in dictum non consectetur a erat. Eget " +
                "arcu dictum varius duis at consectetur. Dictumst vestibulum " +
                "rhoncus est pellentesque elit ullamcorper. Vitae tortor " +
                "condimentum lacinia quis vel. Neque volutpat ac tincidunt vitae " +
                "semper quis lectus. Lacus viverra vitae congue eu consequat. " +
                "Massa tincidunt nunc pulvinar sapien et ligula ullamcorper " +
                "malesuada. Fusce id velit ut tortor. Ullamcorper malesuada " +
                "proin libero nunc consequat. Id eu nisl nunc mi ipsum faucibus " +
                "vitae. " +
                "Turpis massa tincidunt dui ut ornare lectus sit amet est. Sed " +
                "tempus urna et pharetra pharetra massa. Pellentesque elit " +
                "ullamcorper dignissim cras tincidunt lobortis feugiat vivamus " +
                "at. Interdum posuere lorem ipsum dolor sit amet consectetur " +
                "adipiscing. Velit laoreet id donec ultrices tincidunt arcu non. " +
                "Ante in nibh mauris cursus. Euismod nisi porta lorem mollis " +
                "aliquam ut porttitor leo a. Urna id volutpat lacus laoreet non " +
                "curabitur gravida. Arcu dictum varius duis at consectetur lorem " +
                "donec massa sapien. Nullam non nisi est sit amet facilisis " +
                "magna etiam. Metus dictum at tempor commodo ullamcorper a lacus " +
                "vestibulum sed. Turpis egestas integer eget aliquet nibh. In " +
                "ante metus dictum at tempor commodo ullamcorper. Molestie ac " +
                "feugiat sed lectus vestibulum mattis ullamcorper. Quisque " +
                "egestas diam in arcu cursus euismod quis viverra. Sed risus " +
                "ultricies tristique nulla aliquet enim tortor. Purus semper " +
                "eget duis at tellus at urna. Velit egestas dui id ornare. Orci " +
                "phasellus egestas tellus rutrum tellus pellentesque eu " +
                "tincidunt. " +
                "Ipsum dolor sit amet consectetur adipiscing elit pellentesque " +
                "habitant. Ac turpis egestas sed tempus. Commodo elit at " +
                "imperdiet dui accumsan sit amet. Posuere lorem ipsum dolor sit. " +
                "Tortor aliquam nulla facilisi cras fermentum odio eu feugiat. " +
                "Urna et pharetra pharetra massa massa ultricies. Nunc non " +
                "blandit massa enim nec dui nunc. Eu sem integer vitae justo. " +
                "Platea dictumst vestibulum rhoncus est. Commodo viverra " +
                "maecenas accumsan lacus vel facilisis volutpat. Tortor posuere " +
                "ac ut consequat semper viverra. Phasellus faucibus scelerisque " +
                "eleifend donec pretium vulputate sapien nec. Vitae suscipit " +
                "tellus mauris a. Eu lobortis elementum nibh tellus molestie " +
                "nunc non blandit massa. Erat velit scelerisque in dictum non. " +
                "Ornare lectus sit amet est placerat in. Rutrum quisque non " +
                "tellus orci ac auctor augue. Velit euismod in pellentesque " +
                "massa placerat duis ultricies lacus. Ullamcorper malesuada " +
                "proin libero nunc consequat. Est ultricies integer quis auctor " +
                "elit sed vulputate. Dignissim convallis aenean et tortor at " +
                "risus viverra adipiscing. Nec feugiat nisl pretium fusce. " +
                "Fringilla phasellus faucibus scelerisque eleifend donec pretium " +
                "vulputate sapien nec. Ultricies tristique nulla aliquet enim " +
                "tortor at. Lacus laoreet non curabitur gravida arcu. Laoreet id " +
                "donec ultrices tincidunt. Laoreet id donec ultrices tincidunt. " +
                "Elit eget gravida cum sociis natoque penatibus. Viverra aliquet " +
                "eget sit amet. Dolor morbi non arcu risus. Aliquam purus sit " +
                "amet luctus venenatis lectus magna. Ullamcorper velit sed " +
                "ullamcorper morbi tincidunt ornare."
                 */
                ;

        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] is sending %d bytes", message.getBytes().length));

        // Encode the data using UTF-8
        byte[] data = message.getBytes(StandardCharsets.UTF_8);

        // Send "Hello World"
        ULX.send(data, instance);
    }

    @Override
    public void onUlxMessageReceived(Message message, Instance instance) {
    }

    @Override
    public void onUlxMessageFailedSending(MessageInfo messageInfo, UlxError error) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] failed to send message [%s]", error.toString()));
    }

    @Override
    public void onUlxMessageSent(MessageInfo messageInfo) {
        Log.i(getClass().getCanonicalName(), String.format("ULX[APP] message [%d] was sent", messageInfo.getIdentifier()));

        // Flood the device
        sendMessage(messageInfo.getDestination());
    }

    @Override
    public void onUlxMessageDelivered(MessageInfo messageInfo, Instance instance, float progress, boolean done) {

    }
}
