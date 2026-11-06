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
 * The tickets site as a screen of the app: a second WebView placed over the app, leaving the app's own
 * bottom navigation bar visible underneath it. It is created and loaded in the background when the app
 * starts (preload), so opening the tickets tab is instant, and it keeps its state between visits.
 */
@CapacitorPlugin(name = "Tickets")
public class TicketsPlugin extends Plugin {

    private WebView web;
    private OnBackPressedCallback back;

    @PluginMethod
    public void preload(PluginCall call) {
        String url = call.getString("url");
        getActivity().runOnUiThread(() -> {
            ensure(url);
            call.resolve();
        });
    }

    @PluginMethod
    public void show(PluginCall call) {
        String url = call.getString("url");
        int bottom = call.getInt("bottom", 0);
        boolean home = Boolean.TRUE.equals(call.getBoolean("home", false));
        getActivity().runOnUiThread(() -> {
            ensure(url);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) web.getLayoutParams();
            lp.bottomMargin = Math.max(0, bottom);
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
        getActivity().runOnUiThread(() -> {
            hideView();
            call.resolve();
        });
    }

    private void hideView() {
        if (web != null) web.setVisibility(View.INVISIBLE);
        if (back != null) back.setEnabled(false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void ensure(String url) {
        if (web != null) return;
        Activity activity = getActivity();
        web = new WebView(activity);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false; // stays in the tickets screen
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, u)); // tel:, mailto:, payment apps...
                } catch (Exception ignored) {
                }
                return true;
            }
        });
        web.setVisibility(View.INVISIBLE);
        ViewGroup parent = (ViewGroup) getBridge().getWebView().getParent();
        parent.addView(web, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null) web.loadUrl(url);

        // Back button: close the tickets screen and return to the app's home screen.
        back = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                hideView();
                notifyListeners("closed", new JSObject());
            }
        };
        ((AppCompatActivity) activity).getOnBackPressedDispatcher().addCallback(back);
    }
}
