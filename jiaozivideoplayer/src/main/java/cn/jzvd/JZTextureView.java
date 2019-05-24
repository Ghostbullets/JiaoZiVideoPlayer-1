package cn.jzvd;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

/**
 * <p>参照Android系统的VideoView的onMeasure方法
 * <br>注意!relativelayout中无法全屏，要嵌套一个linearlayout</p>
 * <p>Referring Android system Video View of onMeasure method
 * <br>NOTE! Can not fullscreen relativelayout, to nest a linearlayout</p>
 * Created by Nathen
 * On 2016/06/02 00:01
 */
public class JZTextureView extends TextureView {
    protected static final String TAG = "JZResizeTextureView";

    public int currentVideoWidth = 0;
    public int currentVideoHeight = 0;

    public JZTextureView(Context context) {
        super(context);
        currentVideoWidth = 0;
        currentVideoHeight = 0;
    }

    public JZTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        currentVideoWidth = 0;
        currentVideoHeight = 0;
    }

    public void setVideoSize(int currentVideoWidth, int currentVideoHeight) {
        if (this.currentVideoWidth != currentVideoWidth || this.currentVideoHeight != currentVideoHeight) {
            this.currentVideoWidth = currentVideoWidth;
            this.currentVideoHeight = currentVideoHeight;
            requestLayout();
        }
    }

    @Override
    public void setRotation(float rotation) {
        if (rotation != getRotation()) {
            super.setRotation(rotation);
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        Log.i(TAG, "onMeasure " + " [" + this.hashCode() + "] ");
        //当前旋转角度，视频宽、高
        int viewRotation = (int) getRotation();
        int videoWidth = currentVideoWidth;
        int videoHeight = currentVideoHeight;

        //父控件宽高
        int parentHeight = ((View) getParent()).getMeasuredHeight();
        int parentWidth = ((View) getParent()).getMeasuredWidth();
        if (parentWidth != 0 && parentHeight != 0 && videoWidth != 0 && videoHeight != 0) {
            //视频图像显示类型为----充满父容器
            if (Jzvd.VIDEO_IMAGE_DISPLAY_TYPE == Jzvd.VIDEO_IMAGE_DISPLAY_TYPE_FILL_PARENT) {
                //交换宽高参数
                if (viewRotation == 90 || viewRotation == 270) {
                    int tempSize = parentWidth;
                    parentWidth = parentHeight;
                    parentHeight = tempSize;
                }
                /**强制充满**/
                videoHeight = videoWidth * parentHeight / parentWidth;
            }
        }

        // 如果判断成立，则说明显示的TextureView和本身的位置是有90度的旋转的，所以需要交换宽高参数。
        if (viewRotation == 90 || viewRotation == 270) {
            int tempMeasureSpec = widthMeasureSpec;
            widthMeasureSpec = heightMeasureSpec;
            heightMeasureSpec = tempMeasureSpec;
        }

        int width = getDefaultSize(videoWidth, widthMeasureSpec);
        int height = getDefaultSize(videoHeight, heightMeasureSpec);
        if (videoWidth > 0 && videoHeight > 0) {

            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);

            Log.i(TAG, "widthMeasureSpec  [" + MeasureSpec.toString(widthMeasureSpec) + "]");
            Log.i(TAG, "heightMeasureSpec [" + MeasureSpec.toString(heightMeasureSpec) + "]");

            //长宽都固定，根据长宽比调整大小
            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                // the size is fixed
                width = widthSpecSize;
                height = heightSpecSize;
                // for compatibility, we adjust size based on aspect ratio
                //把大于的长度调整大小
                if (videoWidth * height < width * videoHeight) {
                    width = height * videoWidth / videoHeight;
                } else if (videoWidth * height > width * videoHeight) {
                    height = width * videoHeight / videoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                // only the width is fixed, adjust the height to match aspect ratio if possible 只有宽度是固定的，如果可能的话调整高度以匹配长宽比
                width = widthSpecSize;
                height = width * videoHeight / videoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // couldn't match aspect ratio within the constraints 无法在约束条件下匹配纵横比
                    height = heightSpecSize;
                    width = height * videoWidth / videoHeight;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                // only the height is fixed, adjust the width to match aspect ratio if possible
                height = heightSpecSize;
                width = height * videoWidth / videoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // couldn't match aspect ratio within the constraints
                    width = widthSpecSize;
                    height = width * videoHeight / videoWidth;
                }
            } else {
                // neither the width nor the height are fixed, try to use actual video size 宽度和高度都不固定，尽量使用实际的视频大小
                width = videoWidth;
                height = videoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                    // too tall, decrease both width and height
                    height = heightSpecSize;
                    width = height * videoWidth / videoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                    // too wide, decrease both width and height
                    width = widthSpecSize;
                    height = width * videoHeight / videoWidth;
                }
            }
        } else {
            // no size yet, just adopt the given spec sizes 没有尺寸，只是采用给定的规格尺寸
        }
        if (parentWidth != 0 && parentHeight != 0 && videoWidth != 0 && videoHeight != 0) {
            if (Jzvd.VIDEO_IMAGE_DISPLAY_TYPE == Jzvd.VIDEO_IMAGE_DISPLAY_TYPE_ORIGINAL) {
                /**原图**/
                height = videoHeight;
                width = videoWidth;
            } else if (Jzvd.VIDEO_IMAGE_DISPLAY_TYPE == Jzvd.VIDEO_IMAGE_DISPLAY_TYPE_FILL_SCROP) {
                if (viewRotation == 90 || viewRotation == 270) {
                    int tempSize = parentWidth;
                    parentWidth = parentHeight;
                    parentHeight = tempSize;
                }
                /**充满剪切**/
                if (((double) videoHeight / videoWidth) > ((double) parentHeight / parentWidth)) {
                    height = (int) (((double) parentWidth / (double) width * (double) height));
                    width = parentWidth;
                } else if (((double) videoHeight / videoWidth) < ((double) parentHeight / parentWidth)) {
                    width = (int) (((double) parentHeight / (double) height * (double) width));
                    height = parentHeight;
                }
            }
        }
        setMeasuredDimension(width, height);
    }
}
