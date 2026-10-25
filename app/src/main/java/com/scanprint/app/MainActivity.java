package com.scanprint.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private static final int REQ_SAVE = 2;

    private WebView web;
    private ValueCallback<Uri[]> filePathCb;
    private File cameraFile;
    private Uri cameraUri;

    private final ArrayDeque<Object[]> saveQueue = new ArrayDeque<>();
    private boolean saving = false;
    private byte[] pendingBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                cameraFile = null;
                cameraUri = null;
                Intent cam = null;
                try {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    cameraFile = File.createTempFile("capture_", ".jpg", dir);
                    cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", cameraFile);
                    cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    cam = null;
                }
                try {
                    if (params.isCaptureEnabled() && cam != null) {
                        startActivityForResult(cam, REQ_FILE);
                    } else {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType("image/*");
                        if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        }
                        Intent chooser = Intent.createChooser(pick, "Choose photo");
                        if (cam != null) {
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                        }
                        startActivityForResult(chooser, REQ_FILE);
                    }
                } catch (Exception e) {
                    filePathCb = null;
                    cb.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Could not open the photo picker", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            Uri[] result = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getClipData() != null) {
                    ClipData cd = data.getClipData();
                    result = new Uri[cd.getItemCount()];
                    for (int i = 0; i < cd.getItemCount(); i++) result[i] = cd.getItemAt(i).getUri();
                } else if (data != null && data.getData() != null) {
                    result = new Uri[]{data.getData()};
                } else if (cameraFile != null && cameraFile.length() > 0 && cameraUri != null) {
                    result = new Uri[]{cameraUri};
                }
            }
            if (filePathCb != null) {
                filePathCb.onReceiveValue(result);
                filePathCb = null;
            }
        } else if (requestCode == REQ_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBytes != null) {
                try {
                    OutputStream out = getContentResolver().openOutputStream(data.getData());
                    if (out != null) {
                        out.write(pendingBytes);
                        out.close();
                        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Could not save the file", Toast.LENGTH_LONG).show();
                }
            }
            pendingBytes = null;
            saving = false;
            nextSave();
        }
    }

    private void nextSave() {
        if (saving || saveQueue.isEmpty()) return;
        Object[] o = saveQueue.poll();
        saving = true;
        pendingBytes = (byte[]) o[2];
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType((String) o[1]);
        i.putExtra(Intent.EXTRA_TITLE, (String) o[0]);
        try {
            startActivityForResult(i, REQ_SAVE);
        } catch (Exception e) {
            saving = false;
            pendingBytes = null;
            Toast.makeText(this, "No file manager available to save", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    private class Bridge {
        @JavascriptInterface
        public void saveFile(final String name, String b64, final String mime) {
            final byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    saveQueue.add(new Object[]{name, mime, bytes});
                    nextSave();
                }
            });
        }

        @JavascriptInterface
        public void shareFile(final String name, String b64, final String mime) {
            final byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File dir = new File(getCacheDir(), "shared");
                        if (!dir.exists()) dir.mkdirs();
                        File f = new File(dir, name);
                        FileOutputStream fo = new FileOutputStream(f);
                        fo.write(bytes);
                        fo.close();
                        Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType(mime);
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Share"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Could not share the file", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        @JavascriptInterface
        public void printPdf(final String name, String b64) {
            final byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                        PrintAttributes attrs = new PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();
                        pm.print(name, new PdfAdapter(name, bytes), attrs);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Printing is not available", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private static class PdfAdapter extends PrintDocumentAdapter {
        private final String name;
        private final byte[] data;

        PdfAdapter(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }

        @Override
        public void onLayout(PrintAttributes oldAttrs, PrintAttributes newAttrs, CancellationSignal signal,
                             LayoutResultCallback callback, Bundle extras) {
            if (signal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build();
            callback.onLayoutFinished(info, true);
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal signal,
                            WriteResultCallback callback) {
            try {
                OutputStream out = new FileOutputStream(destination.getFileDescriptor());
                out.write(data);
                out.flush();
                callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
            } catch (IOException e) {
                callback.onWriteFailed(String.valueOf(e));
            }
        }
    }
}
