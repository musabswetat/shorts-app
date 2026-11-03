package com.shorts.app.v2;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.documentfile.provider.DocumentFile;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "ShortsApp";
    private static final String PREFS = "ShortsPersistentLibrary";
    private static final String FOLDER_KEY = "saved_folder_uri";
    private static final String CACHE_PREFIX = "videos_";

    private final ExecutorService importExecutor =
            Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<Intent> folderPicker;
    private ActivityResultLauncher<Intent> playerLauncher;

    private volatile boolean destroyed = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        hideSystemBars();

        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(
                    new WebBridge(),
                    "AndroidBridge"
            );
        }

        folderPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK ||
                            result.getData() == null) {
                        return;
                    }

                    Intent data = result.getData();
                    Uri tree = data.getData();

                    if (tree == null) {
                        sendError("لم يتم اختيار مجلد صالح.");
                        return;
                    }

                    int flags = data.getFlags() &
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                             Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    try {
                        if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                            getContentResolver().takePersistableUriPermission(
                                    tree,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            );
                        } else {
                            getContentResolver().takePersistableUriPermission(
                                    tree,
                                    flags
                            );
                        }

                        SharedPreferences prefs =
                                getSharedPreferences(PREFS, MODE_PRIVATE);

                        prefs.edit()
                                .putString(FOLDER_KEY, tree.toString())
                                .apply();

                        beginImport(tree);

                    } catch (Exception e) {
                        Log.e(TAG, "Unable to persist folder access", e);
                        sendError(
                                "لم يتمكن أندرويد من حفظ إذن المجلد. حاول اختيار مجلد آخر."
                        );
                    }
                }
        );

        playerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    WebView web = getWebViewSafe();

                    if (web != null) {
                        String js =
                                result.getResultCode() == Activity.RESULT_FIRST_USER
                                ? "window.returnFromNativePlayer && window.returnFromNativePlayer(); window.reloadFolder && window.reloadFolder();"
                                : "window.returnFromNativePlayer && window.returnFromNativePlayer();";

                        web.evaluateJavascript(js, null);
                    }
                }
        );
    }

    private void hideSystemBars() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN |
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );

        } catch (Exception e) {
            Log.w(TAG, "Could not enable immersive mode", e);
        }
    }

    private WebView getWebViewSafe() {
        try {
            return getBridge() == null
                    ? null
                    : getBridge().getWebView();

        } catch (Exception e) {
            return null;
        }
    }

    private String cacheKey(String uri) {
        return CACHE_PREFIX + sha256(uri == null ? "" : uri);
    }

    private void beginImport(Uri tree) {
        sendImportStarted(-1);

        importExecutor.execute(() -> {
            try {
                DocumentFile root =
                        DocumentFile.fromTreeUri(this, tree);

                if (root == null ||
                        !root.isDirectory() ||
                        !hasPersistedReadPermission(tree)) {

                    sendError(
                            "انتهت صلاحية الوصول إلى المجلد. اختره مرة أخرى."
                    );
                    return;
                }

                List<DocumentFile> docs = new ArrayList<>();

                collectVideos(root, docs, 0);

                sendImportTotal(docs.size());

                JSONArray output = new JSONArray();

                Map<String, JSONObject> old =
                        readCachedMap(tree.toString());

                int failedThumbs = 0;
                int done = 0;

                // إنشاء فهرس خفيف أولاً دون نسخ ملفات الفيديو.
                for (DocumentFile doc : docs) {
                    if (destroyed) return;

                    Uri uri = doc.getUri();
                    String key = uri.toString();

                    String name = doc.getName() == null
                            ? "فيديو"
                            : doc.getName();

                    long size = doc.length();
                    long modified = doc.lastModified();

                    JSONObject item = new JSONObject();

                    item.put("id", key);
                    item.put("uri", key);
                    item.put("name", name);
                    item.put("size", size);
                    item.put("modified", modified);

                    JSONObject previous = old.get(key);

                    String thumb = null;

                    if (previous != null &&
                            previous.optLong("size", -1) == size &&
                            previous.optLong("modified", -1) == modified) {

                        String savedThumb =
                                previous.optString("thumbnail", "");

                        if (!savedThumb.isEmpty() &&
                                new File(savedThumb).isFile()) {

                            thumb = savedThumb;
                        }
                    }

                    if (thumb != null) {
                        item.put("thumbnail", thumb);
                    }

                    output.put(item);
                }

                saveCache(tree.toString(), output);

                // تجهيز الصور المصغرة واحدة تلو الأخرى.
                for (int i = 0; i < docs.size(); i++) {
                    if (destroyed) return;

                    DocumentFile doc = docs.get(i);
                    JSONObject item = output.optJSONObject(i);

                    if (item == null) {
                        done++;
                        continue;
                    }

                    String id = item.optString("id", "");
                    String thumb = item.optString("thumbnail", "");

                    if (thumb.isEmpty() ||
                            !new File(thumb).isFile()) {

                        String generated =
                                createThumbnail(doc.getUri(), id);

                        if (generated != null) {
                            item.put("thumbnail", generated);
                            sendThumbnailReady(id, generated);

                        } else {
                            failedThumbs++;
                        }
                    }

                    done++;

                    saveCache(tree.toString(), output);

                    sendVideoImported(
                            item,
                            done,
                            docs.size()
                    );
                }

                sendImportFinished(
                        docs.size(),
                        failedThumbs
                );

            } catch (Exception e) {
                Log.e(TAG, "Folder import failed", e);

                sendError(
                        "حدث خطأ أثناء فهرسة المجلد: " +
                        safeMessage(e)
                );
            }
        });
    }

    private void collectVideos(
            DocumentFile folder,
            List<DocumentFile> out,
            int depth
    ) {
        if (depth > 12) return;

        DocumentFile[] children;

        try {
            children = folder.listFiles();

        } catch (Exception e) {
            Log.w(TAG, "Folder listing failed", e);
            return;
        }

        for (DocumentFile child : children) {
            if (child == null) continue;

            try {
                if (child.isDirectory()) {
                    collectVideos(child, out, depth + 1);

                } else if (child.isFile() &&
                        isVideo(child.getName(), child.getType())) {

                    out.add(child);
                }

            } catch (Exception e) {
                Log.w(TAG, "Skipping unreadable item", e);
            }
        }
    }

    private boolean isVideo(String name, String mime) {
        if (mime != null &&
                mime.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return true;
        }

        if (name == null) return false;

        String n = name.toLowerCase(Locale.ROOT);

        String[] extensions = {
                ".mp4", ".m4v", ".3gp", ".webm",
                ".mkv", ".mov", ".avi", ".mpeg",
                ".mpg", ".ts", ".m2ts", ".wmv",
                ".flv", ".ogv", ".vob"
        };

        for (String ext : extensions) {
            if (n.endsWith(ext)) return true;
        }

        return false;
    }

    private String createThumbnail(Uri uri, String id) {
        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        Bitmap frame = null;

        try {
            retriever.setDataSource(this, uri);

            frame = retriever.getFrameAtTime(
                    1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            );

            if (frame == null) {
                frame = retriever.getFrameAtTime();
            }

            if (frame == null) return null;

            int max = 720;
            int width = frame.getWidth();
            int height = frame.getHeight();

            float scale = Math.min(
                    1f,
                    max / (float) Math.max(width, height)
            );

            Bitmap scaled = frame;

            if (scale < 1f) {
                scaled = Bitmap.createScaledBitmap(
                        frame,
                        Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)),
                        true
                );

                if (scaled != frame) {
                    frame.recycle();
                }
            }

            File dir = new File(
                    getFilesDir(),
                    "shorts-thumbnails"
            );

            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }

            File target = new File(
                    dir,
                    sha256(id) + ".jpg"
            );

            try (FileOutputStream stream =
                         new FileOutputStream(target)) {

                if (!scaled.compress(
                        Bitmap.CompressFormat.JPEG,
                        78,
                        stream
                )) {
                    return null;
                }

                stream.flush();
            }

            if (!scaled.isRecycled()) {
                scaled.recycle();
            }

            return target.getAbsolutePath();

        } catch (Exception e) {
            Log.w(TAG, "Thumbnail unavailable for " + uri, e);
            return null;

        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}

            if (frame != null && !frame.isRecycled()) {
                frame.recycle();
            }
        }
    }

    private Map<String, JSONObject> readCachedMap(String folder) {
        Map<String, JSONObject> map = new HashMap<>();

        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(cacheKey(folder), "[]");

            JSONArray array = new JSONArray(raw);

            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);

                if (item != null) {
                    map.put(item.optString("id", ""), item);
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Cache read failed", e);
        }

        return map;
    }

    private void saveCache(String folder, JSONArray array) {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(cacheKey(folder), array.toString())
                .apply();
    }

    private boolean hasPersistedReadPermission(Uri uri) {
        try {
            for (android.content.UriPermission permission :
                    getContentResolver().getPersistedUriPermissions()) {

                if (permission.getUri().equals(uri) &&
                        permission.isReadPermission()) {
                    return true;
                }
            }

        } catch (Exception e) {
            Log.w(TAG, "Permission check failed", e);
        }

        return false;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(
                            value.getBytes(
                                    java.nio.charset.StandardCharsets.UTF_8
                            )
                    );

            StringBuilder out = new StringBuilder();

            for (byte b : digest) {
                out.append(
                        String.format(Locale.US, "%02x", b)
                );
            }

            return out.toString();

        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null
                ? "خطأ غير معروف"
                : e.getMessage();
    }

    private void callJs(String script) {
        runOnUiThread(() -> {
            WebView web = getWebViewSafe();

            if (!destroyed && web != null) {
                web.evaluateJavascript(script, null);
            }
        });
    }

    private void sendImportStarted(int total) {
        callJs(
                "window.onImportStarted && window.onImportStarted(" +
                        total + ");"
        );
    }

    private void sendImportTotal(int total) {
        callJs(
                "window.onImportTotal && window.onImportTotal(" +
                        total + ");"
        );
    }

    private void sendCachedVideos(JSONArray list) {
        callJs(
                "window.onCachedVideos && window.onCachedVideos(" +
                        list.toString() + ");"
        );
    }

    private void sendVideoImported(
            JSONObject item,
            int done,
            int total
    ) {
        callJs(
                "window.onVideoImported && window.onVideoImported(" +
                        item.toString() + "," +
                        done + "," +
                        total + ");"
        );
    }

    private void sendThumbnailReady(String id, String path) {
        callJs(
                "window.onVideoThumbnailReady && window.onVideoThumbnailReady(" +
                        JSONObject.quote(id) + "," +
                        JSONObject.quote(path) + ");"
        );
    }

    private void sendImportFinished(int total, int failed) {
        callJs(
                "window.onImportFinished && window.onImportFinished(" +
                        total + "," +
                        failed + ");"
        );
    }

    private void sendError(String message) {
        callJs(
                "window.onFolderError && window.onFolderError(" +
                        JSONObject.quote(message) + ");"
        );
    }

    public class WebBridge {

        @JavascriptInterface
        public void selectFolder() {
            runOnUiThread(() -> {
                try {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    );

                    folderPicker.launch(intent);

                } catch (Exception e) {
                    Log.e(TAG, "Folder picker failed", e);
                    sendError("تعذر فتح نافذة اختيار المجلد.");
                }
            });
        }

        @JavascriptInterface
        public void checkSavedFolder() {
            SharedPreferences prefs =
                    getSharedPreferences(PREFS, MODE_PRIVATE);

            String saved = prefs.getString(FOLDER_KEY, "");

            // محاولة استعادة المجلد المحفوظ في الإصدارات السابقة.
            if (saved.isEmpty()) {
                saved = getSharedPreferences(
                        "ShortsSettings",
                        MODE_PRIVATE
                ).getString("saved_folder", "");

                if (saved.isEmpty()) {
                    saved = getSharedPreferences(
                            "ShortsPrefs",
                            MODE_PRIVATE
                    ).getString("savedFolderUri", "");
                }

                if (!saved.isEmpty()) {
                    prefs.edit()
                            .putString(FOLDER_KEY, saved)
                            .apply();
                }
            }

            if (saved.isEmpty()) return;

            try {
                Uri uri = Uri.parse(saved);

                if (!hasPersistedReadPermission(uri)) {
                    sendError(
                            "صلاحية المجلد غير محفوظة. اختر المجلد مرة واحدة مجدداً."
                    );
                    return;
                }

                String raw = prefs.getString(
                        cacheKey(saved),
                        "[]"
                );

                JSONArray cached = new JSONArray(raw);

                if (cached.length() > 0) {
                    sendCachedVideos(cached);
                }

                beginImport(uri);

            } catch (Exception e) {
                Log.e(TAG, "Saved folder restore failed", e);

                sendError(
                        "تعذر استعادة المجلد المحفوظ. اختره مرة أخرى."
                );
            }
        }

        @JavascriptInterface
        public void openPlayer(String videosJson, int startIndex) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(
                            MainActivity.this,
                            PlayerActivity.class
                    );

                    intent.putExtra(
                            PlayerActivity.EXTRA_VIDEOS,
                            videosJson
                    );

                    intent.putExtra(
                            PlayerActivity.EXTRA_START_INDEX,
                            startIndex
                    );

                    playerLauncher.launch(intent);

                } catch (Exception e) {
                    Log.e(TAG, "Player launch failed", e);
                    sendError("تعذر تشغيل الفيديو.");
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        importExecutor.shutdownNow();
        super.onDestroy();
    }
}
