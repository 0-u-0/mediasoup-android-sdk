package one.dugon.mediasoup_android_sdk.sdp;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;


public class Utils {
    private static final String TAG = "Utils";

    private static final Pattern keyValueRegex = Pattern.compile("^\\s*([^= ]+)(?:\\s*=\\s*([^ ]+))?$");

    private static final Pattern MimeTypeRegex = Pattern.compile("^(audio|video)/(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RtxMimeTypeRegex = Pattern.compile("^(audio|video)/rtx$", Pattern.CASE_INSENSITIVE);

    // Well-known parameters and their expected types.
    private static final Map<String, Character> WellKnownParameters = new HashMap<String, Character>() {{
        put("profile-level-id", 's');   // String
        put("packetization-mode", 'd'); // Integer
        put("profile-id", 's');         // String
    }};

    public static JsonObject extractRtpCapabilities(JsonObject sdpObject) {
        Map<Integer, JsonObject> codecsMap = new HashMap<>();
        JsonArray headerExtensions = new JsonArray();
        boolean gotAudio = false;
        boolean gotVideo = false;

        for (JsonElement mediaElement : sdpObject.getAsJsonArray("media")) {
            JsonObject m = mediaElement.getAsJsonObject();
            String kind = m.get("type").getAsString();

            if ("audio".equals(kind)) {
                if (gotAudio) continue;
                gotAudio = true;
            } else if ("video".equals(kind)) {
                if (gotVideo) continue;
                gotVideo = true;
            } else {
                continue;
            }

            for (JsonElement rtpElement : m.getAsJsonArray("rtp")) {
                JsonObject rtp = rtpElement.getAsJsonObject();
                String mimeType = kind + "/" + rtp.get("codec").getAsString();

                JsonObject codec = new JsonObject();
                codec.addProperty("kind", kind);
                codec.addProperty("mimeType", mimeType);
                codec.add("preferredPayloadType", rtp.get("payload"));
                codec.add("clockRate", rtp.get("rate"));
                codec.add("parameters", new JsonObject());
                codec.add("rtcpFeedback", new JsonArray());

                if ("audio".equals(kind)) {
                    JsonElement jsonEncoding = rtp.get("encoding");
                    if (jsonEncoding != null && jsonEncoding.isJsonPrimitive()) {
                        codec.addProperty("channels", Integer.parseInt(jsonEncoding.getAsString()));
                    } else {
                        codec.addProperty("channels", 1);
                    }
                }

                codecsMap.put(codec.get("preferredPayloadType").getAsInt(), codec);
            }

            for (JsonElement fmtpElement : m.getAsJsonArray("fmtp")) {
                JsonObject fmtp = fmtpElement.getAsJsonObject();
                JsonObject parameters = Utils.parseParams(fmtp.get("config").getAsString());
                int payload = fmtp.get("payload").getAsInt();

                JsonObject codec = codecsMap.get(payload);
                if (codec == null) continue;

                if (parameters.has("profile-id")) {
                    parameters.addProperty("profile-id", Integer.parseInt(parameters.get("profile-id").getAsString()));
                }

                codec.add("parameters", parameters);
            }

            for (JsonElement fbElement : m.getAsJsonArray("rtcpFb")) {
                JsonObject fb = fbElement.getAsJsonObject();
                int payload = Integer.parseInt(fb.get("payload").getAsString());
                JsonObject codec = codecsMap.get(payload);

                if (codec == null) continue;

                JsonObject feedback = new JsonObject();
                feedback.addProperty("type", fb.get("type").getAsString());

                JsonElement subtype = fb.get("subtype");
                if (subtype != null) {
                    feedback.add("parameter", subtype);
                }

                codec.getAsJsonArray("rtcpFeedback").add(feedback);
            }

            for (JsonElement extElement : m.getAsJsonArray("ext")) {
                JsonObject ext = extElement.getAsJsonObject();
                JsonObject headerExtension = new JsonObject();
                headerExtension.addProperty("kind", kind);
                headerExtension.add("uri", ext.get("uri"));
                headerExtension.add("preferredId", ext.get("value"));
                headerExtensions.add(headerExtension);
            }
        }

        JsonObject rtpCapabilities = new JsonObject();
        rtpCapabilities.add("headerExtensions", headerExtensions);
        rtpCapabilities.add("codecs", new JsonArray());
        rtpCapabilities.add("fecMechanisms", new JsonArray());

        for (Map.Entry<Integer, JsonObject> entry : codecsMap.entrySet()) {
            rtpCapabilities.getAsJsonArray("codecs").add(entry.getValue());
        }

        return rtpCapabilities;
    }

    public static void insertParam(JsonObject o, String str) {
        Matcher matcher = keyValueRegex.matcher(str);

        if (!matcher.matches()) {
            return;
        }

        String param = matcher.group(1);  // The key (parameter name)
        String value = matcher.group(2);  // The value (optional, can be null)

        char type;

        if (WellKnownParameters.containsKey(param)) {
            type = WellKnownParameters.get(param);
        } else if (isInt(value)) {
            type = 'd';  // Treat as an integer
        }  else {
            type = 's';  // Default to a string
        }

        // Insert into the given JSON object based on type
        o.add(param, toType(value, type));
    }

    // Utility to convert a string value into the correct JSON type (int, float, or string)
    private static JsonElement toType(String value, char type) {
        if (value == null) {
            return new com.google.gson.JsonPrimitive("");  // Handle missing values as empty strings
        }

        switch (type) {
            case 'd':  // Integer type
                return new com.google.gson.JsonPrimitive(Integer.parseInt(value));
            case 'f':  // Float type
                return new com.google.gson.JsonPrimitive(Float.parseFloat(value));
            case 's':  // String type
            default:
                return new com.google.gson.JsonPrimitive(value);
        }
    }

    // Utility to check if a string represents an integer
    private static boolean isInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static JsonObject parseParams(String str) {
        JsonObject obj = new JsonObject();
        StringTokenizer tokenizer = new StringTokenizer(str, ";");

        while (tokenizer.hasMoreTokens()) {
            String param = tokenizer.nextToken().trim();

            if (param.length() == 0)
                continue;

            insertParam(obj, param);
        }

        return obj;
    }

    //--------------------------
    public static JsonObject extractDtlsParameters(JsonObject sdpObject) {
        JsonObject mediaSection = null;
        JsonObject fingerprint = new JsonObject();
        String role = null;

        // Find the first media section with "iceUfrag" and non-zero port
        for (JsonElement mediaElement : sdpObject.getAsJsonArray("media")) {
            JsonObject media = mediaElement.getAsJsonObject();
            if (media.has("iceUfrag") && media.get("port").getAsInt() != 0) {
                mediaSection = media;
                break;
            }
        }

        if (mediaSection != null) {
            if (mediaSection.has("fingerprint")) {
                fingerprint = mediaSection.getAsJsonObject("fingerprint");
            } else if (sdpObject.has("fingerprint")) {
                fingerprint = sdpObject.getAsJsonObject("fingerprint");
            }

            if (mediaSection.has("setup")) {
                String setup = mediaSection.get("setup").getAsString();
                switch (setup) {
                    case "active":
                        role = "client";
                        break;
                    case "passive":
                        role = "server";
                        break;
                    case "actpass":
                        role = "auto";
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid setup value: " + setup);
                }
            }
        }

        JsonObject dtlsParameters = new JsonObject();
        dtlsParameters.addProperty("role", role);

        JsonArray fingerprintsArray = new JsonArray();
        JsonObject fingerprintObject = new JsonObject();
        fingerprintObject.add("algorithm", fingerprint.get("type"));
        fingerprintObject.add("value", fingerprint.get("hash"));
        fingerprintsArray.add(fingerprintObject);

        dtlsParameters.add("fingerprints", fingerprintsArray);

        return dtlsParameters;
    }

    public static String getCodecName(JsonObject codec) {
        Pattern mimeTypePattern = Pattern.compile("^(audio|video)/", Pattern.CASE_INSENSITIVE);
        String mimeType = codec.get("mimeType").getAsString();
        Matcher matcher = mimeTypePattern.matcher(mimeType);

        return matcher.replaceAll("");
    }

    public static JsonArray getRtpEncodings(JsonObject offerMediaObject) {
        List<Long> ssrcs = new ArrayList<>();
        Log.d(TAG,offerMediaObject.toString());

        // Extract SSRCs from the offerMediaObject
        for (JsonElement lineElement : offerMediaObject.getAsJsonArray("ssrcs")) {
            JsonObject line = lineElement.getAsJsonObject();
            long ssrc = line.get("id").getAsLong();
            ssrcs.add(ssrc);
        }

        if (ssrcs.isEmpty()) {
            throw new RuntimeException("no a=ssrc lines found");
        }

        // Remove duplicates
        Set<Long> uniqueSsrcs = new LinkedHashSet<>(ssrcs);
        ssrcs.clear();
        ssrcs.addAll(uniqueSsrcs);

        // Get media and RTX SSRCs
        Map<Long, Long> ssrcToRtxSsrc = new HashMap<>();

        if (offerMediaObject.has("ssrcGroups")) {
            JsonArray ssrcGroups = offerMediaObject.getAsJsonArray("ssrcGroups");

            for (JsonElement lineElement : ssrcGroups) {
                JsonObject line = lineElement.getAsJsonObject();
                if (!line.get("semantics").getAsString().equals("FID")) {
                    continue;
                }

                String fidLine = line.get("ssrcs").getAsString();
                String[] ssrcArray = fidLine.split(" ");
                long ssrc = Long.parseLong(ssrcArray[0]);
                long rtxSsrc = Long.parseLong(ssrcArray[1]);

                // Remove the RTX SSRC from the list
                ssrcs.remove(rtxSsrc);

                // Add to the map
                ssrcToRtxSsrc.put(ssrc, rtxSsrc);
            }
        }

        // Fill RTP parameters
        JsonArray encodings = new JsonArray();

        for (Long ssrc : ssrcs) {
            JsonObject encoding = new JsonObject();
            encoding.addProperty("ssrc", ssrc);

            if (ssrcToRtxSsrc.containsKey(ssrc)) {
                JsonObject rtx = new JsonObject();
                rtx.addProperty("ssrc", ssrcToRtxSsrc.get(ssrc));
                encoding.add("rtx", rtx);
            }

            encodings.add(encoding);
        }

        return encodings;
    }

    public static String getCname(JsonObject offerMediaObject) {

        if (!offerMediaObject.has("ssrcs")) {
            return "";
        }

        JsonArray mSsrcs = offerMediaObject.getAsJsonArray("ssrcs");

        Iterator<JsonElement> iterator = mSsrcs.iterator();
        while (iterator.hasNext()) {
            JsonObject line = iterator.next().getAsJsonObject();

            if (line.has("attribute") && line.get("attribute").isJsonPrimitive()
                    && line.get("attribute").getAsJsonPrimitive().isString()) {
                return line.getAsJsonPrimitive("value").getAsString();
            }
        }

        return "";
    }


    public JsonArray reduceCodecs(JsonArray codecs, JsonObject capCodec) {
        JsonArray filteredCodecs = new JsonArray();

        // If no capability codec is given, take the first one (and RTX).
        if (capCodec == null || !capCodec.isJsonObject()) {
            filteredCodecs.add(codecs.get(0));

            if (codecs.size() > 1 && isRtxCodec(codecs.get(1).getAsJsonObject())) {
                filteredCodecs.add(codecs.get(1));
            }
        } else {
            // Otherwise look for a compatible set of codecs.
            for (int idx = 0; idx < codecs.size(); ++idx) {
                JsonObject codec = codecs.get(idx).getAsJsonObject();
                if (matchCodecs(codec, capCodec, true, false)) {
                    filteredCodecs.add(codec);

                    if (idx + 1 < codecs.size() && isRtxCodec(codecs.get(idx + 1).getAsJsonObject())) {
                        filteredCodecs.add(codecs.get(idx + 1).getAsJsonObject());
                    }

                    break;
                }
            }

            if (filteredCodecs.size() == 0) {
                throw new IllegalArgumentException("no matching codec found");
            }
        }

        return filteredCodecs;
    }

    private static boolean isRtxCodec(JsonObject codec) {

        String mimeType = codec.get("mimeType").getAsString();
        Matcher matcher = RtxMimeTypeRegex.matcher(mimeType);

        return matcher.matches();
    }

    private static String getVP9ProfileId(JsonObject codec) {

        JsonObject parameters = codec.getAsJsonObject("parameters");
        JsonElement profileIdElement = parameters.get("profile-id");

        if (profileIdElement == null) {
            return "0";
        } else if (profileIdElement.isJsonPrimitive() && profileIdElement.getAsJsonPrimitive().isNumber()) {
            return profileIdElement.getAsString();
        } else if (profileIdElement.isJsonPrimitive() && profileIdElement.getAsJsonPrimitive().isString()) {
            return profileIdElement.getAsString();
        } else {
            return "0";
        }
    }


    private static int getH264PacketizationMode(JsonObject codec) {

        JsonObject parameters = codec.getAsJsonObject("parameters");
        JsonElement packetizationModeElement = parameters.get("packetization-mode");

        if (packetizationModeElement == null || !packetizationModeElement.isJsonPrimitive() || !packetizationModeElement.getAsJsonPrimitive().isNumber()) {
            return 0;
        }

        return packetizationModeElement.getAsInt();
    }

    private static String getH264ProfileLevelId(JsonObject codec) {

        JsonObject parameters = codec.getAsJsonObject("parameters");
        JsonElement profileLevelIdElement = parameters.get("profile-level-id");

        if (profileLevelIdElement == null) {
            return "";
        } else if (profileLevelIdElement.isJsonPrimitive() && profileLevelIdElement.getAsJsonPrimitive().isNumber()) {
            return profileLevelIdElement.getAsString();
        } else if (profileLevelIdElement.isJsonPrimitive() && profileLevelIdElement.getAsJsonPrimitive().isString()) {
            return profileLevelIdElement.getAsString();
        } else {
            return "";
        }
    }

    private static int getH264LevelAsymmetryAllowed(JsonObject codec) {

        JsonObject parameters = codec.getAsJsonObject("parameters");
        JsonElement levelAsymmetryAllowedElement = parameters.get("level-asymmetry-allowed");

        if (levelAsymmetryAllowedElement == null || !levelAsymmetryAllowedElement.isJsonPrimitive() || !levelAsymmetryAllowedElement.getAsJsonPrimitive().isNumber()) {
            return 0;
        }

        return levelAsymmetryAllowedElement.getAsInt();
    }

    private static boolean matchHeaderExtensions(JsonObject aExt, JsonObject bExt) {

        if (!aExt.get("kind").equals(bExt.get("kind"))) {
            return false;
        }

        return aExt.get("uri").equals(bExt.get("uri"));
    }



    private static JsonArray reduceRtcpFeedback(JsonObject codecA, JsonObject codecB) {

        JsonArray reducedRtcpFeedback = new JsonArray();
        JsonArray rtcpFeedbackA = codecA.getAsJsonArray("rtcpFeedback");
        JsonArray rtcpFeedbackB = codecB.getAsJsonArray("rtcpFeedback");

        for (JsonElement aFbElement : rtcpFeedbackA) {
            JsonObject aFb = aFbElement.getAsJsonObject();
            Optional<JsonObject> rtcpFeedbackOpt = StreamSupport.stream(rtcpFeedbackB.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(bFb -> aFb.get("type").equals(bFb.get("type")) && aFb.get("parameter").equals(bFb.get("parameter")))
                    .findFirst();

            rtcpFeedbackOpt.ifPresent(reducedRtcpFeedback::add);
        }

        return reducedRtcpFeedback;
    }

    private static boolean matchCodecs(JsonObject aCodec, JsonObject bCodec, boolean strict, boolean modify) {

        String aMimeType = aCodec.get("mimeType").getAsString().toLowerCase();
        String bMimeType = bCodec.get("mimeType").getAsString().toLowerCase();

        if (!aMimeType.equals(bMimeType))
            return false;

        if (!aCodec.get("clockRate").equals(bCodec.get("clockRate")))
            return false;

        if (aCodec.has("channels") != bCodec.has("channels"))
            return false;

        if (aCodec.has("channels") && !aCodec.get("channels").equals(bCodec.get("channels")))
            return false;

        // Match H264 parameters.
        if (aMimeType.equals("video/h264")) {
            if (strict) {
                int aPacketizationMode = getH264PacketizationMode(aCodec);
                int bPacketizationMode = getH264PacketizationMode(bCodec);

                if (aPacketizationMode != bPacketizationMode)
                    return false;

                Map<String, String> aParameters = new HashMap<>();
                Map<String, String> bParameters = new HashMap<>();

                aParameters.put("level-asymmetry-allowed", String.valueOf(getH264LevelAsymmetryAllowed(aCodec)));
                aParameters.put("packetization-mode", String.valueOf(aPacketizationMode));
                aParameters.put("profile-level-id", getH264ProfileLevelId(aCodec));
                bParameters.put("level-asymmetry-allowed", String.valueOf(getH264LevelAsymmetryAllowed(bCodec)));
                bParameters.put("packetization-mode", String.valueOf(bPacketizationMode));
                bParameters.put("profile-level-id", getH264ProfileLevelId(bCodec));

                if (!H264ProfileLevelId.isSameProfile(aParameters, bParameters))
                    return false;

                String selectedProfileLevelId = "";
                try {
                    selectedProfileLevelId = H264ProfileLevelId.generateProfileLevelIdStringForAnswer(aParameters, bParameters);
                } catch (RuntimeException e) {
                    return false;
                }

                if (modify) {
                    if (!selectedProfileLevelId.isEmpty()) {
                        aCodec.getAsJsonObject("parameters").addProperty("profile-level-id", selectedProfileLevelId);
                        bCodec.getAsJsonObject("parameters").addProperty("profile-level-id", selectedProfileLevelId);
                    } else {
                        aCodec.getAsJsonObject("parameters").remove("profile-level-id");
                        bCodec.getAsJsonObject("parameters").remove("profile-level-id");
                    }
                }
            }
        }
        // Match VP9 parameters.
        else if (aMimeType.equals("video/vp9")) {
            if (strict) {
                String aProfileId = getVP9ProfileId(aCodec);
                String bProfileId = getVP9ProfileId(bCodec);

                return Objects.equals(aProfileId, bProfileId);
            }
        }

        return true;
    }

    public static JsonObject getExtendedRtpCapabilities(JsonObject localCaps, JsonObject remoteCaps){
        // This may throw.
//        validateRtpCapabilities(localCaps);
//        validateRtpCapabilities(remoteCaps);

        JsonObject extendedRtpCapabilities = new JsonObject();
        extendedRtpCapabilities.add("codecs", new JsonArray());
        extendedRtpCapabilities.add("headerExtensions", new JsonArray());

        // Match media codecs and keep the order preferred by remoteCaps.
        JsonArray remoteCapsCodecs = remoteCaps.getAsJsonArray("codecs");

        for (JsonElement remoteCodecElement : remoteCapsCodecs) {
            JsonObject remoteCodec = remoteCodecElement.getAsJsonObject();
            if (isRtxCodec(remoteCodec)) continue;

            JsonArray localCodecs = localCaps.getAsJsonArray("codecs");

            Optional<JsonObject> matchingLocalCodecOpt = StreamSupport.stream(localCodecs.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(localCodec -> matchCodecs(localCodec, remoteCodec, true, true))
                    .findFirst();

            if (!matchingLocalCodecOpt.isPresent()) continue;

            JsonObject matchingLocalCodec = matchingLocalCodecOpt.get();

            JsonObject extendedCodec = new JsonObject();
            extendedCodec.add("mimeType", matchingLocalCodec.get("mimeType"));
            extendedCodec.add("kind", matchingLocalCodec.get("kind"));
            extendedCodec.add("clockRate", matchingLocalCodec.get("clockRate"));
            extendedCodec.add("localPayloadType", matchingLocalCodec.get("preferredPayloadType"));
            extendedCodec.add("localRtxPayloadType", JsonNull.INSTANCE);
            extendedCodec.add("remotePayloadType", remoteCodec.get("preferredPayloadType"));
            extendedCodec.add("remoteRtxPayloadType", JsonNull.INSTANCE);
            extendedCodec.add("localParameters", matchingLocalCodec.get("parameters"));
            extendedCodec.add("remoteParameters", remoteCodec.get("parameters"));
            extendedCodec.add("rtcpFeedback", reduceRtcpFeedback(matchingLocalCodec, remoteCodec));

            if (matchingLocalCodec.has("channels")) {
                extendedCodec.add("channels", matchingLocalCodec.get("channels"));
            }

            extendedRtpCapabilities.getAsJsonArray("codecs").add(extendedCodec);
        }

        // Match RTX codecs.
        JsonArray extendedCodecs = extendedRtpCapabilities.getAsJsonArray("codecs");

        for (JsonElement extendedCodecElement : extendedCodecs) {
            JsonObject extendedCodec = extendedCodecElement.getAsJsonObject();

            JsonArray localCodecs = localCaps.getAsJsonArray("codecs");
            Optional<JsonObject> localCodecOpt = StreamSupport.stream(localCodecs.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(localCodec -> isRtxCodec(localCodec) && localCodec.getAsJsonObject("parameters").get("apt").equals(extendedCodec.get("localPayloadType")))
                    .findFirst();

            if (!localCodecOpt.isPresent()) continue;

            JsonObject matchingLocalRtxCodec = localCodecOpt.get();
            JsonArray remoteCodecs = remoteCaps.getAsJsonArray("codecs");
            Optional<JsonObject> remoteCodecOpt = StreamSupport.stream(remoteCodecs.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(remoteCodec -> isRtxCodec(remoteCodec) && remoteCodec.getAsJsonObject("parameters").get("apt").equals(extendedCodec.get("remotePayloadType")))
                    .findFirst();

            if (!remoteCodecOpt.isPresent()) continue;

            JsonObject matchingRemoteRtxCodec = remoteCodecOpt.get();

            extendedCodec.add("localRtxPayloadType", matchingLocalRtxCodec.get("preferredPayloadType"));
            extendedCodec.add("remoteRtxPayloadType", matchingRemoteRtxCodec.get("preferredPayloadType"));
        }

        // Match header extensions.
        JsonArray remoteExts = remoteCaps.getAsJsonArray("headerExtensions");

        for (JsonElement remoteExtElement : remoteExts) {
            JsonObject remoteExt = remoteExtElement.getAsJsonObject();

            JsonArray localExts = localCaps.getAsJsonArray("headerExtensions");
            Optional<JsonObject> localExtOpt = StreamSupport.stream(localExts.spliterator(), false)
                    .map(JsonElement::getAsJsonObject)
                    .filter(localExt -> matchHeaderExtensions(localExt, remoteExt))
                    .findFirst();

            if (!localExtOpt.isPresent()) continue;

            JsonObject matchingLocalExt = localExtOpt.get();

            // TODO: Must do stuff for encrypted extensions.

            JsonObject extendedExt = new JsonObject();
            extendedExt.add("kind", remoteExt.get("kind"));
            extendedExt.add("uri", remoteExt.get("uri"));
            extendedExt.add("sendId", matchingLocalExt.get("preferredId"));
            extendedExt.add("recvId", remoteExt.get("preferredId"));
            extendedExt.add("encrypt", matchingLocalExt.get("preferredEncrypt"));

            String remoteExtDirection = remoteExt.get("direction").getAsString();

            switch (remoteExtDirection) {
                case "sendrecv":
                    extendedExt.addProperty("direction", "sendrecv");
                    break;
                case "recvonly":
                    extendedExt.addProperty("direction", "sendonly");
                    break;
                case "sendonly":
                    extendedExt.addProperty("direction", "recvonly");
                    break;
                case "inactive":
                    extendedExt.addProperty("direction", "inactive");
                    break;
            }

            extendedRtpCapabilities.getAsJsonArray("headerExtensions").add(extendedExt);
        }

        return extendedRtpCapabilities;
    }

    public static JsonObject getRecvRtpCapabilities(JsonObject extendedRtpCapabilities) {
        // Create the resulting rtpCapabilities object
        JsonObject rtpCapabilities = new JsonObject();
        rtpCapabilities.add("codecs", new JsonArray());
        rtpCapabilities.add("headerExtensions", new JsonArray());

        // Iterate over the codecs in the extendedRtpCapabilities object
        JsonArray codecs = extendedRtpCapabilities.getAsJsonArray("codecs");
        for (JsonElement extendedCodecElement : codecs) {
            JsonObject extendedCodec = extendedCodecElement.getAsJsonObject();

            // Create the codec object
            JsonObject codec = new JsonObject();
            codec.add("mimeType", extendedCodec.get("mimeType"));
            codec.add("kind", extendedCodec.get("kind"));
            codec.add("preferredPayloadType", extendedCodec.get("remotePayloadType"));
            codec.add("clockRate", extendedCodec.get("clockRate"));
            codec.add("parameters", extendedCodec.get("localParameters"));
            codec.add("rtcpFeedback", extendedCodec.get("rtcpFeedback"));

            // Optionally add channels if it exists
            if (extendedCodec.has("channels")) {
                codec.add("channels", extendedCodec.get("channels"));
            }

            // Add codec to the rtpCapabilities codecs array
            rtpCapabilities.getAsJsonArray("codecs").add(codec);

            // Add RTX codec if remoteRtxPayloadType exists
            if (!extendedCodec.has("remoteRtxPayloadType") || extendedCodec.get("remoteRtxPayloadType").isJsonNull()) {
                continue;
            }

            // Create mimeType for RTX codec
            String mimeType = extendedCodec.get("kind").getAsString() + "/rtx";

            // Create the rtxCodec object
            JsonObject rtxCodec = new JsonObject();
            rtxCodec.addProperty("mimeType", mimeType);
            rtxCodec.add("kind", extendedCodec.get("kind"));
            rtxCodec.add("preferredPayloadType", extendedCodec.get("remoteRtxPayloadType"));
            rtxCodec.add("clockRate", extendedCodec.get("clockRate"));

            JsonObject rtxParameters = new JsonObject();
            rtxParameters.add("apt", extendedCodec.get("remotePayloadType"));
            rtxCodec.add("parameters", rtxParameters);

            rtxCodec.add("rtcpFeedback", new JsonArray());

            // Add rtxCodec to the rtpCapabilities codecs array
            rtpCapabilities.getAsJsonArray("codecs").add(rtxCodec);

            // TODO: In the future, add FEC, CN, etc., codecs.
        }

        return rtpCapabilities;
    }

    public static JsonObject getSendingRtpParameters(String kind, JsonObject extendedRtpCapabilities) {
        // Create the resulting rtpParameters object
        JsonObject rtpParameters = new JsonObject();
        rtpParameters.add("mid", null);
        rtpParameters.add("codecs", new JsonArray());
        rtpParameters.add("headerExtensions", new JsonArray());
        rtpParameters.add("encodings", new JsonArray());
        rtpParameters.add("rtcp", new JsonObject());

        // Process codecs
        JsonArray codecs = extendedRtpCapabilities.getAsJsonArray("codecs");
        for (JsonElement extendedCodecElement : codecs) {
            JsonObject extendedCodec = extendedCodecElement.getAsJsonObject();

            if (!kind.equals(extendedCodec.get("kind").getAsString())) {
                continue;
            }

            // Create the codec object
            JsonObject codec = new JsonObject();
            codec.add("mimeType", extendedCodec.get("mimeType"));
            codec.add("payloadType", extendedCodec.get("localPayloadType"));
            codec.add("clockRate", extendedCodec.get("clockRate"));
            codec.add("parameters", extendedCodec.get("localParameters"));
            codec.add("rtcpFeedback", extendedCodec.get("rtcpFeedback"));

            // Optionally add channels if it exists
            if (extendedCodec.has("channels")) {
                codec.add("channels", extendedCodec.get("channels"));
            }

            // Add codec to the rtpParameters codecs array
            rtpParameters.getAsJsonArray("codecs").add(codec);

            // Add RTX codec if localRtxPayloadType exists
            if (extendedCodec.has("localRtxPayloadType") && !extendedCodec.get("localRtxPayloadType").isJsonNull()) {
                String mimeType = extendedCodec.get("kind").getAsString() + "/rtx";

                // Create the rtxCodec object
                JsonObject rtxCodec = new JsonObject();
                rtxCodec.addProperty("mimeType", mimeType);
                rtxCodec.add("payloadType", extendedCodec.get("localRtxPayloadType"));
                rtxCodec.add("clockRate", extendedCodec.get("clockRate"));

                JsonObject rtxParameters = new JsonObject();
                rtxParameters.add("apt", extendedCodec.get("localPayloadType"));
                rtxCodec.add("parameters", rtxParameters);

                rtxCodec.add("rtcpFeedback", new JsonArray());

                // Add rtxCodec to the rtpParameters codecs array
                rtpParameters.getAsJsonArray("codecs").add(rtxCodec);
            }
        }

        // Process header extensions
        JsonArray headerExtensions = extendedRtpCapabilities.getAsJsonArray("headerExtensions");
        for (JsonElement extendedExtensionElement : headerExtensions) {
            JsonObject extendedExtension = extendedExtensionElement.getAsJsonObject();

            if (!kind.equals(extendedExtension.get("kind").getAsString())) {
                continue;
            }

            String direction = extendedExtension.get("direction").getAsString();

            // Ignore RTP extensions not valid for sending.
            if (!"sendrecv".equals(direction) && !"sendonly".equals(direction)) {
                continue;
            }

            // Create the header extension object
            JsonObject ext = new JsonObject();
            ext.add("uri", extendedExtension.get("uri"));
            ext.add("id", extendedExtension.get("sendId"));
            ext.add("encrypt", extendedExtension.get("encrypt"));
            ext.add("parameters", new JsonObject());

            // Add ext to the rtpParameters headerExtensions array
            rtpParameters.getAsJsonArray("headerExtensions").add(ext);
        }

        return rtpParameters;
    }

    private static JsonElement findHeaderExtension(JsonArray headerExtensions, String uri) {
        for (JsonElement extElement : headerExtensions) {
            if (extElement.getAsJsonObject().get("uri").getAsString().equals(uri)) {
                return extElement;
            }
        }
        return null;
    }

    public static JsonObject getSendingRemoteRtpParameters(String kind, JsonObject extendedRtpCapabilities) {

        JsonObject rtpParameters = new JsonObject();
        rtpParameters.add("mid", null);
        rtpParameters.add("codecs", new JsonArray());
        rtpParameters.add("headerExtensions", new JsonArray());
        rtpParameters.add("encodings", new JsonArray());
        rtpParameters.add("rtcp", new JsonObject());

        for (JsonElement extendedCodecElement : extendedRtpCapabilities.getAsJsonArray("codecs")) {
            JsonObject extendedCodec = extendedCodecElement.getAsJsonObject();

            if (!kind.equals(extendedCodec.get("kind").getAsString())) {
                continue;
            }

            JsonObject codec = new JsonObject();
            codec.add("mimeType", extendedCodec.get("mimeType"));
            codec.add("payloadType", extendedCodec.get("localPayloadType"));
            codec.add("clockRate", extendedCodec.get("clockRate"));
            codec.add("parameters", extendedCodec.get("remoteParameters"));
            codec.add("rtcpFeedback", extendedCodec.get("rtcpFeedback"));

            if (extendedCodec.has("channels")) {
                codec.add("channels", extendedCodec.get("channels"));
            }

            rtpParameters.getAsJsonArray("codecs").add(codec);

            // Add RTX codec if localRtxPayloadType exists
            if (extendedCodec.has("localRtxPayloadType") && !extendedCodec.get("localRtxPayloadType").isJsonNull()) {
                String mimeType = extendedCodec.get("kind").getAsString() + "/rtx";
                JsonObject rtxCodec = new JsonObject();
                rtxCodec.addProperty("mimeType", mimeType);
                rtxCodec.add("payloadType", extendedCodec.get("localRtxPayloadType"));
                rtxCodec.add("clockRate", extendedCodec.get("clockRate"));

                JsonObject rtxParameters = new JsonObject();
                rtxParameters.add("apt", extendedCodec.get("localPayloadType"));
                rtxCodec.add("parameters", rtxParameters);

                rtxCodec.add("rtcpFeedback", new JsonArray());

                rtpParameters.getAsJsonArray("codecs").add(rtxCodec);
            }
        }

        for (JsonElement extendedExtensionElement : extendedRtpCapabilities.getAsJsonArray("headerExtensions")) {
            JsonObject extendedExtension = extendedExtensionElement.getAsJsonObject();

            if (!kind.equals(extendedExtension.get("kind").getAsString())) {
                continue;
            }

            String direction = extendedExtension.get("direction").getAsString();

            // Ignore RTP extensions not valid for sending
            if (!"sendrecv".equals(direction) && !"sendonly".equals(direction)) {
                continue;
            }

            JsonObject ext = new JsonObject();
            ext.add("uri", extendedExtension.get("uri"));
            ext.add("id", extendedExtension.get("sendId"));
            ext.add("encrypt", extendedExtension.get("encrypt"));
            ext.add("parameters", new JsonObject());

            rtpParameters.getAsJsonArray("headerExtensions").add(ext);
        }

        JsonArray headerExtensionsArray = rtpParameters.getAsJsonArray("headerExtensions");

        // Reduce codecs' RTCP feedback. Use Transport-CC if available, REMB otherwise
        JsonElement transportWideCcHeaderExtension = findHeaderExtension(headerExtensionsArray, "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01");

        if (transportWideCcHeaderExtension != null) {
            removeRtcpFeedback(rtpParameters, "goog-remb");
            return rtpParameters;
        }

        JsonElement absSendTimeHeaderExtension = findHeaderExtension(headerExtensionsArray, "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time");

        if (absSendTimeHeaderExtension != null) {
            removeRtcpFeedback(rtpParameters, "transport-cc");
            return rtpParameters;
        }

        // Default case: remove both 'transport-cc' and 'goog-remb'
        removeRtcpFeedback(rtpParameters, "transport-cc", "goog-remb");

        return rtpParameters;
    }

    private static void removeRtcpFeedback(JsonObject rtpParameters, String... feedbackTypes) {
        JsonArray codecs = rtpParameters.getAsJsonArray("codecs");

        for (JsonElement codecElement : codecs) {
            JsonObject codec = codecElement.getAsJsonObject();
            JsonArray rtcpFeedback = codec.getAsJsonArray("rtcpFeedback");

            Iterator<JsonElement> it = rtcpFeedback.iterator();
            while (it.hasNext()) {
                JsonObject feedback = it.next().getAsJsonObject();
                String type = feedback.get("type").getAsString();

                for (String feedbackType : feedbackTypes) {
                    if (type.equals(feedbackType)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
    }
}
