package net.kdt.pojavlaunch.progresskeeper;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ProgressKeeper {
    private static final HashMap<String, List<ProgressListener>> sProgressListeners = new HashMap<>();
    private static final HashMap<String, ProgressState> sProgressStates = new HashMap<>();
    private static final List<TaskCountListener> sTaskCountListeners = new ArrayList<>();
    /** When each currently-registered progress key was started, used by the stuck-task watchdog. */
    private static final HashMap<String, Long> sProgressStartTimes = new HashMap<>();
    /** A progress task older than this is considered stuck and may be cleared by the watchdog. */
    public static final long TASK_WATCHDOG_MILLIS = 20 * 60 * 1000L;

    public static synchronized void submitProgress(String progressRecord, int progress, int resid, Object... va) {
        ProgressState progressState = sProgressStates.get(progressRecord);
        boolean shouldCallStarted = progressState == null;
        boolean shouldCallEnded = resid == -1 && progress == -1;
        if(shouldCallEnded) {
            shouldCallStarted = false;
            sProgressStates.remove(progressRecord);
        }else if(shouldCallStarted){
            sProgressStates.put(progressRecord, (progressState = new ProgressState()));
        }
        if(shouldCallStarted) sProgressStartTimes.put(progressRecord, System.currentTimeMillis());
        else if(shouldCallEnded) sProgressStartTimes.remove(progressRecord);
        if(shouldCallEnded || shouldCallStarted) updateTaskCount(sProgressStates.size());
        if(progressState != null) {
            progressState.progress = progress;
            progressState.resid = resid;
            progressState.varArg = va;
        }

        List<ProgressListener> progressListeners = sProgressListeners.get(progressRecord);
        if(progressListeners != null)
            for(ProgressListener listener : progressListeners) {
                    if(shouldCallStarted) listener.onProgressStarted();
                    else if(shouldCallEnded) listener.onProgressEnded();
                    else listener.onProgressUpdated(progress, resid, va);
            }
    }

    private static void updateTaskCount(int count) {
        synchronized (sTaskCountListeners) {
            Iterator<TaskCountListener> iterator = sTaskCountListeners.iterator();
            while(iterator.hasNext()) {
                if(iterator.next().onUpdateTaskCount(count)) iterator.remove();
            }
        }
    }

    public static synchronized boolean hasProgressKey(String key) {
        return sProgressStates.get(key) != null;
    }

    /**
     * @return how long (in ms) the given progress key has been registered, or -1 if it is not running.
     */
    public static synchronized long getProgressAge(String key) {
        Long startedAt = sProgressStartTimes.get(key);
        if(startedAt == null) return -1;
        return System.currentTimeMillis() - startedAt;
    }

    /**
     * Watchdog: drop any progress task that has been registered for longer than maxAgeMillis.
     * A stuck download must never leave the launcher permanently "busy" and block the launch.
     * Clearing a key also notifies its listeners, so the progress UI hides the stuck row.
     * @param maxAgeMillis the age after which a task is considered stuck
     * @return the list of progress keys that were cleared (empty if none were stuck)
     */
    public static synchronized List<String> reapStaleTasks(long maxAgeMillis) {
        if(sProgressStartTimes.isEmpty()) return Collections.emptyList();
        long now = System.currentTimeMillis();
        List<String> reaped = new ArrayList<>();
        for(Map.Entry<String, Long> entry : new ArrayList<>(sProgressStartTimes.entrySet())) {
            long age = now - entry.getValue();
            if(age < maxAgeMillis) continue;
            String key = entry.getKey();
            reaped.add(key);
            Log.w("ProgressKeeper", "Watchdog: progress task \"" + key + "\" has been running for "
                    + (age / 1000) + "s without finishing; clearing it so the launcher is not blocked.");
            // Passing -1/-1 ends the record: it removes the state, decrements the task count and
            // fires onProgressEnded() on the listeners.
            submitProgress(key, -1, -1, (Object) null);
        }
        return reaped;
    }

    public static synchronized void addListener(String progressRecord, ProgressListener listener) {
        ProgressState state = sProgressStates.get(progressRecord);
        if(state != null && (state.resid != -1 || state.progress != -1)) {
            listener.onProgressStarted();
            listener.onProgressUpdated(state.progress, state.resid, state.varArg);
        }else{
            listener.onProgressEnded();
        }
        List<ProgressListener> listenerWeakReferenceList = sProgressListeners.get(progressRecord);
        if(listenerWeakReferenceList == null) sProgressListeners.put(progressRecord, (listenerWeakReferenceList = new ArrayList<>()));
        listenerWeakReferenceList.add(listener);
    }

    public static synchronized void removeListener(String progressRecord, ProgressListener listener) {
        List<ProgressListener> listenerWeakReferenceList = sProgressListeners.get(progressRecord);
        if(listenerWeakReferenceList != null) listenerWeakReferenceList.remove(listener);
    }

    public static void addTaskCountListener(TaskCountListener listener) {
        addTaskCountListener(listener, true);
    }
    public static void addTaskCountListener(TaskCountListener listener, boolean runUpdate) {
        if(runUpdate) synchronized (ProgressKeeper.class) {
            listener.onUpdateTaskCount(sProgressStates.size());
        }
        synchronized (sTaskCountListeners) {
            if(!sTaskCountListeners.contains(listener)) sTaskCountListeners.add(listener);
        }
    }
    public static void removeTaskCountListener(TaskCountListener listener) {
        synchronized (sTaskCountListeners) {
            sTaskCountListeners.remove(listener);
        }
    }

    /**
     * Waits until all tasks are done and runs the runnable, or if there were no pending process remaining
     * The runnable runs from the thread that updated the task count last, and it might be the UI thread,
     * so don't put long running processes in it
     * @param runnable the runnable to run when no tasks are remaining
     */
    public static void waitUntilDone(final Runnable runnable) {
        // If we do it the other way the listener would be removed before it was added, which will cause a listener object leak
        if(getTaskCount() == 0) {
            runnable.run();
            return;
        }
        TaskCountListener listener = taskCount -> {
            if(taskCount == 0) {
                runnable.run();
                return true;
            }
            return false;
        };
        addTaskCountListener(listener);
        startStallWatchdog();
    }

    /**
     * Backstop for {@link #waitUntilDone}: if tasks are still registered after the watchdog
     * timeout, clear the ones that look stuck so whatever was waiting on "all tasks done"
     * (typically the game launch) can proceed instead of hanging forever.
     */
    private static void startStallWatchdog() {
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(TASK_WATCHDOG_MILLIS + 5000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if(getTaskCount() == 0) return;
            List<String> reaped = reapStaleTasks(TASK_WATCHDOG_MILLIS);
            Log.w("ProgressKeeper", "Watchdog fired while waiting for tasks to finish; cleared "
                    + reaped.size() + " stuck task(s): " + reaped);
        }, "progress-stall-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static synchronized int getTaskCount() {
        return sProgressStates.size();
    }

    public static boolean hasOngoingTasks() {
        return getTaskCount() > 0;
    }
}
