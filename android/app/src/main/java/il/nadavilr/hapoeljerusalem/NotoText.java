package il.nadavilr.hapoeljerusalem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;
import androidx.core.content.res.ResourcesCompat;

/**
 * Text for the widgets, drawn in the app's font (Noto Sans Hebrew). A widget cannot use the app's own font – Android
 * shows it in the phone's font – so every text and number is drawn here as an image: an alpha-only bitmap (a quarter of
 * the memory of a colour one) that the ImageView paints in the text's colour. Right-to-left text, mixed with numbers,
 * goes through Android's own bidi line layout; text wider than its room is shortened with "…".
 */
final class NotoText {

    private NotoText() {
    }

    private static Typeface regular, bold;

    static synchronized Typeface face(Context c, boolean b) {
        if (b && bold == null) bold = load(c, R.font.noto_sans_hebrew_bold, Typeface.BOLD);
        if (!b && regular == null) regular = load(c, R.font.noto_sans_hebrew_regular, Typeface.NORMAL);
        return b ? bold : regular;
    }

    private static Typeface load(Context c, int id, int style) {
        try {
            Typeface t = ResourcesCompat.getFont(c, id);
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return Typeface.create(Typeface.SANS_SERIF, style);
    }

    static TextPaint paint(Context c, float dp, boolean b) {
        TextPaint p = new TextPaint(TextPaint.ANTI_ALIAS_FLAG | TextPaint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face(c, b));
        p.setTextSize(dp * c.getResources().getDisplayMetrics().density);
        p.setColor(Color.BLACK);
        return p;
    }

    /** The width the text needs, in dp. */
    static float widthDp(Context c, CharSequence text, float dp, boolean b) {
        return Layout.getDesiredWidth(text, paint(c, dp, b)) / c.getResources().getDisplayMetrics().density;
    }

    /**
     * The text as an alpha-only bitmap: one line, the height of the font's line (as a TextView without font padding),
     * at most {@code maxDp} wide (0 – no limit), shortened with "…" if needed. Null for empty text.
     */
    static Bitmap draw(Context c, CharSequence text, float dp, boolean b, float maxDp) {
        if (text == null || text.length() == 0 || dp <= 0) return null;
        float density = c.getResources().getDisplayMetrics().density;
        TextPaint p = paint(c, dp, b);
        float want = Layout.getDesiredWidth(text, p), max = maxDp > 0 ? maxDp * density : Float.MAX_VALUE;
        CharSequence t = want > max ? TextUtils.ellipsize(text, p, max, TextUtils.TruncateAt.END) : text;
        int w = (int) Math.ceil(Math.min(want, max)) + 2;
        StaticLayout l;
        if (Build.VERSION.SDK_INT >= 23) {
            l = StaticLayout.Builder.obtain(t, 0, t.length(), p, w).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setIncludePad(false).setMaxLines(1).build();
        } else {
            //noinspection deprecation
            l = new StaticLayout(t, p, w, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false);
        }
        int h = Math.max(1, l.getHeight());
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, w), h, Bitmap.Config.ALPHA_8);
        bmp.setDensity(c.getResources().getDisplayMetrics().densityDpi);
        l.draw(new Canvas(bmp));
        return bmp;
    }

    /** Puts the text into an ImageView of the widget, in {@code argb} (its alpha included). */
    static void set(Context c, RemoteViews v, int id, CharSequence text, float dp, boolean b, int argb, float maxDp) {
        Bitmap bmp = draw(c, text, dp, b, maxDp);
        if (bmp == null) {
            v.setImageViewResource(id, android.R.color.transparent);
            return;
        }
        v.setImageViewBitmap(id, bmp);
        v.setInt(id, "setColorFilter", argb | 0xFF000000);
        v.setInt(id, "setImageAlpha", Color.alpha(argb));
    }
}
