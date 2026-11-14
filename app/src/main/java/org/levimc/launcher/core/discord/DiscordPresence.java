package org.levimc.launcher.core.discord;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.levimc.launcher.BuildConfig;
import org.levimc.launcher.core.minecraft.MinecraftActivity;
import org.levimc.launcher.core.minecraft.MinecraftLoadingActivity;
import org.levimc.launcher.core.minecraft.MinecraftRuntimePreparer;
import org.levimc.launcher.core.versions.GameVersion;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.activities.SplashActivity;

import java.lang.ref.WeakReference;

public final class DiscordPresence implements Application.ActivityLifecycleCallbacks {
    private static DiscordPresence instance;

    private DiscordPresence(Application application) {
        application.registerActivityLifecycleCallbacks(this);
    }

    public static void initialize(Application application) {
        instance = new DiscordPresence(application);
    }

    public static boolean isConfigured() {
        return false;
    }

    public static void refresh() {
    }

    public static void shutdown() {
    }

    private void schedule(long delay) {
    }

    private Activity chooseActivity() {
        return null;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
