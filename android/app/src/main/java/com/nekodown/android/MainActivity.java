package com.nekodown.android;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.nekodown.android.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment queueFragment = new QueueFragment();
    private final Fragment historyFragment = new HistoryFragment();
    private Fragment activeFragment = homeFragment;

    public QueueFragment getQueueFragment() {
        return (QueueFragment) queueFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, historyFragment, "3").hide(historyFragment)
                    .add(R.id.fragment_container, queueFragment, "2").hide(queueFragment)
                    .add(R.id.fragment_container, homeFragment, "1").commit();
        }

        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = homeFragment;
            } else if (itemId == R.id.nav_queue) {
                selectedFragment = queueFragment;
            } else if (itemId == R.id.nav_history) {
                selectedFragment = historyFragment;
            }
            if (selectedFragment != null && selectedFragment != activeFragment) {
                getSupportFragmentManager().beginTransaction()
                        .hide(activeFragment)
                        .show(selectedFragment)
                        .commit();
                activeFragment = selectedFragment;
            }
            return true;
        });

        checkForAndroidUpdate();
    }

    private void checkForAndroidUpdate() {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL("https://api.github.com/repos/CutMyThrOat/nekodown-update/releases?per_page=20");
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() != 200) return;
                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }
                JSONArray releases = new JSONArray(body.toString());
                JSONObject newest = null;
                int newestCode = versionCode(BuildConfig.VERSION_NAME);
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject release = releases.optJSONObject(i);
                    if (release == null || release.optBoolean("draft") || release.optBoolean("prerelease")) continue;
                    String tag = release.optString("tag_name");
                    if (!tag.startsWith("android-v")) continue;
                    int candidate = versionCode(tag.substring("android-v".length()));
                    if (candidate > newestCode) { newestCode = candidate; newest = release; }
                }
                if (newest == null) return;
                String downloadUrl = "";
                JSONArray assets = newest.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name").endsWith(".apk")) { downloadUrl = asset.optString("browser_download_url"); break; }
                }
                if (downloadUrl.isEmpty()) return;
                final String tag = newest.optString("tag_name");
                final String finalDownloadUrl = downloadUrl;
                new Handler(Looper.getMainLooper()).post(() -> showUpdateDialog(tag, finalDownloadUrl));
            } catch (Exception ignored) {
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private int versionCode(String value) {
        try {
            String[] parts = value.replace("v", "").split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return major * 10000 + minor * 100 + patch;
        } catch (Exception ignored) { return 0; }
    }

    private void showUpdateDialog(String tag, String downloadUrl) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Actualización disponible")
                .setMessage("Hay una nueva versión de NekoDown (" + tag.replace("android-v", "") + ").")
                .setNegativeButton("Más tarde", null)
                .setPositiveButton("Descargar", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))))
                .show();
    }
}
