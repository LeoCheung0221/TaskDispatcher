package com.tufusi.taskdispatcher;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by 鼠夏目 on 2020/3/12.
 *
 * @See
 * @Description 调度任务抽象类 - 实现线程执行函数
 */
public abstract class AbstractDispatcherTask implements Runnable {

    long startDelayMillisecond;
    long periodMillisecond;
    boolean mainThread = true;
    AtomicBoolean canceled = new AtomicBoolean(false);

    protected AbstractDispatcherTask(long periodMillisecond) {
        this.periodMillisecond = periodMillisecond;
    }

    protected AbstractDispatcherTask(long periodMillisecond, boolean mainThread) {
        this.periodMillisecond = periodMillisecond;
        this.mainThread = mainThread;
    }

    protected AbstractDispatcherTask(long periodMillisecond, boolean mainThread, long startDelayMillisecond) {
        this.periodMillisecond = periodMillisecond;
        this.mainThread = mainThread;
        this.startDelayMillisecond = startDelayMillisecond;
    }

    /**
     * 实现分发任务方法
     */
    public abstract void onDispatch();

    @Override
    public void run() {
        if (!canceled.get()) {
            onDispatch();
        }
    }
}
