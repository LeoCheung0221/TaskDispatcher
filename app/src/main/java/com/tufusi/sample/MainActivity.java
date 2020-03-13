package com.tufusi.sample;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.tufusi.taskdispatcher.AbstractDispatcherTask;
import com.tufusi.taskdispatcher.AbstractTask;
import com.tufusi.taskdispatcher.TaskDispatcher;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    private AbstractTask<String> mDemoTask;
    private long mStartMillis;
    private AbstractDispatcherTask mDispatcherTask;
    private int times = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.start_task).setOnClickListener(this);
        findViewById(R.id.cancel_task).setOnClickListener(this);
        findViewById(R.id.timeout_task).setOnClickListener(this);
        findViewById(R.id.lift_task).setOnClickListener(this);

        mDemoTask = new AbstractTask<String>() {
            @Override
            public String doInBackground() {

                mStartMillis = System.currentTimeMillis();
                try {
                    Thread.sleep(8000);
                } catch (InterruptedException e) {
                    Log.i(TAG, "" + e);
                }
                Log.i(TAG, "withResultTask doInBackground, current thread is ? " + Thread.currentThread().getName());
                return "休眠" + (System.currentTimeMillis() - mStartMillis) / 1000 + "秒";
            }

            @Override
            public void onSuccess(String result) {
                Log.i(TAG, "withResultTask onSuccess, current thread is main ? "
                        + TaskDispatcher.isMainThread() + ", result : " + result);
            }

            @Override
            public void onFail(Throwable throwable) {
                super.onFail(throwable);
                Log.i(TAG, "onFail： 休眠 " + (System.currentTimeMillis() - mStartMillis) / 1000 + "秒");
            }

            @Override
            public void onCancel() {
                super.onCancel();
                Log.i(TAG, "onCancel： 休眠 " + (System.currentTimeMillis() - mStartMillis) / 1000 + "秒");
            }
        };

        mDispatcherTask = new AbstractDispatcherTask(1000, false, 3000) {
            @Override
            public void onDispatch() {
                if (times-- < 0) {
                    TaskDispatcher.stopDispatchTask(this);
                } else {
                    Log.i(TAG, " current thread is ? " + Thread.currentThread().getName() + " uptimeMillis " + SystemClock.uptimeMillis());
                }
            }
        };
    }

    /**
     * 期望结果是休眠3秒，回调到onCancel,代表超时
     */
    private void timeOutTask() {
        TaskDispatcher.executeTimeOutTask(3000, mDemoTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TaskDispatcher.cancelTask(mDemoTask);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.start_task:
                Log.i(TAG, "startTask");
                times = 10;
                TaskDispatcher.dispatchTask(mDispatcherTask);
//                noResultTask();
//                withResultTask();
                break;
            case R.id.cancel_task:
                Log.i(TAG, "cancelTask");
                TaskDispatcher.stopDispatchTask(mDispatcherTask);
//                TaskDispatcher.cancelTask(mDemoTask);
                break;
            case R.id.timeout_task:
                Log.i(TAG, "timeOutTask");
                timeOutTask();
                break;
            case R.id.lift_task:
                Log.i(TAG, "lifeTask");
                getSupportFragmentManager().beginTransaction().replace(R.id.life_fragment_container, new LifeFragment()).commitAllowingStateLoss();
                break;
            default:
                break;
        }
    }

    private void noResultTask() {
        TaskDispatcher.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                Log.i(TAG, "noResultTask current thread is main ? " + TaskDispatcher.isMainThread());
            }
        });
    }

    private void withResultTask() {
        TaskDispatcher.execute(mDemoTask);
    }
}
