package one.dugon.mediasoup_android_sdk;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.webrtc.MediaStreamTrack;
import org.webrtc.VideoTrack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import one.dugon.mediasoup_android_sdk.protoo.ProtooEventListener;
import one.dugon.mediasoup_android_sdk.protoo.ProtooSocket;


public class Engine {

    private static final String TAG = "Engine";
    private ProtooSocket protoo;
    private SendTransport sendTransport;
    private RecvTransport recvTransport;

    private LocalAudioSource localAudioSource;
    private LocalVideoSource localVideoSource;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Consumer<String> onTrack = null;

    List<Peer> peerList;


    public Engine(Context context){
        protoo = new ProtooSocket();
        Device.initialize(context);
    }
    public void connect(String signalServer, String roomId, String peerId){

        protoo.setEventListener(new ProtooEventListener() {
            @Override
            public void onConnect() {
                Log.d(TAG, "onConnect");

                executor.execute(()->{
                    getRtpCaps();
                    createWebRTCTransport(false);
                    createWebRTCTransport(true);
                    join();
                });
            }

            @Override
            public void onDisconnect() {

            }

            @Override
            public void onRequest(JsonObject requestData) {
                Log.d(TAG,"onRequest:");
                String requestMethod = requestData.get("method").getAsString();
                if (Objects.equals(requestMethod, "newConsumer")) {
                    Log.d(TAG,"newConsumer");
                    int id = requestData.get("id").getAsInt();
                    JsonObject data = requestData.get("data").getAsJsonObject();
                    String kind = data.get("kind").getAsString();
                    String receiverId = data.get("id").getAsString();
                    JsonObject rtpParameters= data.get("rtpParameters").getAsJsonObject();

                    Device.executor.execute(()->{
                        recvTransport.receive(receiverId,kind,rtpParameters);
//                        if(kind.equals("video")){
//                            Log.d(TAG,"video !" + transceiver.getMid());
//                            var track = transceiver.getReceiver().track();
//                            var videotrack =  (VideoTrack)track;
//                            videotrack.setEnabled(true);
//
//                            myVideoTrack = videotrack;
//                            addRemoteVideoRenderer(videotrack);
//                        }
                        protoo.response(id);
                    });


                }
            }

            @Override
            public void onNotification(String method, JsonObject data) {

            }

            @Override
            public void onError() {

            }
        });

        protoo.connect(signalServer, Map.of("roomId", roomId, "peerId", peerId));
    }

    private void getRtpCaps(){
        JsonObject response = protoo.requestSync("getRouterRtpCapabilities");
        Device.load(response);
    }

    private void createWebRTCTransport(boolean isSender){
        JsonObject createData = new JsonObject();
        createData.addProperty("consuming", !isSender);
        createData.addProperty("forceTcp", false);
        createData.addProperty("producing", isSender);

        JsonObject response = protoo.requestSync("createWebRtcTransport", createData);

        String id = response.get("id").getAsString();
        JsonObject iceParameters = response.getAsJsonObject("iceParameters");
        JsonArray iceCandidates = response.getAsJsonArray("iceCandidates");
        JsonObject dtlsParameters = response.getAsJsonObject("dtlsParameters");

        if (isSender){
            sendTransport = Device.createSendTransport(id, iceParameters, iceCandidates, dtlsParameters);

            sendTransport.onConnect = (JsonObject dtls)->{
                Log.d(TAG,"dtls:"+dtls.toString());
                JsonObject connectData = new JsonObject();
                connectData.addProperty("transportId", id);
                connectData.add("dtlsParameters",dtls);
//                var r4 = socket.request("connectWebRtcTransport", connectData);
                JsonObject connectResponse = protoo.requestSync("connectWebRtcTransport", connectData);
                Log.d(TAG,"dtls: ok");
            };

            sendTransport.onProduce = (JsonObject pData)->{
                Log.d(TAG, "onProduce:");
                pData.addProperty("transportId",id);
                JsonObject produceResponse = protoo.requestSync("produce", pData);
                return produceResponse.get("id").getAsString();
            };

        }else{
            recvTransport = Device.createRecvTransport(id,iceParameters,iceCandidates,dtlsParameters);
//            recvTransport.onTrack = (MediaStreamTrack track)->{
//                String kind = track.kind();
//                if(kind.equals("video")){
//                    var videotrack =  (VideoTrack)track;
//                    videotrack.setEnabled(true);
//
////                    myVideoTrack = videotrack;
//                    addRemoteVideoRenderer(videotrack);
//                }
//            };
            recvTransport.onConnect = (JsonObject dtls)->{
                Log.d(TAG,"recv dtls:"+dtls.toString());
                JsonObject connectData = new JsonObject();
                connectData.addProperty("transportId", id);
                connectData.add("dtlsParameters",dtls);
//                var r4 = socket.request("connectWebRtcTransport", connectData);
                JsonObject connectResponse = protoo.requestSync("connectWebRtcTransport", connectData);
                Log.d(TAG,"dtls: ok");
            };

            recvTransport.onTrack = (String trackId)-> {
                if(onTrack != null){
                    onTrack.accept(trackId);
                }
            };

        }

    }

    private void join(){
        JsonObject joinData = new JsonObject();
        JsonObject rtpCapabilitiesJson = Device.rtpCapabilities;
        JsonObject sctpCapabilitiesJson = Device.sctpCapabilities;

        JsonObject device = new JsonObject();
        device.addProperty("flag", "chrome");
        device.addProperty("name", "Chrome");
        device.addProperty("version", "129.0.0.0");

        joinData.add("device", device);
        joinData.add("rtpCapabilities", rtpCapabilitiesJson);
        joinData.add("sctpCapabilities", sctpCapabilitiesJson);
        joinData.addProperty("displayName", "gg");

        JsonObject response = protoo.requestSync("join", joinData);
        // TODO: 2025/3/16 handle response
        JsonArray peers =  response.get("peers").getAsJsonArray();
//        for (JsonElement p : peers) {
//            Log.d(TAG,"peer");
//
//        }
        // TODO: 2025/8/14 reuse gson
        Gson gson = new Gson();
        peerList = gson.fromJson(peers, new TypeToken<List<Peer>>(){}.getType());
        for(Peer p: peerList ){
            Log.d(TAG, "peer join " + p.id);
        }
    }

    public void enableCam(){
        localVideoSource = Device.createVideoSource();
//        sendTransport.abc();
        sendTransport.send(localVideoSource);
    }

    public void enableMic(){
        localAudioSource = Device.createAudioSource();
        sendTransport.send(localAudioSource);
    }

    public void previewCam(Player player){
        player.play(localVideoSource);
    }

    public void initView(Player player){
        Device.initView(player);
    }

    public void play(Player player, String trackId){
        MediaStreamTrack track = recvTransport.transport.tracks.get(trackId);
        VideoTrack videoTrack = (VideoTrack) track;
        player.play(videoTrack);
    }
}
