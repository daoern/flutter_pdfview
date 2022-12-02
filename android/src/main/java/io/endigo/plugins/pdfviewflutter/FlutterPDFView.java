package io.endigo.plugins.pdfviewflutter;

import android.content.Context;
import android.view.View;
import android.net.Uri;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformView;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.PDFView.Configurator;
import com.github.barteksc.pdfviewer.listener.*;
import com.github.barteksc.pdfviewer.util.Constants;

public class FlutterPDFView implements PlatformView, MethodCallHandler {
    private final PDFView pdfView;
    private final MethodChannel methodChannel;

    @SuppressWarnings("unchecked")
    FlutterPDFView(Context context, BinaryMessenger messenger, int id, Map<String, Object> params) {
        pdfView = new PDFView(context, null);

        methodChannel = new MethodChannel(messenger, "plugins.endigo.io/pdfview_" + id);
        methodChannel.setMethodCallHandler(this);

        Configurator config = null;
        if (params.get("filePath") != null) {
          String filePath = (String) params.get("filePath");
          config = pdfView.fromUri(getURI(filePath));
        }
        else if (params.get("pdfData") != null) {
          byte[] data = (byte[]) params.get("pdfData");
          config = pdfView.fromBytes(data);
        }

        if (config != null) {
            config
                    .enableSwipe(getBoolean(params, "enableSwipe"))
                    .swipeVertical(!getBoolean(params, "swipeHorizontal"))
                    .password(getString(params, "password"))
                    .showMinimap(false)
                    .enableAnnotationRendering(true)
                    .onPageChange(new OnPageChangeListener() {
                        @Override
                        public void onPageChanged(int page, int total) {
                            Map<String, Object> args = new HashMap<>();
                            args.put("page", page);
                            args.put("total", total);
                            methodChannel.invokeMethod("onPageChanged", args);
                        }
                    }).onError(new OnErrorListener() {
                @Override
                public void onError(Throwable t) {
                    Map<String, Object> args = new HashMap<>();
                    args.put("error", t.toString());
                    methodChannel.invokeMethod("onError", args);
                }
            }).onPageError(new OnPageErrorListener() {
                @Override
                public void onPageError(int page, Throwable t) {
                    Map<String, Object> args = new HashMap<>();
                    args.put("page", page);
                    args.put("error", t.toString());
                    methodChannel.invokeMethod("onPageError", args);
                }
            }).onRender(new OnRenderListener() {
                @Override
                public void onInitiallyRendered(int pages) {
                    Map<String, Object> args = new HashMap<>();
                    args.put("pages", pages);
                    methodChannel.invokeMethod("onRender", args);
                }
            }).enableDoubletap(true).defaultPage(getInt(params, "defaultPage")).load();
        }
    }

    @Override
    public View getView() {
        return pdfView;
    }

    @Override
    public void onMethodCall(MethodCall methodCall, Result result) {
        switch (methodCall.method) {
            case "pageCount":
                getPageCount(result);
                break;
            case "currentPage":
                getCurrentPage(result);
                break;
            case "setPage":
                setPage(methodCall, result);
                break;
            case "updateSettings":
                updateSettings(methodCall, result);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    void getPageCount(Result result) {
        result.success(pdfView.getPageCount());
    }

    void getCurrentPage(Result result) {
        result.success(pdfView.getCurrentPage());
    }

    void setPage(MethodCall call, Result result) {
        if (call.argument("page") != null) {
            int page = (int) call.argument("page");
            pdfView.jumpTo(page);
        }

        result.success(true);
    }

    @SuppressWarnings("unchecked")
    private void updateSettings(MethodCall methodCall, Result result) {
        applySettings((Map<String, Object>) methodCall.arguments);
        result.success(null);
    }

    private void applySettings(Map<String, Object> settings) {
        for (String key : settings.keySet()) {
            switch (key) {
                case "enableSwipe":
                    pdfView.enableSwipe(getBoolean(settings, key));
                    break;
                case "nightMode":
                case "pageFling":
                case "pageSnap":
                case "preventLinkNavigation":
                    break;
                default:
                    throw new IllegalArgumentException("Unknown PDFView setting: " + key);
            }
        }
    }

    @Override
    public void dispose() {
        methodChannel.setMethodCallHandler(null);
    }

    boolean getBoolean(Map<String, Object> params, String key) {
        return params.containsKey(key) ? (boolean) params.get(key) : false;
    }

    String getString(Map<String, Object> params, String key) {
        return params.containsKey(key) ? (String) params.get(key) : "";
    }

    int getInt(Map<String, Object> params, String key) {
        return params.containsKey(key) ? (int) params.get(key) : 0;
    }

    private Uri getURI(final String uri) {
        Uri parsed = Uri.parse(uri);

        if (parsed.getScheme() == null || parsed.getScheme().isEmpty()) {
            return Uri.fromFile(new File(uri));
        }
        return parsed;
    }

}
