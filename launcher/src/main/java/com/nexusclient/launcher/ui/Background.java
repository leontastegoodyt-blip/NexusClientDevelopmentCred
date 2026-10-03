package com.nexusclient.launcher.ui;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Background – runs a slow job (internet, files) on a background thread, then hands the result back
 * on the UI thread. Keeps the window from freezing while the job runs.
 */
public final class Background {

    private Background() {}

    /**
     * @param job     the slow work (runs in the background)
     * @param onDone  what to do with the result (runs on the UI thread)
     * @param onError what to do if it failed (runs on the UI thread); null = show an error toast
     */
    public static <T> void run(String name, Callable<T> job, Consumer<T> onDone, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return job.call();
            }
        };
        task.setOnSucceeded(event -> onDone.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            if (onError != null) {
                onError.accept(error);
            } else {
                error.printStackTrace();
                Toast.error(error.getMessage());
            }
        });
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}
