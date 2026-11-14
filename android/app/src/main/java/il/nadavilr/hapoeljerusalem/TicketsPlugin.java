package il.nadavilr.hapoeljerusalem;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * The club's sites as screens of the app – login ("auth"), tickets ("tix"), shop ("shop") and membership ("mem"): one
 * WebView per site over the app, under the app's header and above its bottom navigation. Each site keeps its own page
 * and history between visits, and all of them share one cookie store (one login for tickets and membership).
 * Pulling down at the top of a site reloads the page that is open in it.
 * A pop-up a site opens (window.open / target=_blank) opens in a browser inside the app, over the site, with a close bar;
 * the pop-up keeps its link to the page that opened it, so logins and payments that use pop-ups work.
 * "Add to Google Wallet" links open the Google Wallet app on the device (not a Google login inside the app).
 * Every address change is reported to the app as a "url" event ({key, url}).
 */
@CapacitorPlugin(name = "Tickets")
public class TicketsPlugin extends Plugin {

    private static final class Site {
        FrameLayout box;
        SwipeRefreshLayout pull;
        Runnable goBack;
        WebView web;
        OnBackPressedCallback back;
        final List<View> pops = new ArrayList<>();
        final List<WebView> popWebs = new ArrayList<>();
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
            if (home && url != null) { closePops(s); s.web.loadUrl(url); }
            s.box.setVisibility(View.VISIBLE);
            s.box.bringToFront();
            s.back.setEnabled(true);
            call.resolve();
        });
    }

    /** The header's "חזרה": closes a pop-up, else back inside the site, and from its first page closes it. */
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
        CookieManager.getInstance().flush();
    }

    private static void closePops(Site s) {
        while (!s.pops.isEmpty()) closeTopPop(s);
    }

    private static void closeTopPop(Site s) {
        int i = s.pops.size() - 1; if (i < 0) return;
        View v = s.pops.remove(i); WebView w = s.popWebs.remove(i);
        if (v.getParent() instanceof ViewGroup) ((ViewGroup) v.getParent()).removeView(v);
        try { w.stopLoading(); w.destroy(); } catch (Exception ignored) { }
    }

    /** The login pages of the club's sites (the club's account system and the Cognito login behind it). */
    private static boolean isLogin(String h) {
        return h != null && (h.equals("auth.hapoel.co.il") || h.endsWith(".amazoncognito.com") || h.startsWith("cognito-"));
    }

    private static String host(String u) { try { return u == null ? null : Uri.parse(u).getHost(); } catch (Exception e) { return null; } }

    /** Google Wallet "save" links: to the Google Wallet app (falls back to whatever handles the link, e.g. Chrome). */
    private static boolean isWallet(Uri u) {
        String h = u.getHost(), p = u.getPath();
        return h != null && p != null && (h.equals("pay.google.com") || h.equals("wallet.google.com") || h.equals("walletobjects.googleapis.com")) && (p.startsWith("/gp/v/save") || p.contains("/save"));
    }

    /** Links that leave the WebView: other apps (tel:, mailto:, whatsapp:, intent:…) and Google Wallet. True = handled. */
    private static boolean external(Activity a, Uri u) {
        if (u == null) return false;
        String sc = u.getScheme();
        if ("http".equals(sc) || "https".equals(sc)) {
            if (!isWallet(u)) return false;
            Intent w = new Intent(Intent.ACTION_VIEW, u).setPackage("com.google.android.apps.walletnfcrel");
            try { a.startActivity(w); return true; } catch (ActivityNotFoundException ignored) { }
            try { a.startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)); } catch (Exception ignored) { }
            return true;
        }
        if ("about".equals(sc) || "data".equals(sc) || "blob".equals(sc) || "javascript".equals(sc)) return false;
        try {
            if ("intent".equals(sc)) {
                Intent i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE); i.setComponent(null); i.setSelector(null);
                try { a.startActivity(i); }
                catch (ActivityNotFoundException e) {
                    String fb = i.getStringExtra("browser_fallback_url");
                    if (fb != null) a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fb)));
                    else if (i.getPackage() != null) a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())));
                }
            } else a.startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignored) { }
        return true;
    }

    /* Inside a pop-up: the page's link to the window that opened it ("window.opener") goes through the app. A login page
       that ends with opener.postMessage(...) + window.close() (the club's login, opened by the shop) then hands the login
       to the site and closes – as in a regular browser */
    private static final String SHIM = "(function(){if(window.__hpjOp)return;window.__hpjOp=1;"
        + "var g=function(u){try{HPJPopup.go(u==null?'':String(u))}catch(e){}};"
        + "var loc={reload:function(){g('')},assign:g,replace:g,get href(){return ''},set href(u){g(u)}};"
        + "var op={closed:false,postMessage:function(d){try{HPJPopup.post(JSON.stringify(d===undefined?null:d),location.origin)}catch(e){}},focus:function(){},close:function(){},HandlePopupResult:g,get location(){return loc},set location(u){g(u)}};"
        + "try{Object.defineProperty(window,'opener',{configurable:true,get:function(){return op},set:function(){}})}catch(e){}"
        + "var c=window.close;window.close=function(){try{HPJPopup.close()}catch(e){}try{c.call(window)}catch(e){}};})();";

    /* On a site's page: the shop's "התחברות לחשבון האוהד" opens its login (…?option=oauthredirect…) in a pop-up and waits
       for a signal from it that does not reach across the app's windows. Instead the login opens in the site's own window –
       the login's other way, which brings the site back to the shop logged in, exactly as in a browser */
    private static final String SAME_WINDOW_LOGIN = "(function(){if(window.__hpjWo)return;window.__hpjWo=1;var o=window.open;"
        + "window.open=function(u){try{var s=String(u||'');if(/[?&]option=oauthredirect/.test(s)){location.href=s;return {closed:false,close:function(){},focus:function(){},postMessage:function(){}};}}catch(e){}return o.apply(window,arguments);};})();";

    @SuppressLint("SetJavaScriptEnabled")
    private static void setup(WebView web, boolean dark) {
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
        st.setSupportMultipleWindows(true);
        st.setJavaScriptCanOpenWindowsAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(dark ? MainActivity.INK : 0xFFFFFFFF);
    }

    private void report(String k, String url) {
        if (url == null || url.startsWith("about:")) return;
        JSObject e = new JSObject(); e.put("key", k); e.put("url", url); notifyListeners("url", e);
    }

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getActivity().getResources().getDisplayMetrics()); }

    /** Chrome client of a site and of its pop-ups: a new window opens as an in-app browser over the site. */
    private WebChromeClient chrome(Site s, String k, boolean dark) {
        Activity a = getActivity();
        return new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v, boolean dialog, boolean user, Message msg) {
                WebView pw = new WebView(a);
                setup(pw, dark);
                pw.setWebChromeClient(this);
                /* A login window (the shop's "התחברות לחשבון האוהד"): the site opens it, it goes to the club's login, and
                   comes back to the site with the login done. In a regular browser it then closes itself and the site
                   refreshes (through localStorage, which does not reach across the app's windows) – here: as soon as it is
                   back on the site after the login page, the window closes and the site's page reloads, logged in */
                final boolean[] away = { false };
                final String home = host(v.getUrl());
                final Runnable done = () -> {
                    int i = s.popWebs.indexOf(pw); if (i < 0) return;
                    while (s.popWebs.size() > i) closeTopPop(s);
                    CookieManager.getInstance().flush();
                    v.reload();
                };
                final java.util.function.Predicate<String> back = u -> {
                    String h = host(u);
                    if (isLogin(h)) { away[0] = true; return false; }
                    if (!away[0] || h == null || home == null || !h.equals(home)) return false;
                    if (u.contains("option=oauthredirect") || u.contains("code=") || u.contains("state=")) return false;
                    a.runOnUiThread(done); return true;
                };
                /* window.opener of the pop-up: messages go to the page that opened it, and window.close() closes the pop-up */
                pw.addJavascriptInterface(new Object() {
                    @JavascriptInterface public void post(String json, String origin) {
                        a.runOnUiThread(() -> v.evaluateJavascript("(function(){var d;try{d=JSON.parse(" + JSONObject.quote(json) + ")}catch(e){d=" + JSONObject.quote(json) + "}"
                            + "window.dispatchEvent(new MessageEvent('message',{data:d,origin:" + JSONObject.quote(origin == null ? "" : origin) + "}))})()", null));
                    }
                    /* the pop-up sends the opening page somewhere (or reloads it, with "") – and closes */
                    @JavascriptInterface public void go(String u) {
                        a.runOnUiThread(() -> {
                            int i = s.popWebs.indexOf(pw); if (i >= 0) { while (s.popWebs.size() > i) closeTopPop(s); }
                            CookieManager.getInstance().flush();
                            if (u == null || u.isEmpty()) v.reload(); else v.loadUrl(u);
                        });
                    }
                    @JavascriptInterface public void close() {
                        a.runOnUiThread(() -> { if (away[0]) { done.run(); return; } int i = s.popWebs.indexOf(pw); if (i >= 0) { while (s.popWebs.size() > i) closeTopPop(s); } });
                    }
                }, "HPJPopup");
                pw.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) {
                        if (back.test(req.getUrl().toString())) return true;
                        if (!external(a, req.getUrl())) return false;
                        /* the pop-up only led out of the app (e.g. to Google Wallet): close it */
                        if (w.copyBackForwardList().getSize() == 0) w.post(() -> { int i = s.popWebs.indexOf(w); if (i >= 0 && i == s.popWebs.size() - 1) closeTopPop(s); });
                        return true;
                    }
                    @Override public void onPageStarted(WebView w, String u, android.graphics.Bitmap f) { report(k, u); if (back.test(u)) { w.stopLoading(); return; } w.evaluateJavascript(SHIM, null); }
                    @Override public void onPageFinished(WebView w, String u) { w.evaluateJavascript(SHIM, null); CookieManager.getInstance().flush(); if (back.test(u)) return;
                        String h = host(u); if (away[0] && h != null && h.equals(home)) w.postDelayed(() -> { if (s.popWebs.contains(pw)) done.run(); }, 3000); }
                });
                /* the in-app browser: a bar with "סגירה" and the site's address, and the page under it */
                LinearLayout pop = new LinearLayout(a);
                pop.setOrientation(LinearLayout.VERTICAL);
                pop.setBackgroundColor(dark ? MainActivity.INK : 0xFFFFFFFF);
                pop.setClickable(true);
                LinearLayout bar = new LinearLayout(a);
                bar.setOrientation(LinearLayout.HORIZONTAL);
                bar.setGravity(Gravity.CENTER_VERTICAL);
                bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
                bar.setPadding(dp(8), 0, dp(8), 0);
                bar.setBackgroundColor(dark ? 0xFF1E1E1E : 0xFFF2F2F2);
                TextView close = new TextView(a);
                close.setText("✕  סגירה");
                close.setTextColor(MainActivity.RED);
                close.setTextSize(15);
                close.setTypeface(close.getTypeface(), android.graphics.Typeface.BOLD);
                close.setPadding(dp(10), dp(10), dp(10), dp(10));
                close.setOnClickListener(x -> closeTopPop(s));
                TextView addr = new TextView(a);
                addr.setTextColor(dark ? 0xB3FFFFFF : 0x99282828);
                addr.setTextSize(12);
                addr.setSingleLine(true);
                addr.setEllipsize(TextUtils.TruncateAt.END);
                addr.setGravity(Gravity.START);
                addr.setPadding(dp(8), 0, dp(4), 0);
                bar.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                bar.addView(addr, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                pop.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
                pop.addView(pw, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                pw.setWebChromeClient(new WebChromeClient() {
                    @Override public boolean onCreateWindow(WebView v2, boolean d2, boolean u2, Message m2) { return chrome(s, k, dark).onCreateWindow(v2, d2, u2, m2); }
                    @Override public void onCloseWindow(WebView w) { int i = s.popWebs.indexOf(w); if (i >= 0) { while (s.popWebs.size() > i) closeTopPop(s); } }
                    @Override public void onReceivedTitle(WebView w, String t) { String u = w.getUrl(); Uri p = u == null ? null : Uri.parse(u); addr.setText(p != null && p.getHost() != null ? p.getHost() : (t == null ? "" : t)); }
                });
                s.box.addView(pop, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                s.pops.add(pop); s.popWebs.add(pw);
                ((WebView.WebViewTransport) msg.obj).setWebView(pw);
                msg.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView w) { int i = s.popWebs.indexOf(w); if (i >= 0) { while (s.popWebs.size() > i) closeTopPop(s); } }
        };
    }

    private Site site(String k, String url) {
        Site s = sites.get(k);
        if (s != null) return s;
        Activity a = getActivity();
        boolean dark = MainActivity.isDark(a);
        Site site = new Site();
        WebView web = new WebView(a);
        setup(web, dark);
        SwipeRefreshLayout pull = new SwipeRefreshLayout(a);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) { return external(a, req.getUrl()); }
            @Override public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) { report(k, u); v.evaluateJavascript(SAME_WINDOW_LOGIN, null); }
            @Override public void doUpdateVisitedHistory(WebView v, String u, boolean reload) { report(k, u); }
            @Override public void onPageFinished(WebView v, String u) { v.evaluateJavascript(SAME_WINDOW_LOGIN, null); pull.setRefreshing(false); CookieManager.getInstance().flush(); report(k, u); }
        });
        /* Pull down at the top of the site: reload the page that is open now (the site stays on it) */
        pull.addView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pull.setColorSchemeColors(MainActivity.RED);
        pull.setProgressBackgroundColorSchemeColor(dark ? MainActivity.INK : 0xFFFFFFFF);
        pull.setOnChildScrollUpCallback((parent, child) -> web.getScrollY() > 0 || web.canScrollVertically(-1));
        pull.setOnRefreshListener(web::reload);
        /* The site sits under the app's own header (which shows "חזרה" in place of the club logo); the app passes its
           height as "top". Pop-ups are stacked over the site inside this box */
        FrameLayout box = new FrameLayout(a);
        box.setBackgroundColor(dark ? MainActivity.INK : 0xFFFFFFFF);
        box.setClickable(true);
        box.addView(pull, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        box.setVisibility(View.INVISIBLE);
        ((ViewGroup) getBridge().getWebView().getParent()).addView(box, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        site.box = box;
        site.pull = pull;
        site.web = web;
        web.setWebChromeClient(chrome(site, k, dark));
        if (url != null) web.loadUrl(url);
        /* Back: closes a pop-up (or goes back inside it), then the previous page in the site, and from its first page closes the screen */
        Runnable goBack = () -> {
            int n = site.popWebs.size();
            if (n > 0) { WebView pw = site.popWebs.get(n - 1); if (pw.canGoBack()) pw.goBack(); else closeTopPop(site); return; }
            if (web.canGoBack()) web.goBack();
            else { hideSite(site); JSObject e = new JSObject(); e.put("key", k); notifyListeners("closed", e); }
        };
        site.goBack = goBack;
        site.back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { goBack.run(); }
        };
        ((AppCompatActivity) a).getOnBackPressedDispatcher().addCallback(site.back);
        sites.put(k, site);
        return site;
    }
}
