package cn.jzvd;

import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.RequiresApi;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by Nathen on 2017/11/8.
 * 实现系统的播放引擎
 */
public class JZMediaSystem extends JZMediaInterface implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener, MediaPlayer.OnVideoSizeChangedListener {

    public MediaPlayer mediaPlayer;

    public JZMediaSystem(Jzvd jzvd) {
        super(jzvd);
    }

    @Override
    public void prepare() {
        //释放旧视频资源
        release();
        mMediaHandlerThread = new HandlerThread("JZVD");
        mMediaHandlerThread.start();
        mMediaHandler = new Handler(mMediaHandlerThread.getLooper());//主线程还是非主线程，就在这里
        handler = new Handler();

        mMediaHandler.post(() -> {
            try {
                mediaPlayer = new MediaPlayer();
                //设置此媒体播放器的音频流类型
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                //是否循环播放视频
                mediaPlayer.setLooping(jzvd.jzDataSource.looping);
                //设置屏幕常亮
                mediaPlayer.setScreenOnWhilePlaying(true);
                //设置准备、播放完毕、视频缓冲进度、跳转播放、播放错误、播放提示信息、警告、视频宽高变化等监听
                mediaPlayer.setOnPreparedListener(JZMediaSystem.this);
                mediaPlayer.setOnCompletionListener(JZMediaSystem.this);
                mediaPlayer.setOnBufferingUpdateListener(JZMediaSystem.this);
                mediaPlayer.setOnSeekCompleteListener(JZMediaSystem.this);
                mediaPlayer.setOnErrorListener(JZMediaSystem.this);
                mediaPlayer.setOnInfoListener(JZMediaSystem.this);
                mediaPlayer.setOnVideoSizeChangedListener(JZMediaSystem.this);

                Object currentKey = jzvd.jzDataSource.getCurrentKey();
                if (currentKey instanceof AssetFileDescriptor) {
                    AssetFileDescriptor assetFileDescriptor = (AssetFileDescriptor) currentKey;
                    mediaPlayer.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());
                } else {
                    Class<MediaPlayer> clazz = MediaPlayer.class;
                    Method method = clazz.getDeclaredMethod("setDataSource", String.class, Map.class);
                    method.invoke(mediaPlayer, currentKey.toString(), jzvd.jzDataSource.headerMap);
                }

                //准备播放
                mediaPlayer.prepareAsync();
                mediaPlayer.setSurface(new Surface(SAVED_SURFACE));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void start() {
        mMediaHandler.post(() -> mediaPlayer.start());
    }

    @Override
    public void pause() {
        mMediaHandler.post(() -> mediaPlayer.pause());
    }

    @Override
    public boolean isPlaying() {
        return mediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        mMediaHandler.post(() -> {
            try {
                mediaPlayer.seekTo((int) time);
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void release() {//not perfect change you later
        if (mMediaHandler != null && mMediaHandlerThread != null && mediaPlayer != null) {//不知道有没有妖孽
            HandlerThread tmpHandlerThread = mMediaHandlerThread;
            MediaPlayer tmpMediaPlayer = mediaPlayer;
            mMediaHandler.post(() -> {
                tmpMediaPlayer.setSurface(null);
                tmpMediaPlayer.release();
                tmpHandlerThread.quit();
            });
            mediaPlayer = null;
        }
    }

    //TODO 测试这种问题是否在threadHandler中是否正常，所有的操作mediaplayer是否不需要thread，挨个测试，是否有问题
    @Override
    public long getCurrentPosition() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentPosition();
        } else {
            return 0;
        }
    }

    @Override
    public long getDuration() {
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        } else {
            return 0;
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mMediaHandler == null) return;
        mMediaHandler.post(() -> {
            if (mediaPlayer != null) mediaPlayer.setVolume(leftVolume, rightVolume);
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setSpeed(float speed) {
        PlaybackParams pp = mediaPlayer.getPlaybackParams();
        pp.setSpeed(speed);
        mediaPlayer.setPlaybackParams(pp);
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {//当媒体文件准备好播放时调用
        mediaPlayer.start();
        if (jzvd.jzDataSource.getCurrentUrl().toString().toLowerCase().contains("mp3") ||
                jzvd.jzDataSource.getCurrentUrl().toString().toLowerCase().contains("wav")) {
            handler.post(() -> jzvd.onPrepared());//如果是mp3音频，走这里
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {//在播放期间到达媒体源的末尾
        handler.post(() -> jzvd.onAutoCompletion());
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, final int percent) {//在缓冲接收到的媒体流时更新状态百分比
        handler.post(() -> jzvd.setBufferProgress(percent));
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {//跳转播放操作已完成
        handler.post(() -> jzvd.onSeekComplete());
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, final int what, final int extra) {//播放出错
        handler.post(() -> jzvd.onError(what, extra));
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, final int what, final int extra) {//播放中出现的提示信息或警告
        handler.post(() -> {
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {//玩家只需要推动第一个视频帧进行渲染。
                if (jzvd.state == Jzvd.STATE_PREPARING
                        || jzvd.state == Jzvd.STATE_PREPARING_CHANGING_URL) {
                    jzvd.onPrepared();//真正的prepared
                }
            } else {
                jzvd.onInfo(what, extra);
            }
        });
        return false;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {//视频宽高改变
        handler.post(() -> jzvd.onVideoSizeChanged(width, height));
    }

    @Override
    public void setSurface(Surface surface) {
        mediaPlayer.setSurface(surface);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {//SurfaceTexture可使用时回调
        if (SAVED_SURFACE == null) {
            SAVED_SURFACE = surface;
            prepare();
        } else {
            jzvd.textureView.setSurfaceTexture(SAVED_SURFACE);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
