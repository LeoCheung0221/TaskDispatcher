package com.tufusi.taskdispatcher;

import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by 鼠夏目 on 2020/3/13.
 *
 * @See
 * @Description 抽象任务类线程
 */
public abstract class AbstractTask<R> implements Runnable {

    private static final String TAG = "Task";

    private AtomicBoolean mCanceledAtomic = new AtomicBoolean(false);
    private AtomicReference<Thread> mTaskThread = new AtomicReference<>();

    /**
     * 异步任务处理线程，在非主线程中执行
     *
     * @return 处理结果
     * @throws InterruptedException 获取InterruptedException异常，来判断任务是否被取消
     */
    public abstract R doInBackground() throws InterruptedException;

    /**
     * 异步线程处理后的结果，在主线程中执行
     *
     * @param result 处理结果
     */
    public abstract void onSuccess(R result);

    /**
     * 异步线程处理出现异常的回调，非必处理，在主线程中执行
     *
     * @param throwable 抛出的异常
     */
    public void onFail(Throwable throwable) {
    }

    /**
     * 任务被取消进入的回调
     */
    public void onCancel() {
    }

    /**
     * 标记任务为取消标记，这无法真正取消任务，只是通过触发interrupt()让线程回调onFail(), 不让结果回调onSuccess()方法
     */
    void cancel() {
        this.mCanceledAtomic.set(true);
        Thread thread = mTaskThread.get();
        if (thread != null) {
            Log.d(TAG, "Task cancel: " + thread.getName());
            thread.interrupt();
        }

        TaskDispatcher.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                onCancel();
            }
        });
    }

    /**
     * 任务是已取消
     *
     * @return 任务是否已被取消
     */
    public boolean isCanceled() {
        return mCanceledAtomic.get();
    }

    @Override
    public void run() {
        try {
            Log.d(TAG, "task run: " + Thread.currentThread().getName());
            mTaskThread.compareAndSet(null, Thread.currentThread());

            mCanceledAtomic.set(false);

            final R result = doInBackground();
            TaskDispatcher.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCanceled()) {
                        onSuccess(result);
                    }
                }
            });
        } catch (final Throwable throwable) {
            Log.e(TAG, "handle background task error " + throwable);
            TaskDispatcher.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!isCanceled()) {
                        onFail(throwable);
                    }
                }
            });
        }
    }
}
