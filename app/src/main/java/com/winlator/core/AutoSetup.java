package com.winlator.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.GraphicsDrivers;
import com.winlator.xenvironment.RootFS;

import org.json.JSONObject;

/**
 * 一加13T定制：首次启动自动创建「一加13T推荐」容器（免手动配置）。
 * 等待系统文件（rootfs）安装完成后自动执行，创建一次后不再重复。
 */
public abstract class AutoSetup {
    private static final String PREF_KEY = "op13t_auto_container_created_v1";
    private static final String CONTAINER_NAME = "一加13T推荐";
    private static final int MAX_RETRIES = 300;

    public static void ensureDefaultContainer(final Fragment fragment, final ContainerManager manager, final Runnable onReady) {
        if (fragment == null || fragment.getContext() == null || manager == null) return;

        final Context context = fragment.getContext().getApplicationContext();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_KEY, false)) return;
        if (!manager.getContainers().isEmpty()) {
            prefs.edit().putBoolean(PREF_KEY, true).apply();
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] attempts = {0};
        final Runnable[] checker = new Runnable[1];

        checker[0] = new Runnable() {
            @Override
            public void run() {
                if (prefs.getBoolean(PREF_KEY, false)) return;
                if (!manager.getContainers().isEmpty()) {
                    prefs.edit().putBoolean(PREF_KEY, true).apply();
                    return;
                }
                if (!RootFS.find(context).isValid()) {
                    attempts[0]++;
                    if (attempts[0] < MAX_RETRIES) handler.postDelayed(checker[0], 2000);
                    return;
                }

                try {
                    final JSONObject data = new JSONObject();
                    data.put("name", CONTAINER_NAME);
                    data.put("screenSize", Container.DEFAULT_SCREEN_SIZE);
                    data.put("screenOrientation", Container.DEFAULT_SCREEN_ORIENTATION);
                    data.put("swapResolution", Container.DEFAULT_SWAP_RESOLUTION);
                    data.put("envVars", Container.DEFAULT_ENV_VARS);
                    data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(context));
                    data.put("dxwrapper", Container.DEFAULT_DXWRAPPER);
                    data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
                    data.put("drives", Container.DEFAULT_DRIVES);
                    data.put("hudMode", 0);
                    data.put("startupSelection", (int)Container.STARTUP_SELECTION_ESSENTIAL);
                    data.put("box64Preset", Box64Preset.PERFORMANCE);
                    data.put("box64Version", DefaultVersion.BOX64);
                    data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME + ",0");

                    manager.createContainerAsync(data, (container) -> {
                        if (container != null) {
                            prefs.edit().putBoolean(PREF_KEY, true).apply();
                            handler.post(() -> {
                                if (fragment.isAdded() && onReady != null) onReady.run();
                                Toast.makeText(context, "已自动创建『一加13T推荐』容器，可直接点击运行", Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }
                catch (Exception e) { }
            }
        };

        handler.post(checker[0]);
    }
}
