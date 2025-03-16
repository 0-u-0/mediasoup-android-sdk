package one.dugon.mediasoup_android_sdk.sdp;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.function.Predicate;

public class AnswerMediaSection extends MediaSection {

    public AnswerMediaSection(JsonObject iceParameters, JsonArray iceCandidates, JsonObject dtlsParameters, JsonObject sctpParameters, JsonObject offerMediaObject, JsonObject offerRtpParameters, JsonObject answerRtpParameters, JsonObject codecOptions) {
        super(iceParameters, iceCandidates);

        String type = offerMediaObject.get("type").getAsString();
        mediaObject.add("mid", offerMediaObject.get("mid"));
        mediaObject.add("type", offerMediaObject.get("type"));
        mediaObject.add("protocol", offerMediaObject.get("protocol"));

        JsonObject connection = new JsonObject();
        connection.addProperty("ip", "127.0.0.1");
        connection.addProperty("version", 4);
        mediaObject.add("connection", connection);

        mediaObject.addProperty("port", 7);

        // Set DTLS role
        String dtlsRole = dtlsParameters.get("role").getAsString();
        switch (dtlsRole) {
            case "client":
                mediaObject.addProperty("setup", "active");
                break;
            case "server":
                mediaObject.addProperty("setup", "passive");
                break;
            case "auto":
                mediaObject.addProperty("setup", "actpass");
                break;
        }

        if (type.equals("audio") || type.equals("video")) {
            mediaObject.addProperty("direction", "recvonly");
            mediaObject.add("rtp", new JsonArray());
            mediaObject.add("rtcpFb", new JsonArray());
            mediaObject.add("fmtp", new JsonArray());

            // Setup codecs
            for (JsonElement codecElem : answerRtpParameters.getAsJsonArray("codecs")) {
                JsonObject codec = codecElem.getAsJsonObject();
                JsonObject rtp = new JsonObject();
                rtp.add("payload", codec.get("payloadType"));
                rtp.addProperty("codec", Utils.getCodecName(codec));
                rtp.add("rate", codec.get("clockRate"));
                if (codec.has("channels")) {
                    int channels = codec.get("channels").getAsInt();
                    if (channels > 1) {
                        rtp.addProperty("encoding", channels);
                    }
                }
                mediaObject.getAsJsonArray("rtp").add(rtp);

                JsonObject fmtp = new JsonObject();
                fmtp.add("payload", codec.get("payloadType"));
                JsonObject parameters = codec.getAsJsonObject("parameters");
                StringBuilder config = new StringBuilder();
                for (Map.Entry<String, JsonElement> entry : parameters.entrySet()) {
                    if (config.length() > 0) config.append(";");
                    config.append(entry.getKey()).append("=").append(entry.getValue().getAsString());
                }
                if (config.length() > 0) {
                    fmtp.addProperty("config", config.toString());
                    mediaObject.getAsJsonArray("fmtp").add(fmtp);
                }

                for (JsonElement feedbackElem : codec.getAsJsonArray("rtcpFeedback")) {
                    JsonObject feedback = feedbackElem.getAsJsonObject();
                    JsonObject fb = new JsonObject();
                    fb.add("payload", codec.get("payloadType"));
                    fb.add("type", feedback.get("type"));
                    fb.add("subtype", feedback.get("parameter"));
                    mediaObject.getAsJsonArray("rtcpFb").add(fb);
                }
            }

            StringBuilder payloads = new StringBuilder();
            for (JsonElement codecElem : answerRtpParameters.getAsJsonArray("codecs")) {
                if (payloads.length() > 0) payloads.append(" ");
                payloads.append(codecElem.getAsJsonObject().get("payloadType").getAsString());
            }
            mediaObject.addProperty("payloads", payloads.toString());

            JsonArray ext = new JsonArray();
            for (JsonElement extElem : answerRtpParameters.getAsJsonArray("headerExtensions")) {
                JsonObject extObject = extElem.getAsJsonObject();
                Predicate<JsonElement> extPredicate = localExt -> localExt.getAsJsonObject().get("uri").equals(extObject.get("uri"));

                boolean found = false;
                for (JsonElement localExtElem : offerMediaObject.getAsJsonArray("ext")) {
                    if (extPredicate.test(localExtElem)) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    JsonObject extEntry = new JsonObject();
                    extEntry.add("uri", extObject.get("uri"));
                    extEntry.add("value", extObject.get("id"));
                    ext.add(extEntry);
                }
            }
            mediaObject.add("ext", ext);

            if (offerMediaObject.has("extmapAllowMixed")) {
                mediaObject.addProperty("extmapAllowMixed", "extmap-allow-mixed");
            }

            if (offerMediaObject.has("simulcast") && offerMediaObject.has("rids")) {
                JsonObject simulcast = new JsonObject();
                simulcast.addProperty("dir1", "recv");
                simulcast.add("list1", offerMediaObject.getAsJsonObject("simulcast").get("list1"));
                mediaObject.add("simulcast", simulcast);

                JsonArray rids = new JsonArray();
                for (JsonElement ridElem : offerMediaObject.getAsJsonArray("rids")) {
                    JsonObject rid = ridElem.getAsJsonObject();
                    if (rid.get("direction").getAsString().equals("send")) {
                        JsonObject ridEntry = new JsonObject();
                        ridEntry.add("id", rid.get("id"));
                        ridEntry.addProperty("direction", "recv");
                        rids.add(ridEntry);
                    }
                }
                mediaObject.add("rids", rids);
            }

            mediaObject.addProperty("rtcpMux", "rtcp-mux");
            mediaObject.addProperty("rtcpRsize", "rtcp-rsize");
        } else if (type.equals("application")) {
            mediaObject.addProperty("payloads", "webrtc-datachannel");
            mediaObject.add("sctpPort", sctpParameters.get("port"));
            mediaObject.add("maxMessageSize", sctpParameters.get("maxMessageSize"));
        }
    }

    public void setDtlsRole(String role) {
        switch (role) {
            case "client":
                mediaObject.addProperty("setup", "active");
                break;
            case "server":
                mediaObject.addProperty("setup", "passive");
                break;
            case "auto":
                mediaObject.addProperty("setup", "actpass");
                break;
            default:
                throw new IllegalArgumentException("Invalid DTLS role: " + role);
        }
    }
}