package cn.jzvd;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Nathen on 16/7/30.
 * 视频自定义控件
 */
public abstract class Jzvd extends FrameLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener {

    public static final String TAG = "JZVD";
    //屏幕窗口状态
    public static final int SCREEN_NORMAL = 0;//普通窗口
    public static final int SCREEN_FULLSCREEN = 1;//全屏窗口
    public static final int SCREEN_TINY = 2;//小窗窗口
    //当前播放状态
    public static final int STATE_IDLE = -1;
    public static final int STATE_NORMAL = 0;//正常空闲状态，即视频没播，或者播完视频后的状态
    public static final int STATE_PREPARING = 1;//准备中
    public static final int STATE_PREPARING_CHANGE_URL = 2;//准备更改url播放，即切换视频
    public static final int STATE_PREPARING_PLAYING = 3;//准备播放
    public static final int STATE_PREPARED = 4;//预加载完毕
    public static final int STATE_PLAYING = 5;//播放中
    public static final int STATE_PAUSE = 6;//暂停
    public static final int STATE_AUTO_COMPLETE = 7;//播放完毕
    public static final int STATE_ERROR = 8;//播放出错
    //视频图像显示类型
    public static final int VIDEO_IMAGE_DISPLAY_TYPE_ADAPTER = 0;//DEFAULT
    public static final int VIDEO_IMAGE_DISPLAY_TYPE_FILL_PARENT = 1;//视频图像显示类型--充满父容器
    public static final int VIDEO_IMAGE_DISPLAY_TYPE_FILL_SCROP = 2;//视频图像显示类型--填充剪切,例如图宽高300,100,父容器200,200,则填充到600,200,然后多余的剪切
    public static final int VIDEO_IMAGE_DISPLAY_TYPE_ORIGINAL = 3;//视频图像显示类型---使用原图
    public static final int THRESHOLD = 80;
    public static Jzvd CURRENT_JZVD;//当前自定义视频控件
    public static LinkedList<ViewGroup> CONTAINER_LIST = new LinkedList<>();
    public static boolean TOOL_BAR_EXIST = true;//工具栏存在 ?   即进度条时间等控件
    public static int FULLSCREEN_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;//全屏窗口下方向----原:由物理感应器决定显示方向 现:只能是横向
    public static int NORMAL_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;//普通窗口下方向---竖屏
    public static boolean SAVE_PROGRESS = true;//保存视频进度，下次进来选择是否跳转到对应位置播放
    public static boolean WIFI_TIP_DIALOG_SHOWED = false;//当使用数据播放时，是否在播放前弹窗提示，默认false弹出提示
    public static int VIDEO_IMAGE_DISPLAY_TYPE = 0;//视频图像显示类型，默认0普通窗口
    public static long lastAutoFullscreenTime = 0;//最后一次自动全屏时间
    public static int ON_PLAY_PAUSE_TMP_STATE = 0;//这个考虑不放到库里，去自定义
    public static int backUpBufferState = -1;
    public static float PROGRESS_DRAG_RATE = 1f;//进度条滑动阻尼系数 越大播放进度条滑动越慢
    public static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {//是否新建个class，代码更规矩，并且变量的位置也很尴尬
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    Log.d(TAG, "AUDIOFOCUS_LOSS [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    try {
                        Jzvd player = CURRENT_JZVD;
                        if (player != null && player.state == Jzvd.STATE_PLAYING) {
                            player.startButton.performClick();
                        }
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };
    public int state = -1;//当前播放状态
    public int screen = -1;//当前窗口状态
    public JZDataSource jzDataSource;//数据来源
    public int widthRatio = 0;//视频宽比  1 16
    public int heightRatio = 0;//视频高比 1  9
    public Class mediaInterfaceClass;
    public JZMediaInterface mediaInterface;//播放器接口类，实现不同播放引擎
    public int positionInList = -1;//很想干掉它 当前jzvd在ListView列表中的位置
    public int videoRotation = 0;//当前jzvd旋转角度
    public int seekToManulPosition = -1;//用户手动拖动进度条后的定位位置
    public long seekToInAdvance = 0;//跳转时间进度，MediaPlayer.seekTo(seekToInAdvance)

    public ImageView startButton;//开始、暂停按钮
    public SeekBar progressBar;//底部进度条
    public ImageView fullscreenButton;//全屏按钮
    public TextView currentTimeTextView, totalTimeTextView;//当前时间，总时间
    public ViewGroup textureViewContainer;//存放textureView的容器
    public ViewGroup topContainer, bottomContainer;//头部容器，底部容器
    public JZTextureView textureView;//用于显示视频内容流，必须在硬件加速开启的窗体中使用
    public boolean preloading = false;//是否设置了预加载视频
    //liestview中，退出全屏也会导致列表getview->setUp，这个变量要屏蔽这个过程
    protected long gobakFullscreenTime = 0;//这个应该重写一下，刷新列表，新增列表的刷新，不打断播放，应该是个flag
    protected long gotoFullscreenTime = 0;
    protected Timer UPDATE_PROGRESS_TIMER;//定时器对象
    protected int mScreenWidth;//屏幕宽
    protected int mScreenHeight;//屏幕高
    protected AudioManager mAudioManager;//系统音频管理器
    protected ProgressTimerTask mProgressTimerTask;//一个可以被Timer执行的进度条时间任务
    protected boolean mTouchingProgressBar;//是否触摸视频容器，按下时置为true，抬起时置为false
    protected float mDownX;//触摸事件---按下时X轴
    protected float mDownY;//触摸事件---按下时Y轴
    protected boolean mChangeVolume;//是否是修改音量大小
    protected boolean mChangePosition;//是否是修改视频进度
    protected boolean mChangeBrightness;//是否是修改屏幕亮度大小
    protected long mGestureDownPosition;//记录按下时视频进度
    protected int mGestureDownVolume;//记录按下时音量大小
    protected float mGestureDownBrightness;//记录按下时屏幕亮度大小
    protected long mSeekTimePosition;//记录实时拖动视频进度
    protected Context jzvdContext;//使用该控件的Activity对象
    protected long mCurrentPosition;
    /**
     * 如果不在列表中可以不加block
     */
    protected ViewGroup.LayoutParams blockLayoutParams;
    protected int blockIndex;
    protected int blockWidth;
    protected int blockHeight;

    public Jzvd(Context context) {
        super(context);
        init(context);
    }

    public Jzvd(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    /**
     * 增加准备状态逻辑
     */
    public static void goOnPlayOnResume() {
        if (CURRENT_JZVD != null) {
            if (CURRENT_JZVD.state == Jzvd.STATE_PAUSE) {
                if (ON_PLAY_PAUSE_TMP_STATE == STATE_PAUSE) {
                    CURRENT_JZVD.onStatePause();
                    CURRENT_JZVD.mediaInterface.pause();
                } else {
                    CURRENT_JZVD.onStatePlaying();
                    CURRENT_JZVD.mediaInterface.start();
                }
                ON_PLAY_PAUSE_TMP_STATE = 0;
            } else if (CURRENT_JZVD.state == Jzvd.STATE_PREPARING) {
                //准备状态暂停后的
                CURRENT_JZVD.startVideo();
            }
            if (CURRENT_JZVD.screen == Jzvd.SCREEN_FULLSCREEN) {
                JZUtils.hideStatusBar(CURRENT_JZVD.jzvdContext);
                JZUtils.hideSystemUI(CURRENT_JZVD.jzvdContext);
            }
        }
    }

    /**
     * 增加准备状态逻辑
     */
    public static void goOnPlayOnPause() {
        if (CURRENT_JZVD != null) {
            if (CURRENT_JZVD.state == Jzvd.STATE_AUTO_COMPLETE ||
                    CURRENT_JZVD.state == Jzvd.STATE_NORMAL ||
                    CURRENT_JZVD.state == Jzvd.STATE_ERROR) {
                Jzvd.releaseAllVideos();
            } else if (CURRENT_JZVD.state == Jzvd.STATE_PREPARING) {
                //准备状态暂停的逻辑
                Jzvd.setCurrentJzvd(CURRENT_JZVD);
                CURRENT_JZVD.state = STATE_PREPARING;
            } else {
                ON_PLAY_PAUSE_TMP_STATE = CURRENT_JZVD.state;
                CURRENT_JZVD.onStatePause();
                CURRENT_JZVD.mediaInterface.pause();
            }
        }
    }

    public static void startFullscreenDirectly(Context context, Class _class, String url, String title) {
        startFullscreenDirectly(context, _class, new JZDataSource(url, title));
    }

    public static void startFullscreenDirectly(Context context, Class _class, JZDataSource jzDataSource) {
        JZUtils.hideStatusBar(context);
        JZUtils.setRequestedOrientation(context, FULLSCREEN_ORIENTATION);
        JZUtils.hideSystemUI(context);

        ViewGroup vp = (ViewGroup) JZUtils.scanForActivity(context).getWindow().getDecorView();
        try {
            Constructor<Jzvd> constructor = _class.getConstructor(Context.class);
            final Jzvd jzvd = constructor.newInstance(context);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            vp.addView(jzvd, lp);
            jzvd.setUp(jzDataSource, JzvdStd.SCREEN_FULLSCREEN);
            jzvd.startVideo();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void releaseAllVideos() {
        Log.d(TAG, "releaseAllVideos");
        if (CURRENT_JZVD != null) {
            CURRENT_JZVD.reset();
            CURRENT_JZVD = null;
        }
    }

    public static boolean backPress() {
        Log.i(TAG, "backPress");
        if (CONTAINER_LIST.size() != 0 && CURRENT_JZVD != null) {//判断条件，因为当前所有goBack都是回到普通窗口
            CURRENT_JZVD.gotoNormalScreen();
            return true;
        } else if (CONTAINER_LIST.size() == 0 && CURRENT_JZVD != null && CURRENT_JZVD.screen != SCREEN_NORMAL) {//退出直接进入的全屏
            CURRENT_JZVD.clearFloatScreen();
            return true;
        }
        return false;
    }

    public static void setCurrentJzvd(Jzvd jzvd) {
        if (CURRENT_JZVD != null) CURRENT_JZVD.reset();
        CURRENT_JZVD = jzvd;
    }

    public static void setTextureViewRotation(int rotation) {
        if (CURRENT_JZVD != null && CURRENT_JZVD.textureView != null) {
            CURRENT_JZVD.textureView.setRotation(rotation);
        }
    }

    public static void setVideoImageDisplayType(int type) {
        Jzvd.VIDEO_IMAGE_DISPLAY_TYPE = type;
        if (CURRENT_JZVD != null && CURRENT_JZVD.textureView != null) {
            CURRENT_JZVD.textureView.requestLayout();
        }
    }

    public abstract int getLayoutId();

    /**
     * 初始化信息、控件
     *
     * @param context 上下文
     */
    public void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        jzvdContext = context;
        startButton = findViewById(R.id.start);
        fullscreenButton = findViewById(R.id.fullscreen);
        progressBar = findViewById(R.id.bottom_seek_progress);
        currentTimeTextView = findViewById(R.id.current);
        totalTimeTextView = findViewById(R.id.total);
        bottomContainer = findViewById(R.id.layout_bottom);
        textureViewContainer = findViewById(R.id.surface_container);
        topContainer = findViewById(R.id.layout_top);

        if (startButton == null) {
            startButton = new ImageView(context);
        }
        if (fullscreenButton == null) {
            fullscreenButton = new ImageView(context);
        }
        if (progressBar == null) {
            progressBar = new SeekBar(context);
        }
        if (currentTimeTextView == null) {
            currentTimeTextView = new TextView(context);
        }
        if (totalTimeTextView == null) {
            totalTimeTextView = new TextView(context);
        }
        if (bottomContainer == null) {
            bottomContainer = new LinearLayout(context);
        }
        if (textureViewContainer == null) {
            textureViewContainer = new FrameLayout(context);
        }
        if (topContainer == null) {
            topContainer = new RelativeLayout(context);
        }

        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);
        textureViewContainer.setOnTouchListener(this);

        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;

        state = STATE_IDLE;
    }

    public void setUp(String url, String title) {
        setUp(new JZDataSource(url, title), SCREEN_NORMAL);
    }

    public void setUp(String url, String title, int screen) {
        setUp(new JZDataSource(url, title), screen);
    }

    public void setUp(JZDataSource jzDataSource, int screen) {
        setUp(jzDataSource, screen, JZMediaSystem.class);
    }

    public void setUp(String url, String title, int screen, Class mediaInterfaceClass) {
        setUp(new JZDataSource(url, title), screen, mediaInterfaceClass);
    }

    /**
     * 设置资源信息
     *
     * @param jzDataSource        数据来源
     * @param screen              窗口状态
     * @param mediaInterfaceClass 播放引擎
     */
    public void setUp(JZDataSource jzDataSource, int screen, Class mediaInterfaceClass) {


        this.jzDataSource = jzDataSource;
        this.screen = screen;
        onStateNormal();
        this.mediaInterfaceClass = mediaInterfaceClass;
    }

    /**
     * @param mediaInterfaceClass 设置播放引擎
     */
    public void setMediaInterface(Class mediaInterfaceClass) {
        reset();
        this.mediaInterfaceClass = mediaInterfaceClass;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.start) {//播放、暂停
            clickStart();
        } else if (i == R.id.fullscreen) {//全屏、退出全屏
            clickFullscreen();
        }
    }

    //全屏、退出全屏
    protected void clickFullscreen() {
        Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");
        if (state == STATE_AUTO_COMPLETE) return;
        if (screen == SCREEN_FULLSCREEN) {
            //quit fullscreen
            backPress();
        } else {
            Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
            gotoFullscreen();
        }
    }

    //播放、暂停
    protected void clickStart() {
        Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
        if (jzDataSource == null || jzDataSource.urlsMap.isEmpty() || jzDataSource.getCurrentUrl() == null) {
            Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
            return;
        }
        if (state == STATE_NORMAL) {
            if (!jzDataSource.getCurrentUrl().toString().startsWith("file") && !
                    jzDataSource.getCurrentUrl().toString().startsWith("/") &&
                    !JZUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {//这个可以放到std中
                //不是本地视频、wifi未连接、未授权流量看视频，显示请使用wifi播放弹窗
                showWifiDialog();
                return;
            }
            startVideo();
        } else if (state == STATE_PLAYING) {
            Log.d(TAG, "pauseVideo [" + this.hashCode() + "] ");
            mediaInterface.pause();
            onStatePause();
        } else if (state == STATE_PAUSE) {
            mediaInterface.start();
            onStatePlaying();
        } else if (state == STATE_AUTO_COMPLETE) {
            startVideo();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchActionDown(x, y);
                    break;
                case MotionEvent.ACTION_MOVE:
                    touchActionMove(x, y);
                    break;
                case MotionEvent.ACTION_UP:
                    touchActionUp();
                    break;
            }
        }
        return false;
    }

    protected void touchActionUp() {
        Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
        mTouchingProgressBar = false;
        dismissProgressDialog();
        dismissVolumeDialog();
        dismissBrightnessDialog();
        //修改视频进度
        if (mChangePosition) {
            mediaInterface.seekTo(mSeekTimePosition);
            long duration = getDuration();
            int progress = (int) (mSeekTimePosition * 100 / (duration == 0 ? 1 : duration));
            progressBar.setProgress(progress);
        }
        if (mChangeVolume) {
            //change volume event
        }
        startProgressTimer();
    }

    protected void touchActionMove(float x, float y) {
        Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
        float deltaX = x - mDownX;
        float deltaY = y - mDownY;
        float absDeltaX = Math.abs(deltaX);
        float absDeltaY = Math.abs(deltaY);
        if (screen == SCREEN_FULLSCREEN) {
            //拖动的是NavigationBar和状态栏
            if (mDownX > JZUtils.getScreenWidth(getContext()) || mDownY < JZUtils.getStatusBarHeight(getContext())) {
                return;
            }
            if (!mChangePosition && !mChangeVolume && !mChangeBrightness) {
                if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                    cancelProgressTimer();
                    //左右拖拽视频进度
                    if (absDeltaX >= THRESHOLD) {
                        // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                        // 否则会因为mediaplayer的状态非法导致App Crash
                        if (state != STATE_ERROR) {
                            mChangePosition = true;
                            mGestureDownPosition = getCurrentPositionWhenPlaying();
                        }
                    } else {
                        //如果y轴滑动距离超过设置的处理范围，那么进行滑动事件处理
                        //if (mDownX < mScreenWidth * 0.5f) {//左侧改变亮度
                        if (mDownX < mScreenHeight * 0.5f) {//左侧改变亮度
                            mChangeBrightness = true;
                            WindowManager.LayoutParams lp = JZUtils.getWindow(getContext()).getAttributes();
                            if (lp.screenBrightness < 0) {
                                try {
                                    mGestureDownBrightness = Settings.System.getInt(getContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                                    Log.i(TAG, "current system brightness: " + mGestureDownBrightness);
                                } catch (Settings.SettingNotFoundException e) {
                                    e.printStackTrace();
                                }
                            } else {
                                mGestureDownBrightness = lp.screenBrightness * 255;
                                Log.i(TAG, "current activity brightness: " + mGestureDownBrightness);
                            }
                        } else {//右侧改变声音
                            mChangeVolume = true;
                            mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        }
                    }
                }
            }
        }
        //如果拖拽视频进度
        if (mChangePosition) {
            long totalTimeDuration = getDuration();
            if (PROGRESS_DRAG_RATE <= 0) {
                Log.d(TAG, "error PROGRESS_DRAG_RATE value");
                PROGRESS_DRAG_RATE = 1f;
            }
            //计算拖拽的目标进度(当拖拽整个屏幕宽度的长度时，目标进度=视频长度)
            mSeekTimePosition = (int) (mGestureDownPosition + deltaX * totalTimeDuration / (mScreenWidth * PROGRESS_DRAG_RATE));
            if (mSeekTimePosition > totalTimeDuration)
                mSeekTimePosition = totalTimeDuration;
            //目标进度时间字符串
            String seekTime = JZUtils.stringForTime(mSeekTimePosition);
            //视频时长时间字符串
            String totalTime = JZUtils.stringForTime(totalTimeDuration);
            //显示拖拽弹窗
            showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
        }
        //如果修改音乐音量大小
        if (mChangeVolume) {
            //向下滑动时 deltaY>0，但是向下滑动是降低音量，所以需要-deltaY
            deltaY = -deltaY;
            //获取音乐音量最大值
            int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            //计算音量变化值
            int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
            //设置音乐音量大小
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
            //dialog中显示百分比
            int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);
            //dialog中显示百分比
            showVolumeDialog(-deltaY, volumePercent);
        }
        //如果修改屏幕亮度
        if (mChangeBrightness) {
            //向下滑动时 deltaY>0，但是向下滑动是降低亮度，所以需要-deltaY
            deltaY = -deltaY;
            //计算亮度变化值
            int deltaV = (int) (255 * deltaY * 3 / mScreenHeight);
            WindowManager.LayoutParams params = JZUtils.getWindow(getContext()).getAttributes();
            //设置屏幕亮度
            if (((mGestureDownBrightness + deltaV) / 255) >= 1) {//这和声音有区别，必须自己过滤一下负值
                params.screenBrightness = 1;
            } else if (((mGestureDownBrightness + deltaV) / 255) <= 0) {
                params.screenBrightness = 0.01f;
            } else {
                params.screenBrightness = (mGestureDownBrightness + deltaV) / 255;
            }
            JZUtils.getWindow(getContext()).setAttributes(params);
            //dialog中显示百分比
            int brightnessPercent = (int) (mGestureDownBrightness * 100 / 255 + deltaY * 3 * 100 / mScreenHeight);
            showBrightnessDialog(brightnessPercent);
//                        mDownY = y;
        }
    }

    protected void touchActionDown(float x, float y) {
        Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
        mTouchingProgressBar = true;

        mDownX = x;
        mDownY = y;
        mChangeVolume = false;
        mChangePosition = false;
        mChangeBrightness = false;
    }

    /**
     * 设置当前播放状态为空闲状态
     */
    public void onStateNormal() {
        Log.i(TAG, "onStateNormal " + " [" + this.hashCode() + "] ");
        state = STATE_NORMAL;
        cancelProgressTimer();
        if (mediaInterface != null) mediaInterface.release();
    }

    /**
     * 设置当前播放状态为准备播放中状态
     */
    public void onStatePreparing() {
        Log.i(TAG, "onStatePreparing " + " [" + this.hashCode() + "] ");
        state = STATE_PREPARING;
        resetProgressAndTime();
    }

    public void onStatePreparingPlaying() {
        Log.i(TAG, "onStatePreparingPlaying " + " [" + this.hashCode() + "] ");
        state = STATE_PREPARING_PLAYING;
    }

    public void onStatePreparingChangeUrl() {
        Log.i(TAG, "onStatePreparingChangeUrl " + " [" + this.hashCode() + "] ");
        state = STATE_PREPARING_CHANGE_URL;

        releaseAllVideos();
        startVideo();

//        mediaInterface.prepare();
    }

    /**
     * 更改视频据来源
     *
     * @param jzDataSource    要切换的数据来源
     * @param seekToInAdvance 切换的同时要同步视频进度
     */
    public void changeUrl(JZDataSource jzDataSource, long seekToInAdvance) {
        this.jzDataSource = jzDataSource;
        this.seekToInAdvance = seekToInAdvance;
        onStatePreparingChangeUrl();
    }

    //真正的prepared
    public void onPrepared() {
        Log.i(TAG, "onPrepared " + " [" + this.hashCode() + "] ");
        state = STATE_PREPARED;
        if (!preloading) {
            mediaInterface.start();//这里原来是非县城
            preloading = false;
        }
        if (jzDataSource.getCurrentUrl().toString().toLowerCase().contains("mp3") ||
                jzDataSource.getCurrentUrl().toString().toLowerCase().contains("wma") ||
                jzDataSource.getCurrentUrl().toString().toLowerCase().contains("aac") ||
                jzDataSource.getCurrentUrl().toString().toLowerCase().contains("m4a") ||
                jzDataSource.getCurrentUrl().toString().toLowerCase().contains("wav")) {
            onStatePlaying();
        }
    }

    public void startPreloading() {
        preloading = true;
        startVideo();
    }

    /**
     * 如果STATE_PREPARED就播放，如果没准备完成就走正常的播放函数startVideo();
     */
    public void startVideoAfterPreloading() {
        if (state == STATE_PREPARED) {
            mediaInterface.start();
        } else {
            preloading = false;
            startVideo();
        }
    }

    /**
     * 设置当前播放状态为播放中
     */
    public void onStatePlaying() {
        Log.i(TAG, "onStatePlaying " + " [" + this.hashCode() + "] ");
        if (state == STATE_PREPARED) {//如果是准备完成视频后第一次播放，先判断是否需要跳转进度。
            if (seekToInAdvance != 0) {
                mediaInterface.seekTo(seekToInAdvance);
                seekToInAdvance = 0;
            } else {
                long position = JZUtils.getSavedProgress(getContext(), jzDataSource.getCurrentUrl());
                if (position != 0) {
                    mediaInterface.seekTo(position);//这里为什么区分开呢，第一次的播放和resume播放是不一样的。 这里怎么区分是一个问题。然后
                }
            }
        }
        state = STATE_PLAYING;
        startProgressTimer();
    }

    /**
     * 设置当前播放状态为暂停
     */
    public void onStatePause() {
        Log.i(TAG, "onStatePause " + " [" + this.hashCode() + "] ");
        state = STATE_PAUSE;
        startProgressTimer();
    }

    /**
     * 设置当前播放状态为播放错误
     */
    public void onStateError() {
        Log.i(TAG, "onStateError " + " [" + this.hashCode() + "] ");
        state = STATE_ERROR;
        cancelProgressTimer();
    }

    /**
     * 设置当前播放状态为播放完毕
     */
    public void onStateAutoComplete() {
        Log.i(TAG, "onStateAutoComplete " + " [" + this.hashCode() + "] ");
        state = STATE_AUTO_COMPLETE;
        cancelProgressTimer();
        progressBar.setProgress(100);
        currentTimeTextView.setText(totalTimeTextView.getText());
    }

    /**
     * 提示信息或警告
     *
     * @param what  标识
     * @param extra 额外
     */
    public void onInfo(int what, int extra) {
        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
            Log.d(TAG, "MEDIA_INFO_VIDEO_RENDERING_START");
            if (state == Jzvd.STATE_PREPARED
                    || state == Jzvd.STATE_PREPARING_CHANGE_URL
                    || state == Jzvd.STATE_PREPARING_PLAYING) {
                onStatePlaying();//开始渲染图像，真正进入playing状态
            }
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
            backUpBufferState = state;
            setState(STATE_PREPARING_PLAYING);
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
            if (backUpBufferState != -1) {
                setState(backUpBufferState);
                backUpBufferState = -1;
            }
        }
    }

    /**
     * 视频异步操作期间发生错误
     *
     * @param what  标识
     * @param extra 额外
     */
    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && extra != -38 && what != -38 && extra != 38 && extra != -19) {
            onStateError();
            mediaInterface.release();
        }
    }

    /**
     * 视频播放完毕
     */
    public void onCompletion() {
        Runtime.getRuntime().gc();
        Log.i(TAG, "onAutoCompletion " + " [" + this.hashCode() + "] ");
        //取消进度计时器，隐藏弹窗
        cancelProgressTimer();
        dismissBrightnessDialog();
        dismissProgressDialog();
        dismissVolumeDialog();
        //修改播放状态为播放完毕
        onStateAutoComplete();
        //释放视频资源
        mediaInterface.release();
        JZUtils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //将当前播放的视频进度设置为0
        JZUtils.saveProgress(getContext(), jzDataSource.getCurrentUrl(), 0);

        if (screen == SCREEN_FULLSCREEN) {
            if (CONTAINER_LIST.size() == 0) {
                clearFloatScreen();//直接进入全屏
            } else {
                gotoNormalCompletion();
            }
        }
    }

    public void gotoNormalCompletion() {
        gobakFullscreenTime = System.currentTimeMillis();//退出全屏
        ViewGroup vg = (ViewGroup) (JZUtils.scanForActivity(jzvdContext)).getWindow().getDecorView();
        vg.removeView(this);
        textureViewContainer.removeView(textureView);
        CONTAINER_LIST.getLast().removeViewAt(blockIndex);//remove block
        CONTAINER_LIST.getLast().addView(this, blockIndex, blockLayoutParams);
        CONTAINER_LIST.pop();

        setScreenNormal();
        JZUtils.showStatusBar(jzvdContext);
        JZUtils.setRequestedOrientation(jzvdContext, NORMAL_ORIENTATION);
        JZUtils.showSystemUI(jzvdContext);
    }

    /**
     * 多数表现为中断当前播放
     */
    public void reset() {
        Log.i(TAG, "reset " + " [" + this.hashCode() + "] ");
        //如果是播放中或者暂停状态，保存当前视频播放进度
        if (state == STATE_PLAYING || state == STATE_PAUSE) {
            long position = getCurrentPositionWhenPlaying();
            JZUtils.saveProgress(getContext(), jzDataSource.getCurrentUrl(), position);
        }
        //取消进度计时器，隐藏弹窗
        cancelProgressTimer();
        dismissBrightnessDialog();
        dismissProgressDialog();
        dismissVolumeDialog();
        //修改播放状态为普通
        onStateNormal();
        //删除播放视频用的textureView
        textureViewContainer.removeAllViews();
        //系统的音频焦点状态变换监听
        AudioManager mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        JZUtils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //释放视频资源
        if (mediaInterface != null) mediaInterface.release();
    }

    /**
     * 里面的的onState...()其实就是setState...()，因为要可以被复写，所以参考Activity的onCreate(),onState..()的方式看着舒服一些，老铁们有何高见。
     *
     * @param state stateId
     */
    public void setState(int state) {
        switch (state) {
            case STATE_NORMAL:
                onStateNormal();
                break;
            case STATE_PREPARING:
                onStatePreparing();
                break;
            case STATE_PREPARING_PLAYING:
                onStatePreparingPlaying();
                break;
            case STATE_PREPARING_CHANGE_URL:
                onStatePreparingChangeUrl();
                break;
            case STATE_PLAYING:
                onStatePlaying();
                break;
            case STATE_PAUSE:
                onStatePause();
                break;
            case STATE_ERROR:
                onStateError();
                break;
            case STATE_AUTO_COMPLETE:
                onStateAutoComplete();
                break;
        }
    }

    //设置窗口状态
    public void setScreen(int screen) {//特殊的个别的进入全屏的按钮在这里设置  只有setup的时候能用上
        switch (screen) {
            case SCREEN_NORMAL:
                setScreenNormal();
                break;
            case SCREEN_FULLSCREEN:
                setScreenFullscreen();
                break;
            case SCREEN_TINY:
                setScreenTiny();
                break;
        }
    }

    /**
     * 开始播放视频,{@link #addTextureView()}方法里面设置了{@link android.view.TextureView#setSurfaceTextureListener(TextureView.SurfaceTextureListener)}监听，
     * 在{@link android.view.TextureView.SurfaceTextureListener#onSurfaceTextureAvailable(SurfaceTexture, int, int)}时调用{@link JZMediaInterface#prepare()}方法准备播放
     */
    public void startVideo() {
        Log.d(TAG, "startVideo [" + this.hashCode() + "] ");
        setCurrentJzvd(this);
        try { //创建播放引擎类
            Constructor<JZMediaInterface> constructor = mediaInterfaceClass.getConstructor(Jzvd.class);
            this.mediaInterface = constructor.newInstance(this);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        addTextureView();

        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        JZUtils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        onStatePreparing();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (screen == SCREEN_FULLSCREEN || screen == SCREEN_TINY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        if (widthRatio != 0 && heightRatio != 0) {
            int specWidth = MeasureSpec.getSize(widthMeasureSpec);
            int specHeight = (int) ((specWidth * (float) heightRatio) / widthRatio);
            setMeasuredDimension(specWidth, specHeight);

            int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(specWidth, MeasureSpec.EXACTLY);
            int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(specHeight, MeasureSpec.EXACTLY);
            getChildAt(0).measure(childWidthMeasureSpec, childHeightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

    }

    /**
     * 添加TextureView用于视频播放渲染
     */
    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        //移除之前的布局
        if (textureView != null) textureViewContainer.removeView(textureView);
        textureView = new JZTextureView(getContext().getApplicationContext());
        textureView.setSurfaceTextureListener(mediaInterface);

        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(textureView, layoutParams);
    }

    /**
     * 退出视频播放，释放资源
     */
    public void clearFloatScreen() {
        JZUtils.showStatusBar(getContext());
        JZUtils.setRequestedOrientation(getContext(), NORMAL_ORIENTATION);
        JZUtils.showSystemUI(getContext());

        ViewGroup vg = (ViewGroup) (JZUtils.scanForActivity(getContext())).getWindow().getDecorView();
        vg.removeView(this);
        if (mediaInterface != null) mediaInterface.release();
        CURRENT_JZVD = null;
    }

    /**
     * 视频宽高变化，即横竖、上下屏幕切换
     *
     * @param width  宽
     * @param height 高
     */
    public void onVideoSizeChanged(int width, int height) {
        Log.i(TAG, "onVideoSizeChanged " + " [" + this.hashCode() + "] ");
        if (textureView != null) {
            if (videoRotation != 0) {
                textureView.setRotation(videoRotation);
            }
            textureView.setVideoSize(width, height);
        }
    }

    /**
     * 计时器开始运行，手势滑动抬起；播放状态切换为播放中、暂停；滑动seekBar结束后；调用
     */
    public void startProgressTimer() {
        Log.i(TAG, "startProgressTimer: " + " [" + this.hashCode() + "] ");
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    /**
     * 取消计时器任务，手势滑动改变视频进度；滑动seekBar改变视频进度；播放状态切换为空闲、错误、完成状态；取消播放时；调用
     */
    public void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }
    }

    /**
     * 根据视频进度以及总时长等，设置seekBar的进度，设置时间文本描述
     *
     * @param progress seek的进度，比如0-100
     * @param position 视频进度，指的是具体的多少毫秒
     * @param duration 视频总长度，指的是具体的多少毫秒
     */
    public void onProgress(int progress, long position, long duration) {
//        Log.d(TAG, "onProgress: progress=" + progress + " position=" + position + " duration=" + duration);
        mCurrentPosition = position;
        if (!mTouchingProgressBar) {
            if (seekToManulPosition != -1) {
                if (seekToManulPosition > progress) {
                    return;
                } else {
                    seekToManulPosition = -1;//这个关键帧有没有必要做
                }
            } else {
                if (progress != 0) progressBar.setProgress(progress);
            }
        }
        if (position != 0) currentTimeTextView.setText(JZUtils.stringForTime(position));
        totalTimeTextView.setText(JZUtils.stringForTime(duration));
    }

    /**
     * 用于网络url资源的视频播放时，设置缓冲区的进度条
     *
     * @param bufferProgress 缓冲区的进度条
     */
    public void setBufferProgress(int bufferProgress) {
        if (bufferProgress != 0) progressBar.setSecondaryProgress(bufferProgress);
    }

    /**
     * 重置进度条、时间描述
     */
    public void resetProgressAndTime() {
        mCurrentPosition = 0;
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(JZUtils.stringForTime(0));
        totalTimeTextView.setText(JZUtils.stringForTime(0));
    }

    /**
     * @return 播放中、暂停状态下获取当前视频进度
     */
    public long getCurrentPositionWhenPlaying() {
        long position = 0;
        if (state == STATE_PLAYING || state == STATE_PAUSE || state == STATE_PREPARING_PLAYING) {
            try {
                position = mediaInterface.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    /**
     * @return 获取当前视频总长度
     */
    public long getDuration() {
        long duration = 0;
        try {
            duration = mediaInterface.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {//开始拖动seekBar
        Log.i(TAG, "bottomProgress onStartTrackingTouch [" + this.hashCode() + "] ");
        cancelProgressTimer();
        ViewParent vpdown = getParent();
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true);
            vpdown = vpdown.getParent();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {//停止拖动seekBar
        Log.i(TAG, "bottomProgress onStopTrackingTouch [" + this.hashCode() + "] ");
        startProgressTimer();
        ViewParent vpup = getParent();
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false);
            vpup = vpup.getParent();
        }
        if (state != STATE_PLAYING &&
                state != STATE_PAUSE) return;
        long time = seekBar.getProgress() * getDuration() / 100;
        seekToManulPosition = seekBar.getProgress();
        mediaInterface.seekTo(time);
        Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {//seekBar拖动中
        if (fromUser) {
            //设置这个progres对应的时间，给textview
            long duration = getDuration();
            currentTimeTextView.setText(JZUtils.stringForTime(progress * duration / 100));
        }
    }

    /**
     * 克隆当前Jzvd类并添加到容器中
     *
     * @param vg 容器
     */
    public void cloneAJzvd(ViewGroup vg) {
        try {
            Constructor<Jzvd> constructor = (Constructor<Jzvd>) Jzvd.this.getClass().getConstructor(Context.class);
            Jzvd jzvd = constructor.newInstance(getContext());
            jzvd.setId(getId());
            jzvd.setMinimumWidth(blockWidth);
            jzvd.setMinimumHeight(blockHeight);
            vg.addView(jzvd, blockIndex, blockLayoutParams);
            jzvd.setUp(jzDataSource.cloneMe(), SCREEN_NORMAL, mediaInterfaceClass);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * 切换到全屏窗口状态
     * 如果全屏或者返回全屏的视图有问题，复写这两个函数gotoScreenNormal(),根据自己布局的情况重新布局。
     */
    public void gotoFullscreen() {
        gotoFullscreenTime = System.currentTimeMillis();
        ViewGroup vg = (ViewGroup) getParent();
        jzvdContext = vg.getContext();
        blockLayoutParams = getLayoutParams();
        blockIndex = vg.indexOfChild(this);
        blockWidth = getWidth();
        blockHeight = getHeight();

        vg.removeView(this);
        cloneAJzvd(vg);
        CONTAINER_LIST.add(vg);
        vg = (ViewGroup) (JZUtils.scanForActivity(jzvdContext)).getWindow().getDecorView();

        ViewGroup.LayoutParams fullLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        vg.addView(this, fullLayout);

        setScreenFullscreen();
        JZUtils.hideStatusBar(jzvdContext);
        JZUtils.setRequestedOrientation(jzvdContext, FULLSCREEN_ORIENTATION);
        JZUtils.hideSystemUI(jzvdContext);//华为手机和有虚拟键的手机全屏时可隐藏虚拟键 issue:1326

    }

    /**
     * 切换到普通窗口状态
     */
    public void gotoNormalScreen() {//goback本质上是goto
        gobakFullscreenTime = System.currentTimeMillis();//退出全屏
        ViewGroup vg = (ViewGroup) (JZUtils.scanForActivity(jzvdContext)).getWindow().getDecorView();
        vg.removeView(this);
//        CONTAINER_LIST.getLast().removeAllViews();
        CONTAINER_LIST.getLast().removeViewAt(blockIndex);//remove block
        CONTAINER_LIST.getLast().addView(this, blockIndex, blockLayoutParams);
        CONTAINER_LIST.pop();

        setScreenNormal();//这块可以放到jzvd中
        JZUtils.showStatusBar(jzvdContext);
        JZUtils.setRequestedOrientation(jzvdContext, NORMAL_ORIENTATION);
        JZUtils.showSystemUI(jzvdContext);
    }

    //设置为普通窗口模式
    public void setScreenNormal() {//TODO 这块不对呀，还需要改进，设置flag之后要设置ui，不设置ui这么写没意义呀
        screen = SCREEN_NORMAL;
    }

    //设置为全屏窗口模式
    public void setScreenFullscreen() {
        screen = SCREEN_FULLSCREEN;
    }

    //设置为窗口窗口模式
    public void setScreenTiny() {
        screen = SCREEN_TINY;
    }

    //    //重力感应的时候调用的函数，、、这里有重力感应的参数，暂时不能删除
    public void autoFullscreen(float x) {//TODO写道demo中
        if (CURRENT_JZVD != null
                && (state == STATE_PLAYING || state == STATE_PAUSE)
                && screen != SCREEN_FULLSCREEN
                && screen != SCREEN_TINY) {
            if (x > 0) {
                JZUtils.setRequestedOrientation(getContext(), ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                JZUtils.setRequestedOrientation(getContext(), ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
            }
            gotoFullscreen();
        }
    }

    /**
     * 自动退出全屏窗口模式
     */
    public void autoQuitFullscreen() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
//                && CURRENT_JZVD != null
                && state == STATE_PLAYING
                && screen == SCREEN_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            backPress();
        }
    }

    /**
     * seekTo()定位播放操作完成回调
     */
    public void onSeekComplete() {

    }

    /**
     * 使用流量播放视频前显示弹窗提示
     */
    public void showWifiDialog() {
    }

    /**
     * 手势滑动改变视频进度时回调弹窗
     *
     * @param deltaX            X轴偏移量
     * @param seekTime          当前视频进度对应时间字符串描述
     * @param seekTimePosition  当前视频进度对应时间 毫秒为单位
     * @param totalTime         当前视频总长度对应时间字符串描述
     * @param totalTimeDuration 当前视频总长度对应时间 毫秒为单位
     */
    public void showProgressDialog(float deltaX,
                                   String seekTime, long seekTimePosition,
                                   String totalTime, long totalTimeDuration) {
    }

    /**
     * 对应上面的{@link #showProgressDialog(float, String, long, String, long)},隐藏视频进度弹窗
     */
    public void dismissProgressDialog() {

    }

    /**
     * 显示修改音量弹窗
     *
     * @param deltaY        Y轴偏移量
     * @param volumePercent 音量百分比
     */
    public void showVolumeDialog(float deltaY, int volumePercent) {

    }

    /**
     * 隐藏修改音量弹窗
     */
    public void dismissVolumeDialog() {

    }

    /**
     * 显示修改屏幕亮度弹窗
     *
     * @param brightnessPercent 屏幕亮度百分比
     */
    public void showBrightnessDialog(int brightnessPercent) {

    }

    /**
     * 隐藏修改屏幕亮度弹窗
     */
    public void dismissBrightnessDialog() {

    }

    public Context getApplicationContext() {//这个函数必要吗
        Context context = getContext();
        if (context != null) {
            Context applicationContext = context.getApplicationContext();
            if (applicationContext != null) {
                return applicationContext;
            }
        }
        return context;
    }

    public static class JZAutoFullscreenListener implements SensorEventListener {//传感器事件通知监听
        @Override
        public void onSensorChanged(SensorEvent event) {//可以得到传感器实时测量出来的变化值
            final float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            float z = event.values[SensorManager.DATA_Z];
            //过滤掉用力过猛会有一个反向的大数值
            if (x < -12 || x > 12) {
                if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000) {
                    if (Jzvd.CURRENT_JZVD != null) Jzvd.CURRENT_JZVD.autoFullscreen(x);
                    lastAutoFullscreenTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    /**
     * 一个可以被Timer执行的进度条时间任务
     */
    public class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (state == STATE_PLAYING || state == STATE_PAUSE || state == STATE_PREPARING_PLAYING) {
//                Log.v(TAG, "onProgressUpdate " + "[" + this.hashCode() + "] ");
                post(() -> {
                    long position = getCurrentPositionWhenPlaying();
                    long duration = getDuration();
                    int progress = (int) (position * 100 / (duration == 0 ? 1 : duration));
                    onProgress(progress, position, duration);
                });
            }
        }
    }

}
