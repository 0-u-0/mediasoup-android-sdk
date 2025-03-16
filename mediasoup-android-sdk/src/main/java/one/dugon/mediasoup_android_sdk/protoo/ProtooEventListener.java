package one.dugon.mediasoup_android_sdk.protoo;

import com.google.gson.JsonObject;

public interface ProtooEventListener {
    void onConnect();
    void onDisconnect();
    void onRequest(JsonObject data);
    void onNotification();
    void onError();
}
