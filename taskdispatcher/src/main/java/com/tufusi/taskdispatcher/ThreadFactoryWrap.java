package com.tufusi.taskdispatcher;

import android.os.Process;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by 鼠夏目 on 2020/3/12.
 *
 * @See
 * @Description 线程生产类 封装方法
 */
public class ThreadFactoryWrap {

    static final class BackgroundRunnable implements Runnable {

        private Runnable runnable;

        BackgroundRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            //设置线程优先级别
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            runnable.run();
        }
    }

    /**
     * 任务调度生产
     */
    static final ThreadFactory TASK_DISPATCHER_FACTORY = new ThreadFactory() {

        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(new BackgroundRunnable(r), "TaskDispatcher  #" + count.getAndIncrement());
        }
    };

    /**
     * 超时线程生产
     */
    static final ThreadFactory TIME_OUT_THREAD_FACTORY = new ThreadFactory() {

        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(new BackgroundRunnable(r), "TaskDispatcher timeoutThread #" + count.getAndIncrement());
        }
    };

    /**
     * 调度线程生产
     */
    static final ThreadFactory DISPATCHER_THREAD_FACTORY = new ThreadFactory() {

        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(new BackgroundRunnable(r), "TaskDispatcher scheduler #" + count.getAndIncrement());
        }
    };

}
