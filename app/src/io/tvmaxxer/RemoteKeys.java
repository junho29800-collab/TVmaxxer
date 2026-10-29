package io.tvmaxxer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.InputDevice;
import android.view.KeyEvent;

/**
 * The remote's app buttons on TCL TVs. The stock home screen opens the apps for Netflix, YouTube and Prime (and TCL
 * Channel); with it off, the keys still arrive, so we open the app the person picked (Settings > Remote Buttons).
 * Disney and Globoplay are opened by the system itself, so they are not listed. The YouTube button (BUTTON_3) never
 * reaches an app's window; only the accessibility service's key filter (RemoteKeyService) sees it.
 * On Vestel TVs and Formovie (Xiaomi) projectors only the Netflix button is ours: the system opens Netflix itself (a
 * global key, after the filter).
 * On KTC TVs (NextStar) only the Tabii button is ours, a global key too: the TV's hot-key receiver opens Tabii with it.
 * On Sony TVs, the app buttons (and the Google Play button of older remotes) are global keys for Sony's own receiver;
 * the filter sees them first, the same way.
 * On Xiaomi TVs whose remote has no Home button, its Mi button is BUTTON_4, a global key that Xiaomi's GlobalKeyReceiver
 * (partnercustomizer) turns into PatchWall. The filter sees it first, and by default it opens the home screen.
 * On Xiaomi TVs the YouTube button is ours too: BUTTON_3, a global key for the stock home screen (launcherx), which
 * TVmaxxer turns off. So is the PatchWall button (BUTTON_5, which that receiver would open PatchWall with); Netflix
 * goes to the receiver, which stays on. Their remote sends one press as a burst of presses: the app opens once.
 * On Xiaomi TVs sold in Russia, the Okko, VK, Rutube, KION, Wink and IVI buttons are global keys too, for Xiaomi's
 * hot-key receiver (partnercustomizer's GlobalKeyReceiver).
 * On Hisense TVs, the Prime Video, YouTube, Disney+, All Apps, Settings (gear) and profile buttons go to Google TV's
 * home app, which TVmaxxer mode turns off: without the filter they do nothing. Netflix stays with the system.
 */
final class RemoteKeys {
    private RemoteKeys() {}

    /** Some TCL remotes (BT_RC833) send TCL's own YouTube key instead of BUTTON_3: the same button. */
    private static final int KEY_YOUTUBE_TCL = 4065;

    /**
     * The key codes are the brand's own: on other brands they can be other buttons, so nothing here runs there.
     * Vestel builds TVs sold under other brands (F&U...), so BRAND varies: check the maker too.
     */
    static final boolean TCL = is("TCL"), VESTEL = is("Vestel"), KTC = is("KTC"), SONY = is("Sony"), FORMOVIE = is("Formovie"),
            HISENSE = is("Hisense");
    /**
     * Xiaomi TVs whose remote has a Home button and hands every app button to Xiaomi's receiver, which stays on
     * (MiTV-AXSO2, Amlogic, Android TV 11 tested): only the PatchWall button (BUTTON_5) is ours, left to the TV until an
     * app is picked. By model name, since the other buttons' codes were not checked on this remote.
     */
    static final boolean XIAOMI_PATCHWALL_ONLY = is("Xiaomi") && Build.MODEL.equals("MiTV-AXSO2");
    /**
     * Xiaomi TVs (models MiTV-M…; MiTV-MSSP3, MediaTek, Android TV 9 tested). Not the Mi Box or Mi TV Stick, the slowest
     * devices we run on, whose remotes have a Home button; Formovie's Xiaomi projectors have their own remote.
     */
    static final boolean XIAOMI = is("Xiaomi") && !FORMOVIE && (Build.MODEL.startsWith("MiTV-M") || XIAOMI_PATCHWALL_ONLY);
    /** Brands whose Netflix button is STEM_1, a global key (MediaTek): only that button is ours. */
    static final boolean STEM_NETFLIX = VESTEL || FORMOVIE;
    /**
     * With a key filter on, TCL's Android 11 changes the volume twice for one press, and a held key gets stuck (#325).
     * There the service stays off: the buttons work on our home screen, whose window gets them (BUTTON_3 never does).
     */
    static final boolean FILTER_SAFE = !(TCL && Build.VERSION.SDK_INT == Build.VERSION_CODES.R);

    /** Xiaomi's remote sends one press as a burst of separate presses: a press this soon after the last one is the same. */
    private static final long BURST_MS = 1000;
    private static int lastKey;
    private static long lastDown;

    static boolean supported() {
        return TCL || STEM_NETFLIX || KTC || SONY || XIAOMI || HISENSE;
    }

    /** Whether holding Home can open the settings: the key filter has to be safe on this TV, or nothing sees the key. */
    static boolean homeHoldAvailable() {
        return supported() && FILTER_SAFE;
    }

    /**
     * The key service is on only where the filter is safe, and while it has work: a button that opens something, or the
     * settings on a held Home (Home Settings).
     */
    static boolean serviceWanted() {
        if (!supported() || !FILTER_SAFE) return false;
        if (Prefs.homeHold()) return true;
        for (int key : Prefs.KEYS) if (!Prefs.keyApp(key).isEmpty()) return true;
        return false;
    }

    /** How long Home is held before the settings open: the key's first repeat, about half a second in. */
    private static boolean homeHeld(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.KEYCODE_HOME && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() > 0;
    }

    private static boolean is(String maker) {
        return maker.equalsIgnoreCase(Build.MANUFACTURER) || maker.equalsIgnoreCase(Build.BRAND);
    }

    /** Opens the app for this key on its first press. True if the key is ours and in use (it is then consumed). */
    static boolean onKey(Context c, KeyEvent e) {
        // Holding Home opens the settings (Home Settings), from inside any app. Before `supported()`: on our own home
        // screen the window gets the key on any TV, and no filter is needed for it.
        if (Prefs.homeHoldInKeys() && homeHeld(e)) {
            AppLog.w("home held -> settings");
            c.startActivity(new Intent(c, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        if (!supported()) return false;
        // BUTTON_3 is also a game controller's button: keys from a gamepad or a joystick are left alone.
        int source = e.getSource();
        if ((source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) return false;
        int keyCode = e.getKeyCode() == KEY_YOUTUBE_TCL ? Prefs.KEY_YOUTUBE : e.getKeyCode();
        // Sony sends BUTTON_9 for five app buttons (BUTTON_4 for Netflix, BUTTON_8 for Google Play): only the scan code
        // tells them apart.
        if (SONY && (keyCode == KeyEvent.KEYCODE_BUTTON_4 || keyCode == KeyEvent.KEYCODE_BUTTON_8
                || keyCode == KeyEvent.KEYCODE_BUTTON_9)) {
            keyCode = Prefs.SONY_KEY + e.getScanCode();
        }
        boolean ours = false;
        for (int key : Prefs.KEYS) ours |= key == keyCode;
        if (!ours) return false;
        String pkg = Prefs.keyApp(keyCode);
        if (pkg.isEmpty()) return false;   // "Nothing": the key goes on as if we weren't here
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            // Only on Xiaomi, where the bursts are: everywhere else a second press opens the app again, as before.
            boolean burst = XIAOMI && keyCode == lastKey && e.getEventTime() - lastDown < BURST_MS;
            lastKey = keyCode;
            lastDown = e.getEventTime();
            if (burst) return true;
            // Our own package means the home screen in use (Xiaomi's Mi button): ours, or the TV's own after the
            // watchdog handed it back. Not the settings app our launcher icon opens.
            Intent intent = pkg.equals(HomeMode.PKG) || pkg.equals(Prefs.ALL_APPS)
                    ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    : launchIntent(c.getPackageManager(), pkg);
            if (pkg.equals(Prefs.ALL_APPS)) intent.putExtra(HomeActivity.EXTRA_ALL_APPS, true);
            AppLog.w("remote key " + e.getKeyCode() + " -> " + pkg + (intent == null ? " (not installed)" : ""));
            if (intent != null) c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        return true;
    }

    /** The app's TV launcher, or its plain one (kept here, not in HomeApps, so the key process stays small). */
    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        return intent != null ? intent : pm.getLaunchIntentForPackage(pkg);
    }
}
