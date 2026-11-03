package com.shorts.app.v2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.provider.DocumentsContract;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.BridgeActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends BridgeActivity {

    private static final String TAG = "ShortsApp";
    private static final int REQUEST_FOLDER = 7314;

    private static final String PREFS = "ShortsSettings";
    private static final String FOLDER_KEY = "saved_folder";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        applyImmersiveMode();

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        if (getBridge() != null &&
                getBridge().getWebView() != null) {

            getBridge().getWebView().addJavascriptInterface(
                    new AndroidBridge(),
                    "AndroidBridge"
            );
        }
    }

    private void applyImmersiveMode() {

        WindowCompat.setDecorFitsSystemWindows(
                getWindow(),
                false
        );

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(
                        getWindow(),
                        getWindow().getDecorView()
                );

        controller.hide(
                WindowInsetsCompat.Type.systemBars()
        );

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void selectFolder() {

            runOnUiThread(() -> {

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_OPEN_DOCUMENT_TREE
                    );

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    );

                    startActivityForResult(
                            intent,
                            REQUEST_FOLDER
                    );

                } catch (Exception e) {

                    Log.e(TAG, "Folder picker failed", e);

                    notifyError(
                            "تعذر فتح اختيار المجلد."
                    );
                }
            });
        }

        @JavascriptInterface
        public void checkSavedFolder() {

            String saved = getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).getString(FOLDER_KEY, null);

            if (saved == null || saved.isEmpty()) {
                notifyError("اختر مجلد الفيديوهات للبدء.");
                return;
            }

            try {
                scanFolder(Uri.parse(saved));

            } catch (Exception e) {

                Log.e(TAG, "Saved folder error", e);

                notifyError(
                        "تعذر الوصول إلى المجلد المحفوظ. اختره مجدداً."
                );
            }
        }

        @JavascriptInterface
        public void openPlayer(String videosJson, int startIndex) {

            runOnUiThread(() -> {

                try {
                    JSONArray array = new JSONArray(videosJson);

                    ArrayList<String> paths = new ArrayList<>();
                    ArrayList<String> names = new ArrayList<>();

                    for (int i = 0; i < array.length(); i++) {

                        JSONObject item = array.getJSONObject(i);

                        paths.add(item.getString("path"));

                        names.add(
                                item.optString("name", "فيديو")
                        );
                    }

                    if (paths.isEmpty()) {
                        return;
                    }

                    Intent intent = new Intent(
                            MainActivity.this,
                            PlayerActivity.class
                    );

                    intent.putStringArrayListExtra(
                            PlayerActivity.EXTRA_PATHS,
                            paths
                    );

                    intent.putStringArrayListExtra(
                            PlayerActivity.EXTRA_NAMES,
                            names
                    );

                    intent.putExtra(
                            PlayerActivity.EXTRA_INDEX,
                            Math.max(
                                    0,
                                    Math.min(startIndex, paths.size() - 1)
                            )
                    );

                    startActivity(intent);

                } catch (Exception e) {

                    Log.e(TAG, "Open player failed", e);

                    notifyError("تعذر فتح مشغل الفيديو.");
                }
            });
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != REQUEST_FOLDER) {
            return;
        }

        if (resultCode != RESULT_OK || data == null) {
            notifyError("لم يتم اختيار مجلد.");
            return;
        }

        Uri treeUri = data.getData();

        if (treeUri == null) {
            notifyError("تعذر الحصول على المجلد.");
            return;
        }

        try {

            int flags = data.getFlags() &
                    (
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    );

            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    flags
            );

            getSharedPreferences(
                    PREFS,
                    MODE_PRIVATE
            ).edit()
                    .putString(FOLDER_KEY, treeUri.toString())
                    .apply();

            scanFolder(treeUri);

        } catch (Exception e) {

            Log.e(TAG, "Folder permission error", e);

            notifyError(
                    "تعذر حفظ صلاحية المجلد. اختره مرة أخرى."
            );
        }
    }

    private void scanFolder(Uri treeUri) {

        executor.execute(() -> {

            try {

                File videos = new File(
                        getFilesDir(),
                        "shorts-videos"
                );

                File thumbs = new File(
                        getFilesDir(),
                        "shorts-thumbnails"
                );

                videos.mkdirs();
                thumbs.mkdirs();

                deleteContents(videos);
                deleteContents(thumbs);

                JSONArray result = new JSONArray();

                collectVideos(
                        treeUri,
                        DocumentsContract.getTreeDocumentId(treeUri),
                        videos,
                        thumbs,
                        result
                );

                if (result.length() == 0) {
                    notifyError(
                            "لم يتم العثور على ملفات فيديو معروفة الامتداد."
                    );
                    return;
                }

                notifyVideos(result);

            } catch (Exception e) {

                Log.e(TAG, "Folder scan failed", e);

                notifyError(
                        "حدث خطأ أثناء قراءة الفيديوهات: " +
                                (
                                        e.getMessage() == null
                                                ? "غير معروف"
                                                : e.getMessage()
                                )
                );
            }
        });
    }

    private void collectVideos(
            Uri treeUri,
            String parentId,
            File videoDir,
            File thumbDir,
            JSONArray result
    ) throws Exception {

        Uri children =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        parentId
                );

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        List<String[]> docs = new ArrayList<>();

        try (Cursor cursor = getContentResolver().query(
                children,
                projection,
                null,
                null,
                null
        )) {

            if (cursor == null) {
                return;
            }

            int id = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
            );

            int name = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            );

            int mime = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            );

            while (cursor.moveToNext()) {

                docs.add(new String[]{
                        cursor.getString(id),
                        cursor.getString(name),
                        cursor.getString(mime)
                });
            }
        }

        for (String[] doc : docs) {

            String id = doc[0];

            String name = doc[1] == null
                    ? "video.mp4"
                    : doc[1];

            String mime = doc[2] == null
                    ? ""
                    : doc[2];

            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {

                collectVideos(
                        treeUri,
                        id,
                        videoDir,
                        thumbDir,
                        result
                );

            } else if (isSupportedVideo(name, mime)) {

                copyVideo(
                        treeUri,
                        id,
                        name,
                        videoDir,
                        thumbDir,
                        result
                );
            }
        }
    }

    private boolean isSupportedVideo(String name, String mime) {

        String lower = name.toLowerCase(Locale.ROOT);

        String[] extensions = {
                ".mp4",
                ".m4v",
                ".3gp",
                ".3g2",
                ".webm",
                ".mov",
                ".mkv",
                ".avi",
                ".mpg",
                ".mpeg",
                ".mpe",
                ".ts",
                ".m2ts",
                ".mts",
                ".flv",
                ".wmv",
                ".asf",
                ".ogv",
                ".vob",
                ".mpg4"
        };

        for (String ext : extensions) {

            if (lower.endsWith(ext)) {
                return true;
            }
        }

        return mime != null &&
                mime.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    private void copyVideo(
            Uri treeUri,
            String documentId,
            String originalName,
            File videoDir,
            File thumbDir,
            JSONArray result
    ) {

        Uri source =
                DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                );

        String extension = ".video";

        int dot = originalName.lastIndexOf('.');

        if (dot >= 0) {
            extension = originalName
                    .substring(dot)
                    .toLowerCase(Locale.ROOT);
        }

        File target = new File(
                videoDir,
                UUID.randomUUID() + extension
        );

        try (
                InputStream in = getContentResolver().openInputStream(source);
                FileOutputStream out = new FileOutputStream(target)
        ) {

            if (in == null) {
                return;
            }

            byte[] buffer = new byte[128 * 1024];

            int n;

            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }

            out.flush();

            if (target.length() <= 0) {
                target.delete();
                return;
            }

            String thumb = createThumbnail(
                    target,
                    thumbDir
            );

            JSONObject item = new JSONObject();

            item.put("name", originalName);
            item.put("path", target.getAbsolutePath());
            item.put("size", target.length());

            if (thumb != null) {
                item.put("thumbnail", thumb);
            }

            result.put(item);

        } catch (Exception e) {

            Log.e(TAG, "Copy failed: " + originalName, e);

            target.delete();
        }
    }

    private String createThumbnail(File video, File thumbDir) {

        MediaMetadataRetriever retriever =
                new MediaMetadataRetriever();

        Bitmap bitmap = null;

        try {

            retriever.setDataSource(video.getAbsolutePath());

            bitmap = retriever.getFrameAtTime(
                    1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            );

            if (bitmap == null) {
                bitmap = retriever.getFrameAtTime();
            }

            if (bitmap == null) {
                return null;
            }

            File thumb = new File(
                    thumbDir,
                    UUID.randomUUID() + ".jpg"
            );

            try (FileOutputStream out = new FileOutputStream(thumb)) {

                bitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        82,
                        out
                );

                out.flush();
            }

            return thumb.getAbsolutePath();

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Thumbnail unavailable: " + video.getName(),
                    e
            );

            return null;

        } finally {

            if (bitmap != null) {
                bitmap.recycle();
            }

            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private void deleteContents(File dir) {

        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }

        for (File file : files) {

            if (file.isDirectory()) {
                deleteContents(file);
            }

            file.delete();
        }
    }

    private void notifyVideos(JSONArray videos) {

        runOnUiThread(() -> {

            try {

                WebView webView = getBridge().getWebView();

                webView.evaluateJavascript(
                        "window.onFolderSelected(" + videos + ");",
                        null
                );

            } catch (Exception e) {

                Log.e(TAG, "Send videos failed", e);
            }
        });
    }

    private void notifyError(String message) {

        runOnUiThread(() -> {

            try {

                WebView webView = getBridge().getWebView();

                webView.evaluateJavascript(
                        "window.onFolderError(" +
                                JSONObject.quote(message) +
                                ");",
                        null
                );

            } catch (Exception e) {

                Log.e(TAG, "Send error failed", e);
            }
        });
    }

    @Override
    public void onDestroy() {

        executor.shutdown();

        super.onDestroy();
    }
}
