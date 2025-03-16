package one.dugon.mediasoup_android_sdk;

import android.util.Log;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import one.dugon.mediasoup_android_sdk.sdp.RemoteSdp;
import one.dugon.mediasoup_android_sdk.sdp.Utils;


public class Transport implements PeerConnection.Observer{
    private static final String TAG = "Transport";

    public String id ;
    public RemoteSdp remoteSdp;

    public Consumer<JsonObject> onConnect;
    public Consumer<MediaStreamTrack> onTrack = null;

    @Nullable
    public PeerConnection pc;

    public boolean ready = false;
    public ExecutorService executor = Executors.newSingleThreadExecutor();


    public Transport(String id,
                     JsonObject iceParameters,
                     JsonArray iceCandidates,
                     JsonObject dtlsParameters){
        this.id = id;
        this.remoteSdp = new RemoteSdp(iceParameters, iceCandidates, dtlsParameters, null);
    }

    public void start(PeerConnection peerConnection) {
        pc = peerConnection;
    }

    public void SetupTransport(String localDtlsRole, JsonObject localSdpObject) {


        // Get our local DTLS parameters.
        JsonObject dtlsParameters = Utils.extractDtlsParameters(localSdpObject);
        dtlsParameters.addProperty("role","client");
        onConnect.accept(dtlsParameters);
        // Set our DTLS role.
//        dtlsParameters["role"] = localDtlsRole;

        // Update the remote DTLS role in the SDP.
//        var remoteDtlsRole = localDtlsRole.equals("client") ? "server" : "client";
//        this->remoteSdp->UpdateDtlsRole(remoteDtlsRole);
        remoteSdp.updateDtlsRole("server");

        // May throw.
//        this->privateListener->OnConnect(dtlsParameters);
        ready = true;
    }


    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.d(TAG,"iceState:"+iceConnectionState.name());
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {

    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream mediaStream) {

    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
        Log.d(TAG,"onAddTrack");
        if(onTrack != null){
            onTrack.accept(receiver.track());
        }
    }

    @Override
    public void onRemoveTrack(RtpReceiver receiver) {
        Log.d(TAG,"onRemoveTrack");
    }
}
