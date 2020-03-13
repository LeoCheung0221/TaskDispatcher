package com.tufusi.sample;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.tufusi.taskdispatcher.TaskDispatcher;

public class LifeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_test, container, false);
        rootView.findViewById(R.id.remove_fragment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeThis();
            }
        });
        runTask();
        return rootView;
    }

    private void removeThis() {
        if (getFragmentManager() != null) {
            getFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        }
    }

    private void runTask() {
        TaskDispatcher.runOnUIThread(new Runnable() {
            @Override
            public void run() {

                Log.i("LifeFragment", "runTask no life");
            }
        }, 5000);

        TaskDispatcher.runOnUIThread(this, new Runnable() {
            @Override
            public void run() {
                Log.i("LifeFragment", "runTask with life");
            }
        }, 5000);
        TaskDispatcher.runOnUIThread(this, Lifecycle.Event.ON_STOP, new Runnable() {
            @Override
            public void run() {
                Log.i("LifeFragment", "runTask with life on Stop");
            }
        }, 5000);

        TaskDispatcher.runLifecycleRunnable(this, TaskDispatcher.ioHandler(), new Runnable() {
            @Override
            public void run() {
                Log.i("LifeFragment", "io thread runTask with life");
            }
        }, 5000);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("LifeFragment", "onDestroy");
    }
}
