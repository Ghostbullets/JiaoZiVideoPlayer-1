package cn.jzvd;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Nathen
 * On 2016/04/18 16:15
 */
public class JzvdStd extends Jzvd {

    public static long LAST_GET_BATTERYLEVEL_TIME = 0;
    public static int LAST_GET_BATTERYLEVEL_PERCENT = 70;
    protected static Timer DISMISS_CONTROL_VIEW_TIMER;

    public ImageView backButton;//返回
    public ProgressBar bottomProgressBar, loadingProgressBar;//底部弹窗，加载中进弹窗
    public TextView titleTextView;//视频标题
    public ImageView thumbImageView;//缩略图
    public ImageView tinyBackImageView;//小窗口的返回键
    public LinearLayout batteryTimeLayout;//电池时间布局
    public ImageView batteryLevel;//电池电量图标，例如满电、快要没点
    public TextView videoCurrentTime;//视频当前时间
    public TextView replayTextView;//重播
    public TextView clarity;//切换清晰度
    public PopupWindow clarityPopWindow;//切换清晰度弹窗
    public TextView mRetryBtn;//重试按钮
    public LinearLayout mRetryLayout;//重试布局
    protected DismissControlViewTimerTask mDismissControlViewTimerTask;//隐藏控制布局任务任务
    protected Dialog mProgressDialog;//拖拽进度条时显示的弹窗
    protected ProgressBar mDialogProgressBar;//进度弹窗控件
    protected TextView mDialogSeekTime;//显示当前进度文本
    protected TextView mDialogTotalTime;//显示总时间进度文本
    protected ImageView mDialogIcon;//进度图标
    protected Dialog mVolumeDialog;//修改音量大小时显示的弹窗
    protected ProgressBar mDialogVolumeProgressBar;//音量改变弹窗控件
    protected TextView mDialogVolumeTextView;//音量改变文本
    protected ImageView mDialogVolumeImageView;//音量改变图标
    protected Dialog mBrightnessDialog;//修改屏幕亮度时显示的弹窗
    protected ProgressBar mDialogBrightnessProgressBar;//屏幕亮度改变弹窗控件
    protected TextView mDialogBrightnessTextView;//屏幕亮度改变文本


    public JzvdStd(Context context) {
        super(context);
    }

    public JzvdStd(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void init(Context context) {
        super.init(context);
        batteryTimeLayout = findViewById(R.id.battery_time_layout);
        bottomProgressBar = findViewById(R.id.bottom_progress);
        titleTextView = findViewById(R.id.title);
        backButton = findViewById(R.id.back);
        thumbImageView = findViewById(R.id.thumb);
        loadingProgressBar = findViewById(R.id.loading);
        tinyBackImageView = findViewById(R.id.back_tiny);
        batteryLevel = findViewById(R.id.battery_level);
        videoCurrentTime = findViewById(R.id.video_current_time);
        replayTextView = findViewById(R.id.replay_text);
        clarity = findViewById(R.id.clarity);
        mRetryBtn = findViewById(R.id.retry_btn);
        mRetryLayout = findViewById(R.id.retry_layout);

        thumbImageView.setOnClickListener(this);
        backButton.setOnClickListener(this);
        tinyBackImageView.setOnClickListener(this);
        clarity.setOnClickListener(this);
        mRetryBtn.setOnClickListener(this);
    }

    public void setUp(JZDataSource jzDataSource, int screen, Class mediaInterfaceClass) {
        super.setUp(jzDataSource, screen, mediaInterfaceClass);
        titleTextView.setText(jzDataSource.title);
        setScreen(screen);
    }

    /**
     * 更改启动按钮大小，在全屏、普通状态切换时
     * @param size
     */
    public void changeStartButtonSize(int size) {
        ViewGroup.LayoutParams lp = startButton.getLayoutParams();
        lp.height = size;
        lp.width = size;
        lp = loadingProgressBar.getLayoutParams();
        lp.height = size;
        lp.width = size;
    }

    @Override
    public int getLayoutId() {
        return R.layout.jz_layout_std;
    }

    @Override
    public void onStateNormal() {
        super.onStateNormal();
        changeUiToNormal();
    }

    @Override
    public void onStatePreparing() {
        super.onStatePreparing();
        changeUiToPreparing();
    }

    @Override
    public void onStatePlaying() {
        super.onStatePlaying();
        changeUiToPlayingClear();
    }

    @Override
    public void onStatePause() {
        super.onStatePause();
        changeUiToPauseShow();
        cancelDismissControlViewTimer();
    }

    @Override
    public void onStateError() {
        super.onStateError();
        changeUiToError();
    }

    @Override
    public void onStateAutoComplete() {
        super.onStateAutoComplete();
        changeUiToComplete();
        cancelDismissControlViewTimer();
        bottomProgressBar.setProgress(100);
    }

    @Override
    public void changeUrl(int urlMapIndex, long seekToInAdvance) {
        super.changeUrl(urlMapIndex, seekToInAdvance);
        startButton.setVisibility(INVISIBLE);
        replayTextView.setVisibility(View.GONE);
        mRetryLayout.setVisibility(View.GONE);
    }

    @Override
    public void changeUrl(JZDataSource jzDataSource, long seekToInAdvance) {
        super.changeUrl(jzDataSource, seekToInAdvance);
        titleTextView.setText(jzDataSource.title);
        startButton.setVisibility(INVISIBLE);
        replayTextView.setVisibility(View.GONE);
        mRetryLayout.setVisibility(View.GONE);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    if (mChangePosition) {
                        long duration = getDuration();
                        int progress = (int) (mSeekTimePosition * 100 / (duration == 0 ? 1 : duration));
                        bottomProgressBar.setProgress(progress);
                    }
                    if (!mChangePosition && !mChangeVolume) {
                        onClickUiToggle();
                    }
                    break;
            }
        } else if (id == R.id.bottom_seek_progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN://按住底部进度条时，取消隐藏控制控件任务
                    cancelDismissControlViewTimer();
                    break;
                case MotionEvent.ACTION_UP://松开底部进度条时，执行隐藏控制控件任务
                    startDismissControlViewTimer();
                    break;
            }
        }
        return super.onTouch(v, event);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        int i = v.getId();
        if (i == R.id.thumb) {//点击缩略图
            if (jzDataSource == null || jzDataSource.urlsMap.isEmpty() || jzDataSource.getCurrentUrl() == null) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (state == STATE_NORMAL) {//当前播放状态为空闲状态
                if (!jzDataSource.getCurrentUrl().toString().startsWith("file") &&
                        !jzDataSource.getCurrentUrl().toString().startsWith("/") &&
                        !JZUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    //不是本地视频、wifi未连接、未授权流量看视频，显示请使用wifi播放弹窗
                    showWifiDialog();
                    return;
                }
                startVideo();
            } else if (state == STATE_AUTO_COMPLETE) {//当前播放状态为播放完毕状态
                onClickUiToggle();
            }
        } else if (i == R.id.surface_container) {//点击视频，2.5秒后隐藏图标
            startDismissControlViewTimer();
        } else if (i == R.id.back) {//返回
            backPress();
        } else if (i == R.id.back_tiny) {//关闭小窗播放
            clearFloatScreen();
        } else if (i == R.id.clarity) {//切换清晰度
            LayoutInflater inflater = (LayoutInflater) getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.jz_layout_clarity, null);

            OnClickListener mQualityListener = v1 -> {
                int index = (int) v1.getTag();
                changeUrl(index, getCurrentPositionWhenPlaying());
                clarity.setText(jzDataSource.getCurrentKey().toString());//设置切换后的清晰度提示文本
                for (int j = 0; j < layout.getChildCount(); j++) {//设置点击之后的颜色
                    if (j == jzDataSource.currentUrlIndex) {
                        ((TextView) layout.getChildAt(j)).setTextColor(Color.parseColor("#fff85959"));
                    } else {
                        ((TextView) layout.getChildAt(j)).setTextColor(Color.parseColor("#ffffff"));
                    }
                }
                if (clarityPopWindow != null) {
                    clarityPopWindow.dismiss();
                }
            };

            for (int j = 0; j < jzDataSource.urlsMap.size(); j++) {
                String key = jzDataSource.getKeyFromDataSource(j);
                TextView clarityItem = (TextView) View.inflate(getContext(), R.layout.jz_layout_clarity_item, null);
                clarityItem.setText(key);
                clarityItem.setTag(j);
                layout.addView(clarityItem, j);
                clarityItem.setOnClickListener(mQualityListener);
                if (j == jzDataSource.currentUrlIndex) {
                    clarityItem.setTextColor(Color.parseColor("#fff85959"));
                }
            }

            clarityPopWindow = new PopupWindow(layout, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
            clarityPopWindow.setContentView(layout);
            clarityPopWindow.showAsDropDown(clarity);
            layout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int offsetX = clarity.getMeasuredWidth() / 3;
            int offsetY = clarity.getMeasuredHeight() / 3;
            clarityPopWindow.update(clarity, -offsetX, -offsetY, Math.round(layout.getMeasuredWidth() * 2), layout.getMeasuredHeight());
        } else if (i == R.id.retry_btn) {//重新尝试播放
            if (jzDataSource.urlsMap.isEmpty() || jzDataSource.getCurrentUrl() == null) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!jzDataSource.getCurrentUrl().toString().startsWith("file") && !
                    jzDataSource.getCurrentUrl().toString().startsWith("/") &&
                    !JZUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                //不是本地视频、wifi未连接、未授权流量看视频，显示请使用wifi播放弹窗
                showWifiDialog();
                return;
            }
            addTextureView();
            onStatePreparing();
        }
    }

    @Override
    public void setScreenNormal() {
        super.setScreenNormal();
        fullscreenButton.setImageResource(R.drawable.jz_enlarge);
        backButton.setVisibility(View.GONE);
        tinyBackImageView.setVisibility(View.INVISIBLE);
        changeStartButtonSize((int) getResources().getDimension(R.dimen.jz_start_button_w_h_normal));
        batteryTimeLayout.setVisibility(View.GONE);
        clarity.setVisibility(View.GONE);
    }

    @Override
    public void setScreenFullscreen() {
        super.setScreenFullscreen();
        //进入全屏之后要保证原来的播放状态和ui状态不变，改变个别的ui
        fullscreenButton.setImageResource(R.drawable.jz_shrink);
        backButton.setVisibility(View.VISIBLE);
        tinyBackImageView.setVisibility(View.INVISIBLE);
        batteryTimeLayout.setVisibility(View.VISIBLE);
        if (jzDataSource.urlsMap.size() == 1) {
            clarity.setVisibility(GONE);
        } else {
            clarity.setText(jzDataSource.getCurrentKey().toString());
            clarity.setVisibility(View.VISIBLE);
        }
        changeStartButtonSize((int) getResources().getDimension(R.dimen.jz_start_button_w_h_fullscreen));
        setSystemTimeAndBattery();
    }

    @Override
    public void setScreenTiny() {
        super.setScreenTiny();
        tinyBackImageView.setVisibility(View.VISIBLE);
        setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE);
        batteryTimeLayout.setVisibility(View.GONE);
        clarity.setVisibility(View.GONE);
    }

    @Override
    public void showWifiDialog() {
        super.showWifiDialog();
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(getResources().getString(R.string.tips_not_wifi));
        builder.setPositiveButton(getResources().getString(R.string.tips_not_wifi_confirm), (dialog, which) -> {
            dialog.dismiss();
            startVideo();
            WIFI_TIP_DIALOG_SHOWED = true;
        });
        builder.setNegativeButton(getResources().getString(R.string.tips_not_wifi_cancel), (dialog, which) -> {
            dialog.dismiss();
            clearFloatScreen();
        });
        builder.setOnCancelListener(DialogInterface::dismiss);
        builder.create().show();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        super.onStartTrackingTouch(seekBar);
        cancelDismissControlViewTimer();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        super.onStopTrackingTouch(seekBar);
        startDismissControlViewTimer();
    }

    /**
     * 界面切换
     */
    public void onClickUiToggle() {//这是事件
        if (bottomContainer.getVisibility() != View.VISIBLE) {
            setSystemTimeAndBattery();
            clarity.setText(jzDataSource.getCurrentKey().toString());
        }
        if (state == STATE_PREPARING) {
            changeUiToPreparing();
            if (bottomContainer.getVisibility() == View.VISIBLE) {
            } else {
                setSystemTimeAndBattery();
            }
        } else if (state == STATE_PLAYING) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToPlayingClear();
            } else {
                changeUiToPlayingShow();
            }
        } else if (state == STATE_PAUSE) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToPauseClear();
            } else {
                changeUiToPauseShow();
            }
        }
    }

    /**
     * 设置显示系统时间和电池
     */
    public void setSystemTimeAndBattery() {
        SimpleDateFormat dateFormater = new SimpleDateFormat("HH:mm");
        Date date = new Date();
        videoCurrentTime.setText(dateFormater.format(date));
        //超过30秒则重新发起广播获取电量
        if ((System.currentTimeMillis() - LAST_GET_BATTERYLEVEL_TIME) > 30000) {
            LAST_GET_BATTERYLEVEL_TIME = System.currentTimeMillis();
            getContext().registerReceiver(
                    batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            );
        } else {
            setBatteryLevel();
        }
    }

    /**
     * 设置电量图标
     */
    public void setBatteryLevel() {
        int percent = LAST_GET_BATTERYLEVEL_PERCENT;
        if (percent < 15) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_10);
        } else if (percent >= 15 && percent < 40) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_30);
        } else if (percent >= 40 && percent < 60) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_50);
        } else if (percent >= 60 && percent < 80) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_70);
        } else if (percent >= 80 && percent < 95) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_90);
        } else if (percent >= 95 && percent <= 100) {
            batteryLevel.setBackgroundResource(R.drawable.jz_battery_level_100);
        }
    }

    public void onCLickUiToggleToClear() {
        if (state == STATE_PREPARING) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToPreparing();
            } else {
            }
        } else if (state == STATE_PLAYING) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToPlayingClear();
            } else {
            }
        } else if (state == STATE_PAUSE) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToPauseClear();
            } else {
            }
        } else if (state == STATE_AUTO_COMPLETE) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToComplete();
            } else {
            }
        }
    }


    @Override
    public void onProgress(int progress, long position, long duration) {
        super.onProgress(progress, position, duration);
        if (progress != 0) bottomProgressBar.setProgress(progress);
    }

    @Override
    public void setBufferProgress(int bufferProgress) {
        super.setBufferProgress(bufferProgress);
        if (bufferProgress != 0) bottomProgressBar.setSecondaryProgress(bufferProgress);
    }

    @Override
    public void resetProgressAndTime() {
        super.resetProgressAndTime();
        bottomProgressBar.setProgress(0);
        bottomProgressBar.setSecondaryProgress(0);
    }

    /**
     * 切换UI为空闲状态
     */
    public void changeUiToNormal() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }
    }

    /**
     * 切换UI为准备中状态
     */
    public void changeUiToPreparing() {
        switch (screen) {
            case SCREEN_NORMAL:
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.VISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 显示UI，当处于播放中状态时
     */
    public void changeUiToPlayingShow() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 隐藏UI，当处于播放中状态时
     */
    public void changeUiToPlayingClear() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE);
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE);
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 显示UI，当处于暂停状态时
     */
    public void changeUiToPauseShow() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.VISIBLE, View.VISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }
    }

    /**
     * 隐藏UI，当处于暂停状态时
     */
    public void changeUiToPauseClear() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE);
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.INVISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.VISIBLE, View.INVISIBLE);
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 切换UI为播放完毕状态
     */
    public void changeUiToComplete() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.VISIBLE, View.INVISIBLE, View.INVISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 切换UI为播放失败状态
     */
    public void changeUiToError() {
        switch (screen) {
            case SCREEN_NORMAL:
                setAllControlsVisibility(View.INVISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.VISIBLE);
                updateStartImage();
                break;
            case SCREEN_FULLSCREEN:
                setAllControlsVisibility(View.VISIBLE, View.INVISIBLE, View.VISIBLE,
                        View.INVISIBLE, View.INVISIBLE, View.INVISIBLE, View.VISIBLE);
                updateStartImage();
                break;
            case SCREEN_TINY:
                break;
        }

    }

    /**
     * 设置全部的控制按钮的显示隐藏
     * @param topCon 顶部容器
     * @param bottomCon 底部容器
     * @param startBtn 开始、暂停按钮
     * @param loadingPro 加载中弹窗
     * @param thumbImg 缩略图
     * @param bottomPro  底部弹窗
     * @param retryLayout 重新尝试播放布局
     */
    public void setAllControlsVisibility(int topCon, int bottomCon, int startBtn, int loadingPro,
                                         int thumbImg, int bottomPro, int retryLayout) {
        topContainer.setVisibility(topCon);
        bottomContainer.setVisibility(bottomCon);
        startButton.setVisibility(startBtn);
        loadingProgressBar.setVisibility(loadingPro);
        thumbImageView.setVisibility(thumbImg);
        bottomProgressBar.setVisibility(bottomPro);
        mRetryLayout.setVisibility(retryLayout);
    }

    /**
     * 更新开始、暂停、重试按钮图标状态
     */
    public void updateStartImage() {
        if (state == STATE_PLAYING) {
            startButton.setVisibility(VISIBLE);
            startButton.setImageResource(R.drawable.jz_click_pause_selector);
            replayTextView.setVisibility(GONE);
        } else if (state == STATE_ERROR) {
            startButton.setVisibility(INVISIBLE);
            replayTextView.setVisibility(GONE);
        } else if (state == STATE_AUTO_COMPLETE) {
            startButton.setVisibility(VISIBLE);
            startButton.setImageResource(R.drawable.jz_click_replay_selector);
            replayTextView.setVisibility(VISIBLE);
        } else {
            startButton.setImageResource(R.drawable.jz_click_play_selector);
            replayTextView.setVisibility(GONE);
        }
    }

    @Override
    public void showProgressDialog(float deltaX, String seekTime, long seekTimePosition, String totalTime, long totalTimeDuration) {
        super.showProgressDialog(deltaX, seekTime, seekTimePosition, totalTime, totalTimeDuration);
        if (mProgressDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.jz_dialog_progress, null);
            mDialogProgressBar = localView.findViewById(R.id.duration_progressbar);
            mDialogSeekTime = localView.findViewById(R.id.tv_current);
            mDialogTotalTime = localView.findViewById(R.id.tv_duration);
            mDialogIcon = localView.findViewById(R.id.duration_image_tip);
            mProgressDialog = createDialogWithView(localView);
        }
        if (!mProgressDialog.isShowing()) {
            mProgressDialog.show();
        }

        mDialogSeekTime.setText(seekTime);
        mDialogTotalTime.setText(" / " + totalTime);
        mDialogProgressBar.setProgress(totalTimeDuration <= 0 ? 0 : (int) (seekTimePosition * 100 / totalTimeDuration));
        if (deltaX > 0) {
            mDialogIcon.setBackgroundResource(R.drawable.jz_forward_icon);
        } else {
            mDialogIcon.setBackgroundResource(R.drawable.jz_backward_icon);
        }
        onCLickUiToggleToClear();
    }

    @Override
    public void dismissProgressDialog() {
        super.dismissProgressDialog();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void showVolumeDialog(float deltaY, int volumePercent) {
        super.showVolumeDialog(deltaY, volumePercent);
        if (mVolumeDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.jz_dialog_volume, null);
            mDialogVolumeImageView = localView.findViewById(R.id.volume_image_tip);
            mDialogVolumeTextView = localView.findViewById(R.id.tv_volume);
            mDialogVolumeProgressBar = localView.findViewById(R.id.volume_progressbar);
            mVolumeDialog = createDialogWithView(localView);
        }
        if (!mVolumeDialog.isShowing()) {
            mVolumeDialog.show();
        }
        if (volumePercent <= 0) {
            mDialogVolumeImageView.setBackgroundResource(R.drawable.jz_close_volume);
        } else {
            mDialogVolumeImageView.setBackgroundResource(R.drawable.jz_add_volume);
        }
        if (volumePercent > 100) {
            volumePercent = 100;
        } else if (volumePercent < 0) {
            volumePercent = 0;
        }
        mDialogVolumeTextView.setText(volumePercent + "%");
        mDialogVolumeProgressBar.setProgress(volumePercent);
        onCLickUiToggleToClear();
    }

    @Override
    public void dismissVolumeDialog() {
        super.dismissVolumeDialog();
        if (mVolumeDialog != null) {
            mVolumeDialog.dismiss();
        }
    }

    @Override
    public void showBrightnessDialog(int brightnessPercent) {
        super.showBrightnessDialog(brightnessPercent);
        if (mBrightnessDialog == null) {
            View localView = LayoutInflater.from(getContext()).inflate(R.layout.jz_dialog_brightness, null);
            mDialogBrightnessTextView = localView.findViewById(R.id.tv_brightness);
            mDialogBrightnessProgressBar = localView.findViewById(R.id.brightness_progressbar);
            mBrightnessDialog = createDialogWithView(localView);
        }
        if (!mBrightnessDialog.isShowing()) {
            mBrightnessDialog.show();
        }
        if (brightnessPercent > 100) {
            brightnessPercent = 100;
        } else if (brightnessPercent < 0) {
            brightnessPercent = 0;
        }
        mDialogBrightnessTextView.setText(brightnessPercent + "%");
        mDialogBrightnessProgressBar.setProgress(brightnessPercent);
        onCLickUiToggleToClear();
    }

    @Override
    public void dismissBrightnessDialog() {
        super.dismissBrightnessDialog();
        if (mBrightnessDialog != null) {
            mBrightnessDialog.dismiss();
        }
    }

    /**
     * 根据布局创建弹窗
     * @param localView 布局
     * @return
     */
    public Dialog createDialogWithView(View localView) {
        Dialog dialog = new Dialog(getContext(), R.style.jz_style_dialog_progress);
        dialog.setContentView(localView);
        Window window = dialog.getWindow();
        window.addFlags(Window.FEATURE_ACTION_BAR);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        window.setLayout(-2, -2);
        WindowManager.LayoutParams localLayoutParams = window.getAttributes();
        localLayoutParams.gravity = Gravity.CENTER;
        window.setAttributes(localLayoutParams);
        return dialog;
    }

    /**
     * 延迟2.5秒执行隐藏控制控件任务
     */
    public void startDismissControlViewTimer() {
        cancelDismissControlViewTimer();
        DISMISS_CONTROL_VIEW_TIMER = new Timer();
        mDismissControlViewTimerTask = new DismissControlViewTimerTask();
        DISMISS_CONTROL_VIEW_TIMER.schedule(mDismissControlViewTimerTask, 2500);
    }

    /**
     * 取消执行隐藏控制控件任务
     */
    public void cancelDismissControlViewTimer() {
        if (DISMISS_CONTROL_VIEW_TIMER != null) {
            DISMISS_CONTROL_VIEW_TIMER.cancel();
        }
        if (mDismissControlViewTimerTask != null) {
            mDismissControlViewTimerTask.cancel();
        }

    }

    @Override
    public void onAutoCompletion() {
        super.onAutoCompletion();
        cancelDismissControlViewTimer();
    }

    @Override
    public void reset() {
        super.reset();
        cancelDismissControlViewTimer();
        if (clarityPopWindow != null) {
            clarityPopWindow.dismiss();
        }
    }

    /**
     * 隐藏控制布局，比如顶部、底部控件、开始按钮等
     */
    public void dismissControlView() {
        if (state != STATE_NORMAL
                && state != STATE_ERROR
                && state != STATE_AUTO_COMPLETE) {
            post(() -> {
                bottomContainer.setVisibility(View.INVISIBLE);
                topContainer.setVisibility(View.INVISIBLE);
                startButton.setVisibility(View.INVISIBLE);
                if (clarityPopWindow != null) {
                    clarityPopWindow.dismiss();
                }
                if (screen != SCREEN_TINY) {
                    bottomProgressBar.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    public class DismissControlViewTimerTask extends TimerTask {

        @Override
        public void run() {
            dismissControlView();
        }
    }

    /**
     * 用于接收电池电量变化广播
     */
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //电池电量变化监听
            if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                //当前剩余电量
                int level = intent.getIntExtra("level", 0);
                //总电量
                int scale = intent.getIntExtra("scale", 100);
                //当前剩余电量百分比
                int percent = level * 100 / scale;
                LAST_GET_BATTERYLEVEL_PERCENT = percent;
                setBatteryLevel();
                try {
                    getContext().unregisterReceiver(batteryReceiver);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
