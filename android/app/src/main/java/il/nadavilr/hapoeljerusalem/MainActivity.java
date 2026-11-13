package il.nadavilr.hapoeljerusalem;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * The app window.
 * - Web links to other sites open in a window over the app (top bar with "חזרה"); the device Back button
 *   goes back inside that site first, then closes it and returns to the exact screen underneath.
 * - Device Back inside the app follows the page's own screens (window.hpjBack) and closes the app at home.
 * - Transparent status bar: the app's glass header continues underneath it (height passed to the page as --sat).
 */
public class MainActivity extends BridgeActivity {

    static final int RED = 0xFFD21919, INK = 0xFF282828;

    private LinearLayout extLayer;
    private WebView extWeb;
    private OnBackPressedCallback extBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(TicketsPlugin.class);
        registerPlugin(UpdaterPlugin.class);
        registerPlugin(LiveActivityPlugin.class);
        super.onCreate(savedInstanceState);
        LiveActivityPlugin.fromIntent(getIntent());
        LiveFixtures.schedule(this);
        preferHighRefreshRate();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        applyStatusBarIcons();
        try { UpdateCheckWorker.cancel(this); } catch (Exception ignored) { }
        if (bridge == null) return;

        WebView app = bridge.getWebView();
        app.setOverScrollMode(View.OVER_SCROLL_NEVER);
        app.setBackgroundColor(isDark() ? INK : Color.WHITE);
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) { super.onPageStarted(v, url, icon); passStatusBar(v); }
            @Override public void onPageFinished(WebView v, String url) { super.onPageFinished(v, url); passStatusBar(v); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (isWebPage(u) && req.isForMainFrame()) { openExternal(u.toString()); return true; }
                return super.shouldOverrideUrlLoading(v, req);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                app.evaluateJavascript("(function(){try{return !!(window.hpjBack&&window.hpjBack())}catch(e){return false}})()", r -> {
                    if (!"true".equals(r)) { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); setEnabled(true); }
                });
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        LiveActivityPlugin.fromIntent(intent);
    }

    static int statusBarPx(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : (int) (24 * c.getResources().getDisplayMetrics().density);
    }

    private void passStatusBar(WebView v) {
        float css = statusBarPx(this) / getResources().getDisplayMetrics().density;
        v.evaluateJavascript("document.documentElement.style.setProperty('--sat','" + css + "px')", null);
    }

    static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean isDark() { return isDark(this); }

    private void applyStatusBarIcons() {
        new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(!isDark());
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        applyStatusBarIcons();
        if (bridge != null) bridge.getWebView().setBackgroundColor(isDark() ? INK : Color.WHITE);
    }

    private static boolean isWebPage(Uri u) {
        if (u == null) return false;
        String s = u.getScheme(), h = u.getHost();
        return ("https".equals(s) || "http".equals(s)) && h != null && !h.equals("localhost");
    }

    /** Another site, in a window over the app: a glass-like top bar ("חזרה" + page title) with a thin red line. */
    @SuppressLint("SetJavaScriptEnabled")
    private void openExternal(String url) {
        if (extLayer != null) { extWeb.loadUrl(url); return; }
        float d = getResources().getDisplayMetrics().density;
        boolean dark = isDark();
        int bg = dark ? INK : Color.WHITE, fg = dark ? Color.WHITE : INK;

        LinearLayout layer = new LinearLayout(this);
        layer.setOrientation(LinearLayout.VERTICAL);
        layer.setBackgroundColor(bg);
        layer.setClickable(true);
        layer.setPadding(0, statusBarPx(this), 0, 0);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(bg);
        bar.setPadding((int) (10 * d), 0, (int) (10 * d), 0);

        TextView back = new TextView(this);
        back.setText("→  חזרה");
        back.setTextColor(fg);
        back.setTextSize(15);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setGravity(Gravity.CENTER);
        back.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(999 * d);
        pill.setColor(dark ? 0x1FFFFFFF : 0x0F282828);
        back.setBackground(pill);
        back.setContentDescription("חזרה");
        back.setOnClickListener(v -> closeExternal());
        bar.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (38 * d)));

        TextView title = new TextView(this);
        title.setTextColor(dark ? 0xB3FFFFFF : 0xB3282828);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setPadding((int) (12 * d), 0, (int) (6 * d), 0);
        title.setText(Uri.parse(url).getHost());
        bar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        layer.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (56 * d)));

        View line = new View(this);
        line.setBackgroundColor(RED);
        layer.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (2 * d))));

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String t) { if (t != null && !t.isEmpty() && !t.startsWith("http")) title.setText(t); }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl(); String sc = u.getScheme();
                if ("http".equals(sc) || "https".equals(sc)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }
        });
        layer.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewGroup parent = (ViewGroup) bridge.getWebView().getParent();
        parent.addView(layer, new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        layer.bringToFront();
        extLayer = layer;
        extWeb = web;
        web.loadUrl(url);

        extBack = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (extWeb != null && extWeb.canGoBack()) extWeb.goBack(); else closeExternal(); }
        };
        getOnBackPressedDispatcher().addCallback(this, extBack);
    }

    private void closeExternal() {
        if (extLayer == null) return;
        ViewGroup p = (ViewGroup) extLayer.getParent();
        if (p != null) p.removeView(extLayer);
        if (extWeb != null) { extWeb.stopLoading(); extWeb.destroy(); }
        extLayer = null;
        extWeb = null;
        if (extBack != null) { extBack.remove(); extBack = null; }
    }

    /** The screen's highest refresh rate (e.g. 120Hz) for smooth scrolling. */
    private void preferHighRefreshRate() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            Display dsp = getWindowManager().getDefaultDisplay();
            Display.Mode cur = dsp.getMode(), best = cur;
            for (Display.Mode m : dsp.getSupportedModes())
                if (m.getPhysicalWidth() == cur.getPhysicalWidth() && m.getPhysicalHeight() == cur.getPhysicalHeight() && m.getRefreshRate() > best.getRefreshRate()) best = m;
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.preferredDisplayModeId = best.getModeId();
            getWindow().setAttributes(lp);
        } catch (Exception ignored) { }
    }
}
