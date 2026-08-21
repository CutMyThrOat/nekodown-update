package com.nekodown.android;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NekoApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        installCrashReporter();
    }

    /**
     * Keeps a local diagnostic report instead of silently losing the crash.
     * No credentials or automatic upload are embedded in the APK.
     */
    private void installCrashReporter() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                File dir = new File(getExternalFilesDir(null), "diagnostics");
                if (!dir.exists()) dir.mkdirs();
                String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
                File report = new File(dir, "crash-" + stamp + ".txt");
                try (PrintWriter out = new PrintWriter(new FileOutputStream(report))) {
                    out.println("NekoDown Android crash report");
                    out.println("version=1.5.1");
                    out.println("manufacturer=" + Build.MANUFACTURER);
                    out.println("model=" + Build.MODEL);
                    out.println("sdk=" + Build.VERSION.SDK_INT);
                    out.println();
                    error.printStackTrace(out);
                }
                getSharedPreferences("nekodown_diagnostics", Context.MODE_PRIVATE)
                    .edit().putString("last_report", report.getAbsolutePath()).apply();
                Log.e("NekoDown", "Crash report saved at " + report, error);
            } catch (Exception reportError) {
                Log.e("NekoDown", "Could not save crash report", reportError);
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
