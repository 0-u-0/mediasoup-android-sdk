package one.dugon.mediasoup_android_sdk.sdp;

import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoteSdp {
    private JsonObject iceParameters;
    private JsonArray iceCandidates;
    private JsonObject dtlsParameters;
    private JsonObject sctpParameters;
    private JsonObject sdpObject;
    private List<MediaSection> mediaSections;
    private Map<String, Integer> midToIndex;
    private String firstMid;

    public RemoteSdp(JsonObject iceParameters, JsonArray iceCandidates, JsonObject dtlsParameters, JsonObject sctpParameters) {
        this.iceParameters = iceParameters;
        this.iceCandidates = iceCandidates;
        this.dtlsParameters = dtlsParameters;
        this.sctpParameters = sctpParameters;
        this.mediaSections = new ArrayList<>();
        this.midToIndex = new HashMap<>();
        initializeSdpObject();
    }

    private void initializeSdpObject() {
        sdpObject = new JsonObject();
        sdpObject.addProperty("version", 0);

        JsonObject origin = new JsonObject();
        origin.addProperty("address", "0.0.0.0");
        origin.addProperty("ipVer", 4);
        origin.addProperty("netType", "IN");
        origin.addProperty("sessionId", 10000);
        origin.addProperty("sessionVersion", 0);
        origin.addProperty("username", "dugon");
        sdpObject.add("origin", origin);

        sdpObject.addProperty("name", "-");

        JsonObject timing = new JsonObject();
        timing.addProperty("start", 0);
        timing.addProperty("stop", 0);
        sdpObject.add("timing", timing);

        sdpObject.add("media", new JsonArray());

        if (iceParameters.has("iceLite")) {
            sdpObject.addProperty("icelite", "ice-lite");
        }

        JsonObject msidSemantic = new JsonObject();
        msidSemantic.addProperty("semantic", "WMS");
        msidSemantic.addProperty("token", "*");
        sdpObject.add("msidSemantic", msidSemantic);

        JsonArray fingerprints = dtlsParameters.getAsJsonArray("fingerprints");
        int numFingerprints = fingerprints.size();
        JsonObject lastFingerprint = fingerprints.get(numFingerprints - 1).getAsJsonObject();
        JsonObject fingerprint = new JsonObject();
        fingerprint.addProperty("type", lastFingerprint.get("algorithm").getAsString());
        fingerprint.addProperty("hash", lastFingerprint.get("value").getAsString());
        sdpObject.add("fingerprint", fingerprint);

        JsonObject groups = new JsonObject();
        groups.addProperty("type", "BUNDLE");
        groups.addProperty("mids", "");
        sdpObject.add("groups", groups);
    }

    public void updateIceParameters(JsonObject newIceParameters) {
        this.iceParameters = newIceParameters;

        if (iceParameters.has("iceLite")) {
            sdpObject.addProperty("icelite", "ice-lite");
        }

        for (int i = 0; i < mediaSections.size(); i++) {
            MediaSection mediaSection = mediaSections.get(i);
            mediaSection.setIceParameters(newIceParameters);
            sdpObject.get("media").getAsJsonArray().set(i, mediaSection.getObject());
        }
    }

    public void updateDtlsRole(String role) {
        dtlsParameters.addProperty("role", role);

        if (iceParameters.has("iceLite")) {
            sdpObject.addProperty("icelite", "ice-lite");
        }

        for (int i = 0; i < mediaSections.size(); i++) {
            MediaSection mediaSection = mediaSections.get(i);
            mediaSection.setDtlsRole(role);
            sdpObject.get("media").getAsJsonArray().set(i, mediaSection.getObject());
        }
    }

    public MediaSectionIdx getNextMediaSectionIdx() {
        for (int i = 0; i < mediaSections.size(); i++) {
            MediaSection mediaSection = mediaSections.get(i);
            if (mediaSection.isClosed()) {
                return new MediaSectionIdx(i, mediaSection.getMid());
            }
        }
        return new MediaSectionIdx(mediaSections.size());
    }

    public void send(JsonObject offerMediaObject, String reuseMid, JsonObject offerRtpParameters, JsonObject answerRtpParameters, JsonObject codecOptions) {
        AnswerMediaSection mediaSection = new AnswerMediaSection(
                iceParameters,
                iceCandidates,
                dtlsParameters,
                sctpParameters,
                offerMediaObject,
                offerRtpParameters,
                answerRtpParameters,
                codecOptions);

        if (!reuseMid.isEmpty()) {
            replaceMediaSection(mediaSection, reuseMid);
        } else {
            addMediaSection(mediaSection);
        }
    }

    public void sendSctpAssociation(JsonObject offerMediaObject) {
        JsonObject emptyJson = new JsonObject();
        AnswerMediaSection mediaSection = new AnswerMediaSection(
                iceParameters,
                iceCandidates,
                dtlsParameters,
                sctpParameters,
                offerMediaObject,
                emptyJson,
                emptyJson,
                null);
        addMediaSection(mediaSection);
    }

    public void recvSctpAssociation() {
        JsonObject emptyJson = new JsonObject();
        OfferMediaSection mediaSection = new OfferMediaSection(
                iceParameters,
                iceCandidates,
                dtlsParameters,
                sctpParameters,
                "datachannel",
                "application",
                emptyJson,
                "",
                "");
        addMediaSection(mediaSection);
    }

    public void receive(String mid, String kind, JsonObject offerRtpParameters, String streamId, String trackId) {
        OfferMediaSection mediaSection = new OfferMediaSection(
                iceParameters,
                iceCandidates,
                dtlsParameters,
                null,
                mid,
                kind,
                offerRtpParameters,
                streamId,
                trackId);

        for (int i = 0; i < mediaSections.size(); i++) {
            MediaSection existingSection = mediaSections.get(i);
            if (existingSection.isClosed()) {
                replaceMediaSection(mediaSection, existingSection.getMid());
                return;
            }
        }
        addMediaSection(mediaSection);
    }

    public void disableMediaSection(String mid) {
        int idx = midToIndex.get(mid);
        MediaSection mediaSection = mediaSections.get(idx);
        mediaSection.disable();
    }

    public void closeMediaSection(String mid) {
        int idx = midToIndex.get(mid);
        MediaSection mediaSection = mediaSections.get(idx);

        if (mid.equals(firstMid)) {
            mediaSection.disable();
        } else {
            mediaSection.close();
        }

        sdpObject.get("media").getAsJsonArray().set(idx, mediaSection.getObject());
        regenerateBundleMids();
    }

    public String getSdp() {
        int version = sdpObject.getAsJsonObject("origin").get("sessionVersion").getAsInt();
        sdpObject.getAsJsonObject("origin").addProperty("sessionVersion", ++version);
        Log.d("exttest",sdpObject.getAsJsonArray("media").get(0).toString());
        return Writer.write(sdpObject);
    }

    private void addMediaSection(MediaSection newMediaSection) {
        if (firstMid == null) {
            firstMid = newMediaSection.getMid();
        }

        mediaSections.add(newMediaSection);
        midToIndex.put(newMediaSection.getMid(), mediaSections.size() - 1);
        sdpObject.get("media").getAsJsonArray().add(newMediaSection.getObject());

        regenerateBundleMids();
    }

    private void replaceMediaSection(MediaSection newMediaSection, String reuseMid) {
        if (!reuseMid.isEmpty()) {
            int idx = midToIndex.get(reuseMid);
            MediaSection oldMediaSection = mediaSections.get(idx);
            mediaSections.set(idx, newMediaSection);
            midToIndex.remove(oldMediaSection.getMid());
            midToIndex.put(newMediaSection.getMid(), idx);

            sdpObject.get("media").getAsJsonArray().set(idx, newMediaSection.getObject());
            regenerateBundleMids();
        } else {
            int idx = midToIndex.get(newMediaSection.getMid());
            MediaSection oldMediaSection = mediaSections.get(idx);
            mediaSections.set(idx, newMediaSection);
            sdpObject.get("media").getAsJsonArray().set(mediaSections.size() - 1, newMediaSection.getObject());
        }
    }

    private void regenerateBundleMids() {
        String mids = "";
        for (MediaSection mediaSection : mediaSections) {
            if (!mediaSection.isClosed()) {
                if (mids.isEmpty()) {
                    mids = mediaSection.getMid();
                } else {
                    mids += " " + mediaSection.getMid();
                }
            }
        }
        sdpObject.getAsJsonObject("groups").addProperty("mids", mids);
    }

    public static class MediaSectionIdx {
        public int idx;
        public String mid;

        public MediaSectionIdx(int idx, String mid) {
            this.idx = idx;
            this.mid = mid;
        }

        public MediaSectionIdx(int idx) {
            this.idx = idx;
        }
    }
}
