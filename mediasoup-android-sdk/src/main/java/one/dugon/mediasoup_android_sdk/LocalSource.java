package one.dugon.mediasoup_android_sdk;

import org.webrtc.MediaStreamTrack;

abstract class LocalSource {

    abstract MediaStreamTrack getTrack();

    abstract String getKind();

}