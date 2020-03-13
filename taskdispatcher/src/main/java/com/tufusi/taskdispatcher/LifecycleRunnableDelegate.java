package com.tufusi.taskdispatcher;

import android.os.Handler;

import androidx.lifecycle.GenericLifecycleObserver;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

/**
 * Created by 鼠夏目 on 2020/3/13.
 *
 * @See
 * @Description 生命周期委托线程
 */
public class LifecycleRunnableDelegate implements Runnable {

    private Runnable mOriginRunnable;
    private LifecycleOwner mLifecycleOwner;
    private GenericLifecycleObserver mLifecycleObserver;

    LifecycleRunnableDelegate(LifecycleOwner lifecycleOwner, final Handler handler, final Lifecycle.Event targetEvent, final Runnable originRunnable) {
        if (originRunnable == null || lifecycleOwner == null) {
            return;
        }
        this.mLifecycleOwner = lifecycleOwner;
        this.mOriginRunnable = originRunnable;
        mLifecycleObserver = new GenericLifecycleObserver() {
            @Override
            public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
                if (event == targetEvent) {
                    if (mLifecycleOwner != null) {
                        mLifecycleOwner.getLifecycle().removeObserver(this);
                    }
                    handler.removeCallbacks(LifecycleRunnableDelegate.this);
                }
            }
        };
        if (TaskDispatcher.isMainThread()) {
            mLifecycleOwner.getLifecycle().addObserver(mLifecycleObserver);
        } else {
            TaskDispatcher.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    mLifecycleOwner.getLifecycle().addObserver(mLifecycleObserver);
                }
            });
        }
    }

    @Override
    public void run() {
        if (mOriginRunnable != null && mLifecycleOwner != null) {
            mOriginRunnable.run();
            mLifecycleOwner.getLifecycle().removeObserver(mLifecycleObserver);
        }
    }
}
