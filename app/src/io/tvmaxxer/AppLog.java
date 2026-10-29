package io.tvmaxxer;

import android.util.Log;

/** The app's log (adb logcat -s TVmaxxer). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("TVmaxxer", message);
    }
}
