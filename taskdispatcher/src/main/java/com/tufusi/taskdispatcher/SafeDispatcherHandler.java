package com.tufusi.taskdispatcher;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Created by 鼠夏目 on 2020/3/12.
 *
 * @See
 * @Description 线程安全的任务调度器句柄
 */
public class SafeDispatcherHandler extends Handler {

    private static final String TAG = "SafeDispatcherHandler";

    SafeDispatcherHandler(Looper looper) {
        super(looper);
    }

    @Override
    public void dispatchMessage(Message msg) {
        try {
            super.dispatchMessage(msg);
        } catch (Exception e) {
            Log.d(TAG, "dispatchMessage Exception " + msg + " , " + e);
        } catch (Error error) {
            Log.d(TAG, "dispatchMessage error " + msg + " , " + error);
        }
    }
}
