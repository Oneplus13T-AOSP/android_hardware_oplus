package android.app;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;

import android.app.ActivityManager.RunningTaskInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import com.oplus.osense.complexscene.OplusComplexSceneObserver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Camera-only bridge for the stock float-window scene (type 2). */
final class OplusWindowSceneCompat {
    private static final String TAG = "OplusWindowSceneCompat";
    private static final int FLOAT_WINDOW = 2;
    private static final int ENTER_FLOAT_WINDOW = 3;
    private static final int EXIT_FLOAT_WINDOW = 4;
    private static final OplusWindowSceneCompat INSTANCE = new OplusWindowSceneCompat();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Set<OplusComplexSceneObserver> mObservers = new HashSet<>();
    private Set<Integer> mFloatingTasks = new HashSet<>();
    private boolean mListening;
    private int mGeneration;
    private final Runnable mRefresh = this::refresh;
    private final TaskStackListener mListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            scheduleRefresh();
        }

        @Override
        public void onActivityPinned(String packageName, int userId, int taskId, int rootTaskId) {
            scheduleRefresh();
        }

        @Override
        public void onActivityUnpinned() {
            scheduleRefresh();
        }

        @Override
        public void onTaskDisplayChanged(int taskId, int displayId) {
            scheduleRefresh();
        }

        @Override
        public void onTaskFocusChanged(int taskId, boolean focused) {
            scheduleRefresh();
        }
    };

    static List<RunningTaskInfo> getVisibleTasks(int displayId) throws RemoteException {
        List<RunningTaskInfo> result = new ArrayList<>();
        for (RunningTaskInfo task : ActivityTaskManager.getService().getTasks(
                Integer.MAX_VALUE, false, false, displayId)) {
            int type = task.getActivityType();
            if (task.isVisibleRequested && task.topActivity != null
                    && type != ACTIVITY_TYPE_HOME && type != ACTIVITY_TYPE_RECENTS) {
                result.add(task);
            }
        }
        // AOSP puts the focused task first; the camera stops scanning at its own task.
        // Keep visible floating windows ahead of the focused fullscreen camera task.
        result.sort((left, right) -> Boolean.compare(isFloating(right), isFloating(left)));
        return result;
    }

    private static boolean isFloating(RunningTaskInfo task) {
        int mode = task.getWindowingMode();
        return mode == WINDOWING_MODE_FREEFORM || mode == WINDOWING_MODE_PINNED;
    }

    private static boolean supports(Bundle bundle) {
        if (bundle == null) return false;
        ArrayList<Integer> types = bundle.getIntegerArrayList("complexSceneTypeArray");
        return types != null && types.size() == 1 && types.get(0) != null
                && types.get(0) == FLOAT_WINDOW;
    }

    static boolean register(Bundle bundle, OplusComplexSceneObserver observer) {
        if (observer == null || !supports(bundle)) return false;
        return INSTANCE.add(observer);
    }

    static boolean unregister(Bundle bundle, OplusComplexSceneObserver observer) {
        if (observer == null || !supports(bundle)) return false;
        return INSTANCE.remove(observer);
    }

    private synchronized boolean add(OplusComplexSceneObserver observer) {
        if (!mListening) {
            try {
                ActivityTaskManager.getService().registerTaskStackListener(mListener);
                mListening = true;
            } catch (RemoteException | SecurityException e) {
                Log.w(TAG, "Cannot observe window scenes", e);
                return false;
            }
        }
        if (mObservers.add(observer)) mGeneration++;
        scheduleRefresh();
        return true;
    }

    private synchronized boolean remove(OplusComplexSceneObserver observer) {
        if (!mObservers.remove(observer)) return false;
        mGeneration++;
        if (mObservers.isEmpty()) {
            mHandler.removeCallbacks(mRefresh);
            mFloatingTasks.clear();
            try {
                ActivityTaskManager.getService().unregisterTaskStackListener(mListener);
                mListening = false;
            } catch (RemoteException | SecurityException e) {
                // Retain the registration flag so a later add cannot register twice.
                Log.w(TAG, "Cannot stop observing window scenes", e);
            }
        }
        return true;
    }

    private synchronized void scheduleRefresh() {
        if (mObservers.isEmpty()) return;
        mHandler.removeCallbacks(mRefresh);
        mHandler.post(mRefresh);
    }

    private void refresh() {
        final int generation;
        synchronized (this) {
            if (mObservers.isEmpty()) return;
            generation = mGeneration;
        }
        Set<Integer> current = new HashSet<>();
        try {
            // FloatingWindowDetectHelper queries display 0; do not mix external displays.
            for (RunningTaskInfo task : getVisibleTasks(Display.DEFAULT_DISPLAY)) {
                if (isFloating(task)) current.add(task.taskId);
            }
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "Cannot query window scenes", e);
            return; // A failed query is not an exit event.
        }
        boolean entered;
        boolean exited;
        List<OplusComplexSceneObserver> observers;
        synchronized (this) {
            if (mObservers.isEmpty() || generation != mGeneration) return;
            entered = !mFloatingTasks.containsAll(current);
            exited = !current.containsAll(mFloatingTasks);
            mFloatingTasks = current;
            observers = new ArrayList<>(mObservers);
        }
        // The camera only consumes event IDs and re-queries tasks; no OEM payload is needed.
        for (OplusComplexSceneObserver observer : observers) {
            synchronized (this) {
                if (!mObservers.contains(observer)) continue;
            }
            try {
                if (exited) observer.onChanged(EXIT_FLOAT_WINDOW, Collections.emptyList());
                if (entered) observer.onChanged(ENTER_FLOAT_WINDOW, Collections.emptyList());
            } catch (RuntimeException e) {
                Log.w(TAG, "Window scene callback failed", e);
            }
        }
    }
}
