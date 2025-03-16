package one.dugon.mediasoup_android_sdk.sdp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

public class OfferMediaSection extends MediaSection {

    public OfferMediaSection(JsonObject iceParameters, JsonArray iceCandidates, JsonObject dtlsParameters, JsonObject sctpParameters, String mid, String kind, JsonObject offerRtpParameters, String streamId, String trackId) {
        super(iceParameters, iceCandidates);

        mediaObject.addProperty("mid", mid);
        mediaObject.addProperty("type", kind);

        if (sctpParameters == null) {
            mediaObject.addProperty("protocol", "UDP/TLS/RTP/SAVPF");
        } else {
            mediaObject.addProperty("protocol", "UDP/DTLS/SCTP");
        }

        JsonObject connection = new JsonObject();
        connection.addProperty("ip", "127.0.0.1");
        connection.addProperty("version", 4);
        mediaObject.add("connection", connection);

        mediaObject.addProperty("port", 7);

        // Set DTLS role
        mediaObject.addProperty("setup", "actpass");

        if (kind.equals("audio") || kind.equals("video")) {
            mediaObject.addProperty("direction", "sendonly");
            mediaObject.add("rtp", new JsonArray());
            mediaObject.add("rtcpFb", new JsonArray());
            mediaObject.add("fmtp", new JsonArray());

            // Setup codecs
            for (JsonElement codecElem : offerRtpParameters.getAsJsonArray("codecs")) {
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
            for (JsonElement codecElem : offerRtpParameters.getAsJsonArray("codecs")) {
                if (payloads.length() > 0) payloads.append(" ");
                payloads.append(codecElem.getAsJsonObject().get("payloadType").getAsString());
            }
            mediaObject.addProperty("payloads", payloads.toString());

            JsonArray ext = new JsonArray();
            for (JsonElement extElem : offerRtpParameters.getAsJsonArray("headerExtensions")) {
                JsonObject extObject = new JsonObject();
                extObject.add("uri", extElem.getAsJsonObject().get("uri"));
                extObject.add("value", extElem.getAsJsonObject().get("id"));
                ext.add(extObject);
            }
            mediaObject.add("ext", ext);

            mediaObject.addProperty("rtcpMux", "rtcp-mux");
            mediaObject.addProperty("rtcpRsize", "rtcp-rsize");

            JsonObject encoding = offerRtpParameters.getAsJsonArray("encodings").get(0).getAsJsonObject();
            long ssrc = encoding.get("ssrc").getAsLong();
            long rtxSsrc = encoding.has("rtx") ? encoding.getAsJsonObject("rtx").get("ssrc").getAsLong() : 0;

            mediaObject.add("ssrcs", new JsonArray());
            mediaObject.add("ssrcGroups", new JsonArray());

            if (offerRtpParameters.getAsJsonObject("rtcp").has("cname")) {
                String cname = offerRtpParameters.getAsJsonObject("rtcp").get("cname").getAsString();
                String msid = streamId + " " + trackId;

                JsonObject ssrcItem1 = new JsonObject();
                ssrcItem1.addProperty("id", ssrc);
                ssrcItem1.addProperty("attribute", "cname");
                ssrcItem1.addProperty("value", cname);
                mediaObject.getAsJsonArray("ssrcs").add(ssrcItem1);

                JsonObject ssrcItem2 = new JsonObject();
                ssrcItem2.addProperty("id", ssrc);
                ssrcItem2.addProperty("attribute", "msid");
                ssrcItem2.addProperty("value", msid);
                mediaObject.getAsJsonArray("ssrcs").add(ssrcItem2);

                if (rtxSsrc != 0) {
                    JsonObject ssrcItem3 = new JsonObject();
                    ssrcItem3.addProperty("id", rtxSsrc);
                    ssrcItem3.addProperty("attribute", "cname");
                    ssrcItem3.addProperty("value", cname);
                    mediaObject.getAsJsonArray("ssrcs").add(ssrcItem3);

                    JsonObject ssrcItem4 = new JsonObject();
                    ssrcItem4.addProperty("id", rtxSsrc);
                    ssrcItem4.addProperty("attribute", "msid");
                    ssrcItem4.addProperty("value", msid);
                    mediaObject.getAsJsonArray("ssrcs").add(ssrcItem4);

                    String ssrcs = ssrc + " " + rtxSsrc;
                    JsonObject ssrcGroup = new JsonObject();
                    ssrcGroup.addProperty("semantics", "FID");
                    ssrcGroup.addProperty("ssrcs", ssrcs);
                    mediaObject.getAsJsonArray("ssrcGroups").add(ssrcGroup);
                }
            }
        } else if (kind.equals("application")) {
            mediaObject.addProperty("payloads", "webrtc-datachannel");
            mediaObject.add("sctpPort", sctpParameters.get("port"));
            mediaObject.add("maxMessageSize", sctpParameters.get("maxMessageSize"));
        }
    }

    @Override
    public void setDtlsRole(String role) {
        // Always set to actpass for SDP offer
        mediaObject.addProperty("setup", "actpass");
    }
}