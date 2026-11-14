package il.nadavilr.hapoeljerusalem;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.HashMap;
import java.util.Map;

/**
 * The club's sites as screens of the app – tickets ("tix"), shop ("shop") and membership ("mem"): one WebView per site
 * over the app, leaving the app's bottom navigation visible. Each site keeps its own page and history between visits.
 * Pulling down at the top of a site reloads the page that is open in it (it stays on that page).
 * The tickets site is created and loaded in the background at start (preload), so its tab opens instantly.
 */
@CapacitorPlugin(name = "Tickets")
public class TicketsPlugin extends Plugin {

    private static final class Site {
        LinearLayout box;
        SwipeRefreshLayout pull;
        Runnable goBack;
        WebView web;
        OnBackPressedCallback back;
    }

    private final Map<String, Site> sites = new HashMap<>();

    private static String key(PluginCall call) {
        String k = call.getString("key");
        return k == null || k.isEmpty() ? "tix" : k;
    }

    @PluginMethod
    public void preload(PluginCall call) {
        String url = call.getString("url"), k = key(call);
        getActivity().runOnUiThread(() -> { site(k, url); call.resolve(); });
    }

    @PluginMethod
    public void show(PluginCall call) {
        String url = call.getString("url"), k = key(call);
        int bottom = call.getInt("bottom", 0);
        boolean home = Boolean.TRUE.equals(call.getBoolean("home", false));
        getActivity().runOnUiThread(() -> {
            /* one site on screen at a time */
            for (Map.Entry<String, Site> e : sites.entrySet()) if (!e.getKey().equals(k)) hideSite(e.getValue());
            Site s = site(k, url);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) s.box.getLayoutParams();
            lp.bottomMargin = Math.max(0, bottom);
            int top = call.getInt("top", 0);
            lp.topMargin = top > 0 ? top : MainActivity.statusBarPx(getActivity());
            s.box.setLayoutParams(lp);
            if (home && url != null) s.web.loadUrl(url);
            s.box.setVisibility(View.VISIBLE);
            s.box.bringToFront();
            s.back.setEnabled(true);
            call.resolve();
        });
    }

    /** The header's "חזרה": back inside the site, and from its first page close it (the app returns to "עוד"). */
    @PluginMethod
    public void back(PluginCall call) {
        String k = key(call);
        getActivity().runOnUiThread(() -> { Site s = sites.get(k); if (s != null && s.goBack != null) s.goBack.run(); call.resolve(); });
    }

    /** Hides every site (or only the one in "key"). */
    @PluginMethod
    public void hide(PluginCall call) {
        String k = call.getString("key");
        getActivity().runOnUiThread(() -> {
            for (Map.Entry<String, Site> e : sites.entrySet()) if (k == null || k.equals(e.getKey())) hideSite(e.getValue());
            call.resolve();
        });
    }

    private static void hideSite(Site s) {
        if (s.box != null) s.box.setVisibility(View.INVISIBLE);
        if (s.pull != null) s.pull.setRefreshing(false);
        if (s.back != null) s.back.setEnabled(false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private Site site(String k, String url) {
        Site s = sites.get(k);
        if (s != null) return s;
        Activity a = getActivity();
        boolean dark = MainActivity.isDark(a);
        s = new Site();
        WebView web = new WebView(a);
        WebSettings st = web.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        st.setLoadWithOverviewMode(true);
        st.setUseWideViewPort(true);
        st.setSupportZoom(false);
        st.setBuiltInZoomControls(false);
        st.setDisplayZoomControls(false);
        st.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(dark ? MainActivity.INK : 0xFFFFFFFF);
        web.setWebChromeClient(new WebChromeClient());
        SwipeRefreshLayout box = new SwipeRefreshLayout(a);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl(); String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                try { a.startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
            @Override public void onPageFinished(WebView v, String u) { box.setRefreshing(false); }
        });
        /* Pull down at the top of the site: reload the page that is open now (the site stays on it) */
        box.addView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        box.setColorSchemeColors(MainActivity.RED);
        box.setProgressBackgroundColorSchemeColor(dark ? MainActivity.INK : 0xFFFFFFFF);
        box.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0 || web.canScrollVertically(-1));
        box.setOnRefreshListener(web::reload);
        /* The site sits under the app's own header (which shows "חזרה" in place of the club logo); the app passes its
           height as "top". The window is just the pull-to-refresh box with the site in it */
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(dark ? MainActivity.INK : 0xFFFFFFFF);
        col.setClickable(true);
        col.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        col.setVisibility(View.INVISIBLE);
        ((ViewGroup) getBridge().getWebView().getParent()).addView(col, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null) web.loadUrl(url);
        /* Back: the previous page inside the site, and from its first page close the screen */
        final Site self = s;
        Runnable goBack = () -> {
            if (web.canGoBack()) web.goBack();
            else { hideSite(self); JSObject e = new JSObject(); e.put("key", k); notifyListeners("closed", e); }
        };
        s.goBack = goBack;
        s.back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { goBack.run(); }
        };
        ((AppCompatActivity) a).getOnBackPressedDispatcher().addCallback(s.back);
        s.box = col;
        s.pull = box;
        s.web = web;
        sites.put(k, s);
        return s;
    }
}
