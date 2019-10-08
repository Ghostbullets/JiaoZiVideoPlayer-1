package cn.jzvd.demo.CustomMedia;

import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.IOException;

import cn.jzvd.JZMediaInterface;
import cn.jzvd.Jzvd;
import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.IjkTimedText;

/**
 *
 * Created by Nathen on 2017/11/18.
 * IjkPlayer播放引擎
 */

public class JZMediaIjk extends JZMediaInterface implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnCompletionListener, IMediaPlayer.OnErrorListener, IMediaPlayer.OnInfoListener, IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnTimedTextListener {
    IjkMediaPlayer ijkMediaPlayer;

    public JZMediaIjk(Jzvd jzvd) {
        super(jzvd);
    }

    @Override
    public void start() {
        if (ijkMediaPlayer != null) ijkMediaPlayer.start();
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

            ijkMediaPlayer = new IjkMediaPlayer();
            //0表示使用av解码器，1表示使用媒体解码器。
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 0);
            //open sl es （Open Sound Library for Embedded Systems 为嵌入式系统打开声音库）
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "overlay-format", IjkMediaPlayer.SDL_FCC_RV32);
            //跳帧处理,放CPU处理较慢时，进行跳帧处理，保证播放流程，画面和声音同步
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1);
            //0为一进入就播放,1为进入时不播放
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "http-detect-range-support", 0);
            //重连模式，如果中途服务器断开了连接，让它重新连接 ,默认值为0不重连，传入几就重连几次
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT,"reconnect",1);
            //设置是否开启环路过滤: 0开启，画面质量高，解码开销大，48关闭，画面质量差点，解码开销小
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_CODEC, "skip_loop_filter", 48);
            // 设置缓冲区,单位是kb
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "max-buffer-size", 1024 * 1024);
            ijkMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "enable-accurate-seek", 1);

            ijkMediaPlayer.setOnPreparedListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnVideoSizeChangedListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnCompletionListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnErrorListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnInfoListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnBufferingUpdateListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnSeekCompleteListener(JZMediaIjk.this);
            ijkMediaPlayer.setOnTimedTextListener(JZMediaIjk.this);

            try {
                //设置数据源
                ijkMediaPlayer.setDataSource(jzvd.jzDataSource.getCurrentUrl().toString());
                //设置此媒体播放器的音频流类型
                ijkMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                //设置屏幕常亮
                ijkMediaPlayer.setScreenOnWhilePlaying(true);
                //准备播放
                ijkMediaPlayer.prepareAsync();

                ijkMediaPlayer.setSurface(new Surface(jzvd.textureView.getSurfaceTexture()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    @Override
    public void pause() {
        ijkMediaPlayer.pause();
    }

    @Override
    public boolean isPlaying() {
        return ijkMediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        ijkMediaPlayer.seekTo(time);
    }

    @Override
    public void release() {
        if (mMediaHandler != null && mMediaHandlerThread != null && ijkMediaPlayer != null) {//不知道有没有妖孽
            HandlerThread tmpHandlerThread = mMediaHandlerThread;
            IjkMediaPlayer tmpMediaPlayer = ijkMediaPlayer;
            JZMediaInterface.SAVED_SURFACE = null;

            mMediaHandler.post(() -> {
                tmpMediaPlayer.setSurface(null);
                tmpMediaPlayer.release();
                tmpHandlerThread.quit();
            });
            ijkMediaPlayer = null;
        }
    }

    @Override
    public long getCurrentPosition() {
        return ijkMediaPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return ijkMediaPlayer.getDuration();
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        ijkMediaPlayer.setVolume(leftVolume, rightVolume);
    }

    @Override
    public void setSpeed(float speed) {
        ijkMediaPlayer.setSpeed(speed);
    }

    @Override
    public void onPrepared(IMediaPlayer iMediaPlayer) {
        handler.post(() -> jzvd.onPrepared());
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int i, int i1, int i2, int i3) {
        handler.post(() -> jzvd.onVideoSizeChanged(iMediaPlayer.getVideoWidth(), iMediaPlayer.getVideoHeight()));
    }

    @Override
    public boolean onError(IMediaPlayer iMediaPlayer, final int what, final int extra) {
        handler.post(() -> jzvd.onError(what, extra));
        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer iMediaPlayer, final int what, final int extra) {
        handler.post(() -> jzvd.onInfo(what, extra));
        return false;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer iMediaPlayer, final int percent) {
        handler.post(() -> jzvd.setBufferProgress(percent));
    }

    @Override
    public void onSeekComplete(IMediaPlayer iMediaPlayer) {
        handler.post(() -> jzvd.onSeekComplete());
    }

    @Override
    public void onTimedText(IMediaPlayer iMediaPlayer, IjkTimedText ijkTimedText) {

    }

    @Override
    public void setSurface(Surface surface) {
        ijkMediaPlayer.setSurface(surface);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
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

    @Override
    public void onCompletion(IMediaPlayer iMediaPlayer) {
        handler.post(() -> jzvd.onAutoCompletion());
    }
}
