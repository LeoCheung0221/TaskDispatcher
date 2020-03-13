package com.tufusi.taskdispatcher;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by 鼠夏目 on 2020/3/12.
 *
 * @See
 * @Description 任务分发/调拨类
 */
public class TaskDispatcher {

    private static final String TAG = "TaskDispatcher";
    private static volatile TaskDispatcher sTaskDispatcher = null;

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    /**
     * 复制AsyncTask
     * 从AsyncTask复制的核心池中至少有2个线程，最多4个线程，
     * 更倾向于比CPU处理器数量少一个，从而避免饱和，同时希望核心池中至少有2个线程和最多4个线程
     * 从AsyncTask复制CPU与后台工作
     */
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    /**
     * 线程池中最大数量
     */
    private static final int MAXIMUM_POOL_SIZE = CORE_POOL_SIZE * 2 + 1;
    /**
     * 线程存活时长
     */
    private static final long KEEP_ALIVE = 60L;
    /**
     * 线程池工作队列（用阻塞队列装载）
     */
    private static final BlockingQueue<Runnable> POOL_WORK_QUEUE = new LinkedBlockingDeque<>(128);

    private ThreadPoolExecutor mParallelExecutor;
    private ThreadPoolExecutor mTimeOutExecutor;

    private Handler mIOHandler;
    private SafeDispatcherHandler mMainHandler = new SafeDispatcherHandler(Looper.getMainLooper());

    /**
     * 日志输出实现
     */
    private ILog mILog = new ILog() {
        @Override
        public void info(String info) {
            Log.i(TAG, info);
        }

        @Override
        public void error(String error) {
            Log.e(TAG, error);
        }
    };

    /**
     * 私有构造函数
     */
    private TaskDispatcher() {
        //创建线程池执行器 - 任务调度
        mParallelExecutor = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, POOL_WORK_QUEUE, ThreadFactoryWrap.TASK_DISPATCHER_FACTORY);
        //创建线程池执行器 - 超时
        //这里创建超时线程只能通过SynchronousQueue
        //没有核心线程的线程池要用SynchronousQueue，而不是LinkedBlockingQueue，SynchronousQueue是一个只有一个任务的队列，
        //这样每次就会创建非核心线程执行任务,因为线程池任务放入队列的优先级比创建非核心线程优先级大.
        mTimeOutExecutor = new ThreadPoolExecutor(0, MAXIMUM_POOL_SIZE, KEEP_ALIVE, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), ThreadFactoryWrap.TIME_OUT_THREAD_FACTORY);

        mIOHandler = provideHandler("IoHandler");
    }

    public static TaskDispatcher getInstance() {
        if (sTaskDispatcher == null) {
            synchronized (TaskDispatcher.class) {
                if (sTaskDispatcher == null) {
                    sTaskDispatcher = new TaskDispatcher();
                }
            }
        }
        return sTaskDispatcher;
    }

    /**
     * 获取回调到handlerName的handler。
     * 用于在一个后台线程执行同一种任务时，目的为了线程安全。如数据库、文件操作等等
     *
     * @param handlerName 线程句柄名称
     * @return 异步任务handler
     */
    private static Handler provideHandler(String handlerName) {
        HandlerThread handlerThread = new HandlerThread(handlerName, Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();

        return new SafeDispatcherHandler(handlerThread.getLooper());
    }

    /**
     * 提供一个公用的异步handler
     */
    public static Handler ioHandler() {
        return getInstance().mIOHandler;
    }

    /**
     * 添加日志输出实现
     */
    public static void addLogImpl(ILog taskLog) {
        if (taskLog != null) {
            getInstance().mILog = taskLog;
        }
    }

    /**
     * 执行器平行执行
     *
     * @return 返回执行服务
     */
    public static ExecutorService executorService() {
        return getInstance().mParallelExecutor;
    }

    /**
     * 主线程周期性执行任务，默认立刻执行，之后间隔period执行，不需要时注意取消,每次执行时如果有相同的任务，默认会先取消
     *
     * @param task 执行的任务
     */
    public static void dispatchTask(final AbstractDispatcherTask task) {
        task.canceled.compareAndSet(true, false);
        final ScheduledExecutorService service = new ScheduledThreadPoolExecutor(1, ThreadFactoryWrap.DISPATCHER_THREAD_FACTORY);

        service.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (task.canceled.get()) {
                    service.shutdownNow();
                } else {
                    if (task.mainThread) {
                        runOnUIThread(task);
                    } else {
                        task.run();
                    }
                }
            }
        }, task.startDelayMillisecond, task.periodMillisecond, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消周期性任务
     *
     * @param dispatcherTask 任务对象
     */
    public static void stopDispatchTask(final AbstractDispatcherTask dispatcherTask) {
        dispatcherTask.canceled.compareAndSet(false, true);
    }

    /**
     * 执行一个无回调的后台任务
     */
    public static void execute(Runnable task) {
        getInstance().mILog.info("execute Runnable" + task.toString());
        getInstance().mParallelExecutor.execute(task);
    }

    /**
     * 执行一个后台任务，如果不需回调
     *
     * @see #execute(Runnable)
     **/
    public static <R> void execute(AbstractTask<R> task) {
        getInstance().mILog.info("execute task" + task.toString());
        getInstance().mParallelExecutor.execute(task);
    }

    /**
     * 主线程执行
     */
    public static void runOnUIThread(Runnable runnable) {
        getInstance().mMainHandler.post(runnable);
    }

    /**
     * 执行有生命周期的任务
     */
    public static Runnable runOnUIThread(LifecycleOwner lifecycleOwner, Runnable runnable) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, getInstance().mMainHandler, Lifecycle.Event.ON_DESTROY, runnable);
        getInstance().mMainHandler.post(lifecycleRunnableDelegate);
        return lifecycleRunnableDelegate;
    }

    /**
     * 执行有生命周期的任务，指定Lifecycle.Event
     */
    public static Runnable runOnUIThread(LifecycleOwner lifecycleOwner, Lifecycle.Event targetEvent, Runnable runnable) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, getInstance().mMainHandler, targetEvent, runnable);
        getInstance().mMainHandler.post(lifecycleRunnableDelegate);
        return lifecycleRunnableDelegate;
    }

    /**
     * 执行有生命周期的任务，并设置线程延迟执行时间
     *
     * @param runnable 待执行的线程
     * @param delayed  延迟时间
     */
    public static void runOnUIThread(Runnable runnable, long delayed) {
        getInstance().mMainHandler.postDelayed(runnable, delayed);
    }

    /**
     * 执行有生命周期的任务，并设置线程延迟执行时间
     *
     * @param lifecycleOwner Lifecycle持有者
     * @param runnable       待执行的线程
     * @param delayed        延迟时间
     * @return 被委托执行的拥有生命周期的线程，并在主线程中执行
     */
    public static Runnable runOnUIThread(LifecycleOwner lifecycleOwner, Runnable runnable, long delayed) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, getInstance().mMainHandler, Lifecycle.Event.ON_DESTROY, runnable);
        getInstance().mMainHandler.postDelayed(lifecycleRunnableDelegate, delayed);
        return lifecycleRunnableDelegate;
    }

    /**
     * 执行有生命周期的任务，并设置线程延迟执行时间
     *
     * @param lifecycleOwner Lifecycle持有者
     * @param targetEvent    指定Lifecycle.Event（当前生命周期改变对应的事件）
     * @param runnable       待执行的线程
     * @param delayed        延迟时间
     * @return 被委托执行的拥有生命周期的线程，并在主线程中执行
     */
    public static Runnable runOnUIThread(LifecycleOwner lifecycleOwner, Lifecycle.Event targetEvent, Runnable runnable, long delayed) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, getInstance().mMainHandler, targetEvent, runnable);
        getInstance().mMainHandler.postDelayed(lifecycleRunnableDelegate, delayed);
        return lifecycleRunnableDelegate;
    }

    /**
     * 外部提供执行任务的Handler
     */
    public static Runnable runLifecycleRunnable(LifecycleOwner lifecycleOwner, Handler anyThreadHandler, Runnable runnable) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, anyThreadHandler, Lifecycle.Event.ON_DESTROY, runnable);
        anyThreadHandler.post(lifecycleRunnableDelegate);
        return lifecycleRunnableDelegate;
    }

    /**
     * 外部提供执行任务的Handler，指定Lifecycle.Event
     */
    public static Runnable runLifecycleRunnable(LifecycleOwner lifecycleOwner, Handler anyThreadHandler, Runnable runnable, long delayed) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, anyThreadHandler, Lifecycle.Event.ON_DESTROY, runnable);
        anyThreadHandler.postDelayed(lifecycleRunnableDelegate, delayed);
        return lifecycleRunnableDelegate;
    }

    /**
     * 外部提供执行任务的Handler,指定移除的Lifecycle.Event
     */
    public static Runnable runLifecycleRunnable(LifecycleOwner lifecycleOwner, Handler anyThreadHandler, Lifecycle.Event targetEvent, Runnable runnable, long delayed) {
        LifecycleRunnableDelegate lifecycleRunnableDelegate = new LifecycleRunnableDelegate(lifecycleOwner, anyThreadHandler, targetEvent, runnable);
        anyThreadHandler.postDelayed(lifecycleRunnableDelegate, delayed);
        return lifecycleRunnableDelegate;
    }

    /**
     * 移除主线程回调
     *
     * @param runnable 在主线程中执行的线程
     */
    public static void removeUICallback(Runnable runnable) {
        mainHandler().removeCallbacks(runnable);
    }

    /**
     * 获取主线程句柄
     *
     * @return 返回Handler
     */
    public static Handler mainHandler() {
        return getInstance().mMainHandler;
    }

    public static boolean isMainThread() {
        return Thread.currentThread() == getInstance().mMainHandler.getLooper().getThread();
    }

    /**
     * 使用一个单独的线程池来执行超时任务，避免引起他线程不够用导致超时
     *
     * @param timeOutMillis 超时时间，单位毫秒
     *                      通过实现error(Exception) 判断是否为 TimeoutException 来判断是否超时,
     *                      不能100%保证实际的超时时间就是timeOutMillis，但一般没必要那么精确
     */
    public static <R> void executeTimeOutTask(final long timeOutMillis, final AbstractTask<R> timeOutTask) {
        final Future future = getInstance().mTimeOutExecutor.submit(timeOutTask);
        getInstance().mTimeOutExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    future.get(timeOutMillis, TimeUnit.MILLISECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!timeOutTask.isCanceled()) {
                                timeOutTask.cancel();
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 取消一个任务
     *
     * @param task 被取消的任务
     */
    public static void cancelTask(AbstractTask task) {
        if (task != null) {
            task.cancel();
        }
    }

}
