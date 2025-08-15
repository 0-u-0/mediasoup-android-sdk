package one.dugon.mediasoup_android_sdk;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStreamTrack;

public class LocalAudioSource extends  LocalSource{
    private AudioTrack track;
    private AudioSource source;

    public LocalAudioSource(AudioSource audioSource, AudioTrack audioTrack) {
        source = audioSource;
        track = audioTrack;
    }

    @Override
    MediaStreamTrack getTrack() {
        return track;
    }

    @Override
    String getKind() {
        return "audio";
    }
}
