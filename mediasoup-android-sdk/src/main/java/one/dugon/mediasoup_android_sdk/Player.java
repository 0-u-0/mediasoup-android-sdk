package one.dugon.mediasoup_android_sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import org.webrtc.EglBase;
import org.webrtc.SurfaceViewRenderer;

public class Player extends FrameLayout {

    private SurfaceViewRenderer renderer;

    public Player(Context context) {
        super(context);
        init(context);
    }

    public Player(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        renderer = new SurfaceViewRenderer(context);
        addView(renderer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void initEgl(EglBase.Context context){
        renderer.init(context, null);
    }

    public void play(LocalVideoSource source){
        source.track.addSink(renderer);
    }



}
