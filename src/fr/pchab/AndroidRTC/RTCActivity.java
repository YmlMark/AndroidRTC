package fr.pchab.AndroidRTC;

import java.util.List;

import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoRenderer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

public class RTCActivity extends Activity implements WebRtcClient.RTCListener{
  private final static int VIDEO_CALL_SENT = 666;
  private VideoStreamsView vsv;
  private WebRtcClient client;
  private String callerId;
  private String mSocketAddress;
  private String mPathPrefix;
  private final static String TAG = RTCActivity.class.getName();
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_NO_TITLE);

    PeerConnectionFactory.initializeAndroidGlobals(this);

    // Camera display view
    Point displaySize = new Point();
    getWindowManager().getDefaultDisplay().getSize(displaySize);
    vsv = new VideoStreamsView(this, displaySize);
    mPathPrefix  = getResources().getString(R.string.path_prefix);
    mSocketAddress = "http://" + getResources().getString(R.string.host);
    mSocketAddress += (":"+getResources().getString(R.string.port));
    Log.d(TAG,"WebRtcClient socket address: "+mSocketAddress);
    client = new WebRtcClient(this, mSocketAddress);

    final Intent intent = getIntent();
    final String action = intent.getAction();
    Log.d(TAG,"onCreate() "+intent.getAction());
    if (Intent.ACTION_VIEW.equals(action)) {
      callerId = intent.getData().getLastPathSegment();
    }
  }

  public void onConfigurationChanged(Configuration newConfig)
  {
    super.onConfigurationChanged(newConfig);
    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
  }

  @Override
  public void onPause() {
    super.onPause();
    vsv.onPause();
  }

  @Override
  public void onResume() {
    super.onResume();
    vsv.onResume();
  }

  @Override
  public void onCallReady(String callId) {
	  Log.d(TAG,"onCallReady() callId ="+(callId == null ? "nil" : callId));
    if(callerId != null) {
      try {
        answer(callerId);
      } catch (JSONException e) {
        e.printStackTrace();
      }
    } else {
      call(callId);
    }
  }

  public void answer(String callerId) throws JSONException {
    client.sendMessage(callerId, "init", null);
    startCam();
  }

  public void call(String callId) {
	  String extra = mSocketAddress + mPathPrefix + "/" + callId;
	  Log.d(TAG,"call() intent extra: "+extra);
    Intent msg = new Intent(Intent.ACTION_SEND);
    msg.putExtra(Intent.EXTRA_TEXT, extra);
    msg.setType("text/plain");
    startActivityForResult(Intent.createChooser(msg, "Call someone :"), VIDEO_CALL_SENT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if(requestCode == VIDEO_CALL_SENT) {
      startCam();
    }
  }

  public void startCam() {
    setContentView(vsv);
    // Camera settings
    client.setCamera("front", "640", "480");
    client.start("android_test", true);
  }

  @Override
  public void onStatusChanged(final String newStatus) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
      }
    });
  }

  @Override
  public void onLocalStream(MediaStream localStream) {
    localStream.videoTracks.get(0).addRenderer(new VideoRenderer(new VideoCallbacks(vsv, 0)));
  }

  @Override
  public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
	  Log.d(TAG,"onAddRemoteStream() "+remoteStream.label() + " " + endPoint);
    remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(new VideoCallbacks(vsv, endPoint)));
    vsv.shouldDraw[endPoint] = true;
  }

  @Override
  public void onRemoveRemoteStream(MediaStream remoteStream, int endPoint) {
    remoteStream.videoTracks.get(0).dispose();
    vsv.shouldDraw[endPoint] = false;
  }

  // Implementation detail: bridge the VideoRenderer.Callbacks interface to the
  // VideoStreamsView implementation.
  private class VideoCallbacks implements VideoRenderer.Callbacks {
    private final VideoStreamsView view;
    private final int stream;

    public VideoCallbacks(VideoStreamsView view, int stream) {
      this.view = view;
      this.stream = stream;
    }

    @Override
    public void setSize(final int width, final int height) {
      view.queueEvent(new Runnable() {
        public void run() {
          view.setSize(stream, width, height);
        }
      });
    }

    @Override
    public void renderFrame(VideoRenderer.I420Frame frame) {
      view.queueFrame(stream, frame);
    }
  }
}
