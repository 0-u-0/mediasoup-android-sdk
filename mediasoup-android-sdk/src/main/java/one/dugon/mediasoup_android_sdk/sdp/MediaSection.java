package one.dugon.mediasoup_android_sdk.sdp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class MediaSection {

    protected JsonObject mediaObject;

    public abstract void setDtlsRole(String role);

    public MediaSection(JsonObject iceParameters, JsonArray iceCandidates) {
        // Initialize mediaObject
        this.mediaObject = new JsonObject();

        // Set ICE parameters
        setIceParameters(iceParameters);

        // Set ICE candidates
        JsonArray candidatesArray = new JsonArray();
        for (JsonElement candidate : iceCandidates) {
            JsonObject candidateObject = new JsonObject();
            candidateObject.addProperty("component", 1);
            candidateObject.add("foundation", candidate.getAsJsonObject().get("foundation"));
            candidateObject.add("ip", candidate.getAsJsonObject().get("ip"));
            candidateObject.add("port", candidate.getAsJsonObject().get("port"));
            candidateObject.add("priority", candidate.getAsJsonObject().get("priority"));
            candidateObject.add("transport", candidate.getAsJsonObject().get("protocol"));
            candidateObject.add("type", candidate.getAsJsonObject().get("type"));

            if (candidate.getAsJsonObject().has("tcpType")) {
                candidateObject.add("tcptype", candidate.getAsJsonObject().get("tcpType"));
            }
            candidatesArray.add(candidateObject);
        }
        mediaObject.add("candidates", candidatesArray);

        mediaObject.addProperty("endOfCandidates", "end-of-candidates");
        mediaObject.addProperty("iceOptions", "renomination");
    }

    public String getMid() {
        return mediaObject.get("mid").getAsString();
    }

    public boolean isClosed() {
        return mediaObject.get("port").getAsInt() == 0;
    }

    public JsonObject getObject() {
        return mediaObject;
    }

    protected void setIceParameters(JsonObject iceParameters) {
        mediaObject.add("iceUfrag", iceParameters.get("usernameFragment"));
        mediaObject.add("icePwd", iceParameters.get("password"));
    }

    public void disable() {
        mediaObject.addProperty("direction", "inactive");
        mediaObject.remove("ext");
        mediaObject.remove("ssrcs");
        mediaObject.remove("ssrcGroups");
        mediaObject.remove("simulcast");
        mediaObject.remove("rids");
    }

    public void close() {
        mediaObject.addProperty("direction", "inactive");
        mediaObject.addProperty("port", 0);
        mediaObject.remove("ext");
        mediaObject.remove("ssrcs");
        mediaObject.remove("ssrcGroups");
        mediaObject.remove("simulcast");
        mediaObject.remove("rids");
        mediaObject.remove("extmapAllowMixed");
    }
}
