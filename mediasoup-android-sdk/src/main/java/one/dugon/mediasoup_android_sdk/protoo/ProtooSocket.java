package one.dugon.mediasoup_android_sdk.protoo;


import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

// https://github.com/MJLblabla/mylearnav/blob/89ae1c47acd3b72b8191fb1840f8af126c534118/mediasoupRTC/src/main/java/com/codebouy/mediasouprtc/socket/WebSocketTransport.java
public class ProtooSocket {
    private static final String TAG = "ProtooSocket";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private ProtooEventListener listener;

    private OkHttpClient client;
    private WebSocket webSocket;
    private Gson gson;

    private Map<Integer, CompletableFuture<JsonObject>> pendingRequests;

    public ProtooSocket() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    public void setEventListener(ProtooEventListener listener){
        this.listener = listener;
    }

    public void connect(String url, Map<String, String> queryParams) {

        StringBuilder urlBuilder = new StringBuilder(url);
        if (queryParams != null && !queryParams.isEmpty()) {
            urlBuilder.append("?");
            for (Map.Entry<String, String> param : queryParams.entrySet()) {
                urlBuilder.append(param.getKey()).append("=").append(param.getValue()).append("&");
            }
            urlBuilder.setLength(urlBuilder.length() - 1); // 移除最后一个多余的 '&'
        }

        Log.d(TAG,urlBuilder.toString());

        Request.Builder requestBuilder = new Request.Builder()
                .url(urlBuilder.toString())
                .header("Sec-WebSocket-Protocol", "protoo");


        Request request = requestBuilder.build();

        executor.execute(()->{
            WebSocketListener wsListener = new WebSocketListener(){

                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {

                    Log.d(TAG,"onOpen");
                    if(listener != null){
                        listener.onConnect();
                    }
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
//        super.onMessage(webSocket, bytes);
                    Log.d("W","onMessage:");
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
//        super.onMessage(webSocket, text);
                    Log.d(TAG,text);
                    JsonObject jsonObject = gson.fromJson(text, JsonObject.class);
                    if(jsonObject.has("response")){
                        ProtooResponse response = gson.fromJson(jsonObject, ProtooResponse.class);
                        CompletableFuture<JsonObject> future = pendingRequests.get(response.id);
                        future.complete(response.data);
//            Log.d(TAG,response.toString());
                    } else if(jsonObject.has("notification")){
                        ProtooNotification notification = gson.fromJson(jsonObject, ProtooNotification.class);
                        if(listener != null){
                            listener.onNotification(notification.method, notification.data);
                        }
                    } else if(jsonObject.has("request")){
                        if(listener != null){
                            listener.onRequest(jsonObject);
                        }
                    }

                }

                @Override
                public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
//        super.onFailure(webSocket, t, response);
                    Log.d(TAG,"onFailure:"+t.toString());
                    if(listener != null){
                        listener.onError();
                    }
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
//        super.onClosing(webSocket, code, reason);
                    Log.d(TAG,"onClosing:");

                }

                @Override
                public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
//        super.onClosed(webSocket, code, reason);
                    Log.d(TAG,"onClosed:");
                }
            };
            this.webSocket = client.newWebSocket(request, wsListener);
        });

    }

    public void request(String method, Consumer<JsonObject> callback) {
        request(method,new JsonObject(), callback);
    }

    public void request(String method, JsonObject data, Consumer<JsonObject> callback) {
        int id = new Random().nextInt(Integer.MAX_VALUE);
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("request", true);
        requestJson.addProperty("id", id);
        requestJson.addProperty("method", method);
        requestJson.add("data", data);

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        executor.execute(()-> {
            String message = gson.toJson(requestJson);

            Log.d(TAG,message);

            send(message);

            JsonObject response = null;
            try {
                response = future.get();

                callback.accept(response);

            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        });
    }

    public JsonObject requestSync(String method) {
        return requestSync(method, new JsonObject());
    }
        public JsonObject requestSync(String method, JsonObject data) {
        int id = new Random().nextInt(Integer.MAX_VALUE);
        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("request", true);
        requestJson.addProperty("id", id);
        requestJson.addProperty("method", method);
        requestJson.add("data", data);

        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        executor.execute(()-> {
            String message = gson.toJson(requestJson);

            Log.d(TAG,message);

            send(message);
        });

        JsonObject response = null;
        try {
            response = future.get();

        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return response;
    }


    public void response(int id) {

        JsonObject responseJson = new JsonObject();
        responseJson.addProperty("id", id);
        responseJson.addProperty("response",true);
        responseJson.addProperty("ok",true);
        responseJson.add("data", new JsonObject());

        String message = gson.toJson(responseJson);

        Log.d(TAG,message);

        send(message);
    }

    public void send(String message) {
        if (webSocket != null) {
            webSocket.send(message);
        }
    }

    public void send(ByteString byteString) {
        if (webSocket != null) {
            webSocket.send(byteString);
        }
    }

    public void close(int code, String reason) {
        if (webSocket != null) {
            webSocket.close(code, reason);
        }
    }

    public void cancel() {
        if (webSocket != null) {
            webSocket.cancel();
        }
    }

}