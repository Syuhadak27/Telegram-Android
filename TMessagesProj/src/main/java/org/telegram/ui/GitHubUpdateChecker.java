package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.web.HttpGetFileTask;
import org.telegram.ui.web.HttpGetTask;

import java.io.File;

/**
 * TubeGram: checks the latest GitHub release and offers to download/install its APK.
 */
public class GitHubUpdateChecker {

    private static final String REPO = "Syuhadak27/Telegram-Android";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final String RELEASES_URL = "https://github.com/" + REPO + "/releases/latest";
    private static final String PREFS_NAME = "tubegram_update";
    private static final String KEY_LAST_AUTO_CHECK = "last_auto_check";
    private static final long AUTO_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L;

    public static void check(Activity activity) {
        check(activity, false);
    }

    /** TubeGram: silently checks on app resume and only shows a dialog when an update exists. */
    public static void checkSilently(Activity activity) {
        if (activity == null || activity.isFinishing() || !UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        long last = prefs.getLong(KEY_LAST_AUTO_CHECK, 0L);
        long now = System.currentTimeMillis();
        if (now - last < AUTO_CHECK_INTERVAL_MS) {
            return;
        }
        prefs.edit().putLong(KEY_LAST_AUTO_CHECK, now).apply();
        AndroidUtilities.runOnUIThread(() -> check(activity, true), 2500);
    }

    private static void check(Activity activity, boolean silent) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new HttpGetTask(result -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (result == null) {
                if (!silent) {
                    showSimpleDialog(activity, LocaleController.getString(R.string.UpdateCheckFailedTitle), LocaleController.getString(R.string.UpdateCheckFailed));
                }
                return;
            }
            try {
                parseAndShow(activity, new JSONObject(result), silent);
            } catch (Exception e) {
                FileLog.e(e);
                if (!silent) {
                    showSimpleDialog(activity, LocaleController.getString(R.string.UpdateCheckFailedTitle), LocaleController.getString(R.string.UpdateCheckFailed));
                }
            }
        })
            .setHeader("Accept", "application/vnd.github+json")
            .setHeader("User-Agent", "TubeGram-Android")
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, API_URL);
    }

    private static void parseAndShow(Activity activity, JSONObject release, boolean silent) {
        String tag = release.optString("tag_name", "");
        String latest = tag.startsWith("v") ? tag.substring(1) : tag;
        String current = BuildVars.BUILD_VERSION_STRING;
        if (!isNewer(latest, current)) {
            if (!silent) {
                showSimpleDialog(
                    activity,
                    LocaleController.getString(R.string.CheckForUpdates),
                    LocaleController.formatString(R.string.UpdateNoAvailable, current)
                );
            }
            return;
        }

        String downloadUrl = null;
        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase().endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url", null);
                    break;
                }
            }
        }

        String body = release.optString("body", "");
        if (body.length() > 600) {
            body = body.substring(0, 600) + "\u2026";
        }
        String message = LocaleController.formatString(R.string.UpdateAvailableMessage, latest, current)
            + (TextUtils.isEmpty(body) ? "" : "\n\n" + body);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.UpdateAvailableTitle));
        builder.setMessage(message);
        if (downloadUrl != null) {
            final String finalDownloadUrl = downloadUrl;
            builder.setPositiveButton(LocaleController.getString(R.string.UpdateDownload), (dialog, which) -> download(activity, finalDownloadUrl, latest));
        }
        builder.setNeutralButton(LocaleController.getString(R.string.UpdateOpenBrowser), (dialog, which) -> Browser.openUrl(activity, RELEASES_URL));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private static void download(Activity activity, String url, String version) {
        // FileProvider (provider_paths.xml) exposes the external files dir, not getCacheDir().
        File dir = ApplicationLoader.applicationContext.getExternalFilesDir("updates");
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            showSimpleDialog(activity, LocaleController.getString(R.string.UpdateCheckFailedTitle), LocaleController.getString(R.string.UpdateCheckFailed));
            return;
        }
        File destination = new File(dir, "TubeGram-" + version + ".apk");
        if (destination.exists() && !destination.delete()) {
            FileLog.d("could not delete old update apk");
        }

        int padding = AndroidUtilities.dp(20);

        TextView statusView = new TextView(activity);
        statusView.setText(LocaleController.getString(R.string.UpdateDownloading));

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = AndroidUtilities.dp(12);

        LinearLayout contentView = new LinearLayout(activity);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setPadding(padding, padding, padding, padding);
        contentView.addView(statusView);
        contentView.addView(progressBar, barParams);

        AlertDialog progressDialog = new AlertDialog.Builder(activity)
            .setTitle(LocaleController.getString(R.string.UpdateDownload))
            .setView(contentView)
            .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            .create();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        new HttpGetFileTask(file -> {
            try {
                progressDialog.dismiss();
            } catch (Exception ignore) {
            }
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (file == null || !file.exists()) {
                showSimpleDialog(activity, LocaleController.getString(R.string.UpdateCheckFailedTitle), LocaleController.getString(R.string.UpdateCheckFailed));
                return;
            }
            install(activity, file);
        }, progress -> {
            int percent = Math.max(0, Math.min(100, (int) (progress * 100)));
            statusView.setText(LocaleController.formatString(R.string.UpdateDownloadingPercent, percent));
            progressBar.setProgress(percent);
        })
            .setDestFile(destination)
            .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    private static void install(Activity activity, File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", apk);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(activity, LocaleController.getString(R.string.UpdateCheckFailed), Toast.LENGTH_LONG).show();
        }
    }

    private static void showSimpleDialog(Activity activity, String title, String message) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(LocaleController.getString(R.string.OK), null)
            .show();
    }

    private static boolean isNewer(String latest, String current) {
        if (TextUtils.isEmpty(latest)) {
            return false;
        }
        String[] a = latest.split("\\.");
        String[] b = TextUtils.isEmpty(current) ? new String[0] : current.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = parseInt(a.length > i ? a[i] : "0");
            int y = parseInt(b.length > i ? b[i] : "0");
            if (x > y) {
                return true;
            }
            if (x < y) {
                return false;
            }
        }
        return false;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (Exception ignore) {
            return 0;
        }
    }
}
