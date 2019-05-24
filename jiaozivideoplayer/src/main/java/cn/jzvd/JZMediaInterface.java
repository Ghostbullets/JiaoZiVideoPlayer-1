package cn.jzvd;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;

/**
 * Created by Nathen on 2017/11/7.
 * 自定义播放器要实现的接口
 */
public abstract class JZMediaInterface implements TextureView.SurfaceTextureListener {

    public static SurfaceTexture SAVED_SURFACE;//用于呈现视频内容
    public HandlerThread mMediaHandlerThread;//音频播放子线程
    public Handler mMediaHandler;//在上面的子线程中执行音频相关操作，例如播放、暂停等
    public Handler handler;//主线程中回调上诉的音频操作
    public Jzvd jzvd;//自定义视频控件


    public JZMediaInterface(Jzvd jzvd) {
        this.jzvd = jzvd;
    }

    /**
     * 开始播放
     */
    public abstract void start();

    /**
     * 准备播放
     */
    public abstract void prepare();

    /**
     * 暂停播放
     */
    public abstract void pause();

    /**
     * 是否在播放
     */

    public abstract boolean isPlaying();

    /**
     * 跳转播放
     *
     * @param time
     */
    public abstract void seekTo(long time);

    /**
     * 释放资源
     */
    public abstract void release();

    /**
     * 得到当前播放进度
     *
     * @return
     */
    public abstract long getCurrentPosition();

    /**
     * 获取视频长度
     *
     * @return
     */
    public abstract long getDuration();

    /**
     * 设置音量
     *
     * @param leftVolume  左声道
     * @param rightVolume 右声道
     */
    public abstract void setVolume(float leftVolume, float rightVolume);

    /**
     * 设置播放速度，比如0.5倍数，1.5倍数
     *
     * @param speed
     */
    public abstract void setSpeed(float speed);

    public abstract void setSurface(Surface surface);
}
