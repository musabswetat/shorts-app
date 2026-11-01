
package com.shorts.app.v2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.provider.DocumentsContract;

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

        if (getBridge() != null &&
                getBridge().getWebView() != null) {

            getBridge().getWebView().addJavascriptInterface(
                    new AndroidBridge(),
                    "AndroidBridge"
            );
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void selectFolder() {
            runOnUiThread(() -> {
                try {
                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);

                    intent.addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    );

                    startActivityForResult(intent, REQUEST_FOLDER);

                } catch (Exception e) {
                    Log.e(TAG, "Folder picker failed", e);
                    notifyError("تعذر فتح اختيار المجلد.");
                }
            });
        }

        @JavascriptInterface
        public void checkSavedFolder() {
            SharedPreferences prefs =
                    getSharedPreferences(PREFS, MODE_PRIVATE);

            String saved =
                    prefs.getString(FOLDER_KEY, null);

            if (saved == null || saved.isEmpty()) {
                return;
            }

            try {
                scanFolder(Uri.parse(saved));
            } catch (Exception e) {
                Log.e(TAG, "Saved folder error", e);
                notifyError("تعذر الوصول إلى المجلد المحفوظ. اختره مجدداً.");
            }
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

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
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION |
                     Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            getContentResolver().takePersistableUriPermission(
                    treeUri,
                    flags
            );

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(FOLDER_KEY, treeUri.toString())
                    .apply();

            scanFolder(treeUri);

        } catch (Exception e) {
            Log.e(TAG, "Folder permission error", e);
            notifyError("تعذر حفظ صلاحية المجلد. اختره مرة أخرى.");
        }
    }

    private void scanFolder(Uri treeUri) {
        executor.execute(() -> {
            try {
                File directory = new File(
                        getFilesDir(),
                        "shorts-videos"
                );

                deleteContents(directory);
                directory.mkdirs();

                JSONArray result = new JSONArray();

                String rootId =
                        DocumentsContract.getTreeDocumentId(treeUri);

                collectVideos(
                        treeUri,
                        rootId,
                        directory,
                        result
                );

                Log.i(TAG, "Video count: " + result.length());

                if (result.length() == 0) {
                    notifyError("لم يتم العثور على فيديوهات مدعومة.");
                    return;
                }

                notifyVideos(result);

            } catch (Exception e) {
                Log.e(TAG, "Folder scanning failed", e);

                notifyError(
                        "حدث خطأ أثناء قراءة الفيديوهات: " +
                        (e.getMessage() == null ?
                                "خطأ غير معروف" : e.getMessage())
                );
            }
        });
    }

    private void collectVideos(
            Uri treeUri,
            String parentId,
            File outputDirectory,
            JSONArray result
    ) throws Exception {

        Uri childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri,
                        parentId
                );

        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };

        List<String[]> documents = new ArrayList<>();

        try (Cursor cursor = getContentResolver().query(
                childrenUri,
                projection,
                null,
                null,
                null
        )) {
            if (cursor == null) {
                return;
            }

            int idCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
            );

            int nameCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            );

            int mimeCol = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            );

            while (cursor.moveToNext()) {
                documents.add(new String[]{
                        cursor.getString(idCol),
                        cursor.getString(nameCol),
                        cursor.getString(mimeCol)
                });
            }
        }

        for (String[] document : documents) {
            String id = document[0];
            String name = document[1] == null ?
                    "video.mp4" : document[1];

            String mime = document[2] == null ?
                    "" : document[2];

            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                collectVideos(
                        treeUri,
                        id,
                        outputDirectory,
                        result
                );
                continue;
            }

            if (isSupportedVideo(name)) {
                copyVideo(
                        treeUri,
                        id,
                        name,
                        outputDirectory,
                        result
                );
            }
        }
    }

    private boolean isSupportedVideo(String name) {
        String lower = name.toLowerCase(Locale.ROOT);

        return lower.endsWith(".mp4") ||
                lower.endsWith(".m4v") ||
                lower.endsWith(".3gp") ||
                lower.endsWith(".webm") ||
                lower.endsWith(".mov");
    }

    private void copyVideo(
            Uri treeUri,
            String documentId,
            String originalName,
            File outputDirectory,
            JSONArray result
    ) {
        Uri documentUri =
                DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        documentId
                );

        String extension = ".mp4";
        int dot = originalName.lastIndexOf('.');

        if (dot >= 0) {
            extension = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }

        File destination = new File(
                outputDirectory,
                UUID.randomUUID().toString() + extension
        );

        try (
                InputStream input =
                        getContentResolver().openInputStream(documentUri);

                FileOutputStream output =
                        new FileOutputStream(destination)
        ) {
            if (input == null) {
                Log.e(TAG, "Cannot open " + originalName);
                return;
            }

            byte[] buffer = new byte[64 * 1024];
            int count;

            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }

            output.flush();

            long size = destination.length();

            if (size <= 0) {
                destination.delete();
                return;
            }

            JSONObject item = new JSONObject();
            item.put("name", originalName);
            item.put("path", destination.getAbsolutePath());
            item.put("size", size);

            result.put(item);

            Log.i(TAG,
                    "Copied: " + originalName +
                    " bytes=" + size);

        } catch (Exception e) {
            Log.e(TAG, "Copy failed: " + originalName, e);
            destination.delete();
        }
    }

    private void deleteContents(File directory) {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();

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
                        "window.onFolderSelected(" +
                                videos.toString() +
                                ");",
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

                String safeMessage = JSONObject.quote(message);

                webView.evaluateJavascript(
                        "window.onFolderError(" +
                                safeMessage +
                                ");",
                        null
                );

            } catch (Exception e) {
                Log.e(TAG, "Send error failed", e);
            }
        });
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }
}
