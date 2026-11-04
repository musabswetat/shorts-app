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
import android.view.View;

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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "ShortsApp";
    private static final String PREFS = "ShortsPersistentLibrary";
    private static final String FOLDER_KEY = "saved_folder_uri";
    private final ExecutorService scanExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> folderPicker;
    private ActivityResultLauncher<Intent> playerLauncher;
    private LibraryDbHelper libraryDb;
    private volatile boolean destroyed = false;
    private volatile boolean scanning = false;
    private volatile boolean pickerInProgress = false;
    private volatile boolean initialRestoreDone = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        libraryDb = new LibraryDbHelper(this);
        hideSystemBars();

        folderPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            pickerInProgress = false;
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            Intent data = result.getData();
            Uri tree = data.getData();
            if (tree == null) { sendError("لم يتم اختيار مجلد صالح."); return; }
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(tree, flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(FOLDER_KEY, tree.toString()).apply();
                initialRestoreDone = true;
                // Move to the library immediately; scanning continues on a worker thread.
                sendFolderSelected();
                sendCachedVideos(libraryDb.getVideos(tree.toString()));
                scanFolder(tree, true);
            } catch (Exception e) {
                Log.e(TAG, "Unable to save folder permission", e);
                sendError("تعذر حفظ صلاحية المجلد. اختر مجلداً آخر ثم حاول مجدداً.");
            }
        });

        playerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            WebView web = getWebViewSafe();
            if (web != null) {
                String js = result.getResultCode() == Activity.RESULT_FIRST_USER
                    ? "window.returnFromNativePlayer && window.returnFromNativePlayer(); window.reloadFolder && window.reloadFolder();"
                    : "window.returnFromNativePlayer && window.returnFromNativePlayer();";
                web.evaluateJavascript(js, null);
            }
        });

        if (getBridge() != null && getBridge().getWebView() != null) {
            getBridge().getWebView().addJavascriptInterface(new WebBridge(), "AndroidBridge");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (initialRestoreDone && !pickerInProgress && !scanning) {
            String saved = getSavedFolder();
            if (!saved.isEmpty()) scanFolder(Uri.parse(saved), false);
        }
    }

    private void hideSystemBars() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } catch (Exception e) { Log.w(TAG, "Immersive mode unavailable", e); }
    }

    private WebView getWebViewSafe() {
        try { return getBridge() == null ? null : getBridge().getWebView(); }
        catch (Exception e) { return null; }
    }

    private String getSavedFolder() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(FOLDER_KEY, "");
        if (saved.isEmpty()) {
            saved = getSharedPreferences("ShortsSettings", MODE_PRIVATE).getString("saved_folder", "");
            if (saved.isEmpty()) saved = getSharedPreferences("ShortsPrefs", MODE_PRIVATE).getString("savedFolderUri", "");
            if (!saved.isEmpty()) prefs.edit().putString(FOLDER_KEY, saved).apply();
        }
        return saved;
    }

    private void migrateLegacyCache(String folderUri) {
        try {
            String key = "videos_" + sha256(folderUri);
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "[]");
            JSONArray old = new JSONArray(raw);
            for (int i = 0; i < old.length(); i++) {
                JSONObject item = old.optJSONObject(i);
                if (item != null && !item.optString("id", "").isEmpty()) {
                    libraryDb.putVideo(folderUri, item);
                }
            }
        } catch (Exception e) { Log.w(TAG, "Legacy library migration skipped", e); }
    }

    private void scanFolder(Uri tree, boolean showProgress) {
        if (destroyed || scanning) return;
        scanning = true;
        if (showProgress) sendImportStarted(-1);
        scanExecutor.execute(() -> {
            try {
                if (!hasPersistedReadPermission(tree)) {
                    sendError("صلاحية الوصول إلى المجلد غير محفوظة. اختر المجلد مرة واحدة مجدداً.");
                    return;
                }
                DocumentFile root = DocumentFile.fromTreeUri(this, tree);
                if (root == null || !root.isDirectory()) {
                    sendError("المجلد المحفوظ لم يعد متاحاً. اختر مجلداً جديداً.");
                    return;
                }
                List<DocumentFile> docs = new ArrayList<>();
                collectVideos(root, docs, 0);
                sendImportTotal(docs.size());
                Set<String> found = new HashSet<>();
                int failedThumbs = 0;
                int done = 0;

                for (DocumentFile doc : docs) {
                    if (destroyed) return;
                    Uri uri = doc.getUri();
                    String id = uri.toString();
                    found.add(id);
                    long size = doc.length();
                    long modified = doc.lastModified();
                    JSONObject old = libraryDb.getVideo(id);
                    boolean changed = old == null || old.optLong("size", -1) != size || old.optLong("modified", -1) != modified;
                    JSONObject item = new JSONObject();
                    item.put("id", id);
                    item.put("uri", id);
                    item.put("name", doc.getName() == null ? "فيديو" : doc.getName());
                    item.put("size", size);
                    item.put("modified", modified);

                    String thumbnail = "";
                    if (!changed && old != null) thumbnail = old.optString("thumbnail", "");
                    if (thumbnail.isEmpty() || !new File(thumbnail).isFile()) {
                        String generated = createThumbnail(uri, id);
                        if (generated != null) thumbnail = generated;
                        else failedThumbs++;
                    }
                    if (!thumbnail.isEmpty()) item.put("thumbnail", thumbnail);
                    libraryDb.putVideo(tree.toString(), item);
                    done++;
                    sendVideoImported(item, done, docs.size());
                }

                // Remove database entries for files that were deleted from the chosen folder.
                libraryDb.deleteMissing(tree.toString(), found);
                sendImportFinished(docs.size(), failedThumbs);
            } catch (Exception e) {
                Log.e(TAG, "Folder scan failed", e);
                sendError("حدث خطأ أثناء تحديث المكتبة: " + safeMessage(e));
            } finally {
                scanning = false;
            }
        });
    }

    private void collectVideos(DocumentFile folder, List<DocumentFile> out, int depth) {
        if (depth > 12) return;
        DocumentFile[] children;
        try { children = folder.listFiles(); }
        catch (Exception e) { Log.w(TAG, "Cannot list folder", e); return; }
        for (DocumentFile child : children) {
            if (child == null) continue;
            try {
                if (child.isDirectory()) collectVideos(child, out, depth + 1);
                else if (child.isFile() && isVideo(child.getName(), child.getType())) out.add(child);
            } catch (Exception e) { Log.w(TAG, "Skipping inaccessible file", e); }
        }
    }

    private boolean isVideo(String name, String mime) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("video/")) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        String[] extensions = {".mp4", ".m4v", ".3gp", ".webm", ".mkv", ".mov", ".avi", ".mpeg", ".mpg", ".ts", ".m2ts", ".wmv", ".flv", ".ogv", ".vob"};
        for (String ext : extensions) if (n.endsWith(ext)) return true;
        return false;
    }

    private String createThumbnail(Uri uri, String id) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        Bitmap frame = null;
        Bitmap scaled = null;
        try {
            retriever.setDataSource(this, uri);
            frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) frame = retriever.getFrameAtTime();
            if (frame == null) return null;
            int max = 720;
            float factor = Math.min(1f, max / (float)Math.max(frame.getWidth(), frame.getHeight()));
            scaled = factor < 1f
                ? Bitmap.createScaledBitmap(frame, Math.max(1, (int)(frame.getWidth() * factor)), Math.max(1, (int)(frame.getHeight() * factor)), true)
                : frame;
            File dir = new File(getFilesDir(), "shorts-thumbnails");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File target = new File(dir, sha256(id) + ".jpg");
            try (FileOutputStream stream = new FileOutputStream(target)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 78, stream)) return null;
                stream.flush();
            }
            return target.getAbsolutePath();
        } catch (Exception e) {
            Log.w(TAG, "Thumbnail unavailable: " + uri, e);
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
            if (scaled != null && scaled != frame && !scaled.isRecycled()) scaled.recycle();
            if (frame != null && !frame.isRecycled()) frame.recycle();
        }
    }

    private boolean hasPersistedReadPermission(Uri uri) {
        try {
            for (android.content.UriPermission p : getContentResolver().getPersistedUriPermissions()) {
                if (p.getUri().equals(uri) && p.isReadPermission()) return true;
            }
        } catch (Exception e) { Log.w(TAG, "Permission check failed", e); }
        return false;
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.US, "%02x", b));
            return result.toString();
        } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
    }

    private String safeMessage(Exception e) { return e.getMessage() == null ? "خطأ غير معروف" : e.getMessage(); }

    private void callJs(String js) {
        runOnUiThread(() -> {
            WebView web = getWebViewSafe();
            if (!destroyed && web != null) web.evaluateJavascript(js, null);
        });
    }
    private void sendFolderSelected() { callJs("window.onFolderSelected && window.onFolderSelected();"); }
    private void sendImportStarted(int total) { callJs("window.onImportStarted && window.onImportStarted(" + total + ");"); }
    private void sendImportTotal(int total) { callJs("window.onImportTotal && window.onImportTotal(" + total + ");"); }
    private void sendCachedVideos(JSONArray list) { callJs("window.onCachedVideos && window.onCachedVideos(" + list.toString() + ");"); }
    private void sendVideoImported(JSONObject item, int done, int total) { callJs("window.onVideoImported && window.onVideoImported(" + item.toString() + "," + done + "," + total + ");"); }
    private void sendImportFinished(int total, int failed) { callJs("window.onImportFinished && window.onImportFinished(" + total + "," + failed + ");"); }
    private void sendError(String message) { callJs("window.onFolderError && window.onFolderError(" + JSONObject.quote(message) + ");"); }

    public class WebBridge {
        @JavascriptInterface public void selectFolder() {
            runOnUiThread(() -> {
                try {
                    pickerInProgress = true;
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    folderPicker.launch(intent);
                } catch (Exception e) {
                    pickerInProgress = false;
                    Log.e(TAG, "Folder picker launch failed", e);
                    sendError("تعذر فتح نافذة اختيار المجلد.");
                }
            });
        }

        @JavascriptInterface public void checkSavedFolder() {
            runOnUiThread(() -> {
                String saved = getSavedFolder();
                if (saved.isEmpty()) return;
                try {
                    Uri uri = Uri.parse(saved);
                    if (!hasPersistedReadPermission(uri)) {
                        sendError("صلاحية المجلد غير محفوظة. اختر المجلد مرة واحدة مجدداً.");
                        return;
                    }
                    initialRestoreDone = true;
                    migrateLegacyCache(saved);
                    sendCachedVideos(libraryDb.getVideos(saved));
                    scanFolder(uri, true);
                } catch (Exception e) {
                    Log.e(TAG, "Restore failed", e);
                    sendError("تعذر استعادة المكتبة المحفوظة. اختر المجلد مجدداً.");
                }
            });
        }

        @JavascriptInterface public void openPlayer(String videosJson, int startIndex) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
                    intent.putExtra(PlayerActivity.EXTRA_VIDEOS, videosJson);
                    intent.putExtra(PlayerActivity.EXTRA_START_INDEX, startIndex);
                    playerLauncher.launch(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Player launch failed", e);
                    sendError("تعذر تشغيل الفيديو.");
                }
            });
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        scanExecutor.shutdownNow();
        if (libraryDb != null) libraryDb.close();
        super.onDestroy();
    }
}
