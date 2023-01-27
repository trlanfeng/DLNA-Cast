package com.android.cast.dlna.dmr;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Point;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.cast.dlna.dmr.DLNARendererService.RendererServiceBinder;

import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.model.Channel;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.renderingcontrol.lastchange.ChannelVolume;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlVariable;

/**
 *
 */
public class DLNARendererActivity extends AppCompatActivity {

    private static final String KEY_EXTRA_CURRENT_URI = "Renderer.KeyExtra.CurrentUri";

    public static void startActivity(Context context, String currentURI) {
        Intent intent = new Intent(context, DLNARendererActivity.class);
        intent.putExtra(KEY_EXTRA_CURRENT_URI, currentURI);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // start from service content,should add 'FLAG_ACTIVITY_NEW_TASK' flag.
        context.startActivity(intent);
    }

    private final UnsignedIntegerFourBytes INSTANCE_ID = new UnsignedIntegerFourBytes(0);
    private VideoView mVideoView;
    private ProgressBar mProgressBar;
    private DLNARendererService mRendererService;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRendererService = ((RendererServiceBinder) service).getRendererService();
            mRendererService.setRenderControl(new IDLNARenderControl.VideoViewRenderControl(mVideoView));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRendererService = null;
        }
    };

    private int toastOffsetX = 0;
    private int toastOffsetY = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        toastOffsetX = (int) (point.x * 0.2);
        toastOffsetY = (int) (point.y * 0.15);
        setContentView(R.layout.activity_dlna_renderer);
        mVideoView = findViewById(R.id.video_view);
        mProgressBar = findViewById(R.id.video_progress);
        bindService(new Intent(this, DLNARendererService.class), mServiceConnection, Service.BIND_AUTO_CREATE);
        openMedia(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        openMedia(intent);
    }

    private void openMedia(Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            mProgressBar.setVisibility(View.VISIBLE);
            String currentUri = bundle.getString(KEY_EXTRA_CURRENT_URI);
            mVideoView.setVideoURI(Uri.parse(currentUri));
            mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    mp.start();
                    mProgressBar.setVisibility(View.INVISIBLE);
                    notifyTransportStateChanged(TransportState.PLAYING);
                }
            });
            mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    mProgressBar.setVisibility(View.INVISIBLE);
                    notifyTransportStateChanged(TransportState.STOPPED);
                    finish();
                    return true;
                }
            });
            mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mProgressBar.setVisibility(View.INVISIBLE);
                    notifyTransportStateChanged(TransportState.STOPPED);
                    finish();
                }
            });
        } else {
            Toast.makeText(this, "没有找到有效的视频地址，请检查...", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (mVideoView != null) mVideoView.stopPlayback();
        notifyTransportStateChanged(TransportState.STOPPED);
        unbindService(mServiceConnection);
        super.onDestroy();
    }

    public String timeToString(int duration) {
        if (duration <= 0) return "00:00";
        String ss = "";
        String ms = "";
        int d = duration / 1000;
        int s = d % 60;
        int m = (d - s) / 60;
        if (s < 10) {
            ss = "0" + s;
        } else {
            ss = String.valueOf(s);
        }
        if (m < 10) {
            ms = "0" + m;
        } else {
            ms = String.valueOf(m);
        }
        return ms + ":" + ss;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = super.onKeyDown(keyCode, event);
        if (mRendererService != null && mVideoView != null) {
//            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
//                int volume = ((AudioManager) getApplication().getSystemService(Context.AUDIO_SERVICE)).getStreamVolume(AudioManager.STREAM_MUSIC);
//                notifyRenderVolumeChanged(volume);
//            } else if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
//                if (mVideoView != null && mVideoView.isPlaying()) {
//                    mVideoView.pause();
//                    notifyTransportStateChanged(TransportState.PAUSED_PLAYBACK);
//                } else if (mVideoView != null) {
//                    mVideoView.resume();
//                    notifyTransportStateChanged(TransportState.PLAYING);
//                }
//            }
            // 处理遥控器
            int curPosition = mVideoView.getCurrentPosition();
            int targetPosition = curPosition;
            int duration = mVideoView.getDuration();
            String totalString = timeToString(duration);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (mVideoView.isPlaying()) {
                        mVideoView.pause();
                    } else {
                        mVideoView.start();
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT: {
                    targetPosition = Math.max(0, curPosition - 15000);
                    mVideoView.seekTo(targetPosition);
                    String targetString = timeToString(targetPosition);
                    String toastString = targetString + " / " + totalString;
                    Toast toast = Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.RIGHT | Gravity.BOTTOM, toastOffsetX, toastOffsetY);
                    toast.show();
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    targetPosition = Math.min(curPosition + 15000, duration);
                    mVideoView.seekTo(targetPosition);
                    String targetString = timeToString(targetPosition);
                    String toastString = targetString + " / " + totalString;
                    Toast toast = Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.RIGHT | Gravity.BOTTOM, toastOffsetX, toastOffsetY);
                    toast.show();
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    targetPosition = Math.max(0, curPosition - 180000);
                    mVideoView.seekTo(targetPosition);
                    String targetString = timeToString(targetPosition);
                    String toastString = targetString + " / " + totalString;
                    Toast toast = Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.RIGHT | Gravity.BOTTOM, toastOffsetX, toastOffsetY);
                    toast.show();
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    targetPosition = Math.min(curPosition + 180000, duration);
                    mVideoView.seekTo(targetPosition);
                    String targetString = timeToString(targetPosition);
                    String toastString = targetString + " / " + totalString;
                    Toast toast = Toast.makeText(getApplicationContext(), toastString, Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.RIGHT | Gravity.BOTTOM, toastOffsetX, toastOffsetY);
                    toast.show();
                    break;
                }
            }
        }
        return handled;
    }

    private void notifyTransportStateChanged(TransportState transportState) {
        if (mRendererService != null) {
            mRendererService.getAvTransportLastChange()
                    .setEventedValue(INSTANCE_ID, new AVTransportVariable.TransportState(transportState));
        }
    }

    private void notifyRenderVolumeChanged(int volume) {
        if (mRendererService != null) {
            mRendererService.getAudioControlLastChange()
                    .setEventedValue(INSTANCE_ID, new RenderingControlVariable.Volume(new ChannelVolume(Channel.Master, volume)));
        }
    }
}
