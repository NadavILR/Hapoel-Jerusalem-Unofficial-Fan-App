package il.nadavilr.hapoeljerusalem;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * The tickets site as a screen of the app (it refuses to be shown in a frame): a second WebView over the app
 * that leaves the app's bottom navigation visible. Created and loaded in the background at start (preload),
 * so the tab opens instantly and keeps its state between visits.
 */
@CapacitorPlugin(name = "Tickets")
public class TicketsPlugin extends Plugin {

    private WebView web;
    private OnBackPressedCallback back;

    @PluginMethod
    public void preload(PluginCall call) {
        String url = call.getString("url");
        getActivity().runOnUiThread(() -> { create(url); call.resolve(); });
    }

    @PluginMethod
    public void show(PluginCall call) {
        String url = call.getString("url");
        int bottom = call.getInt("bottom", 0);
        boolean home = Boolean.TRUE.equals(call.getBoolean("home", false));
        getActivity().runOnUiThread(() -> {
            create(url);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) web.getLayoutParams();
            lp.bottomMargin = Math.max(0, bottom);
            lp.topMargin = MainActivity.statusBarPx(getActivity());
            web.setLayoutParams(lp);
            if (home && url != null) web.loadUrl(url);
            web.setVisibility(View.VISIBLE);
            web.bringToFront();
            back.setEnabled(true);
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(PluginCall call) {
        getActivity().runOnUiThread(() -> { hideNow(); call.resolve(); });
    }

    private void hideNow() {
        if (web != null) web.setVisibility(View.INVISIBLE);
        if (back != null) back.setEnabled(false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void create(String url) {
        if (web != null) return;
        Activity a = getActivity();
        web = new WebView(a);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(MainActivity.isDark(a) ? MainActivity.INK : 0xFFFFFFFF);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl(); String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        web.setVisibility(View.INVISIBLE);
        ((ViewGroup) getBridge().getWebView().getParent()).addView(web, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null) web.loadUrl(url);
        /* Back: the previous page inside the site, and from its first page close the screen */
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else { hideNow(); notifyListeners("closed", new JSObject()); }
            }
        };
        ((AppCompatActivity) a).getOnBackPressedDispatcher().addCallback(back);
    }
}
