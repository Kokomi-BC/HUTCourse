package cn.edu.hut.course;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.appbar.MaterialToolbar;

public final class UiStyleHelper {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String KEY_TIMETABLE_THEME_COLOR = "timetable_theme_color";
    private static final int FROST_BG_DARK = Color.parseColor("#0C1018");
    private static final int FROST_BG_LIGHT = Color.parseColor("#F5F7FA");
    private static final int ON_SURFACE_DARK = Color.parseColor("#F3F6FF");
    private static final int ON_SURFACE_LIGHT = Color.parseColor("#141923");

    private UiStyleHelper() {}

    public static int resolvePageBackgroundColor(Context context) {
        boolean darkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return darkMode ? FROST_BG_DARK : FROST_BG_LIGHT;
    }

    public static int resolveOnSurfaceColor(Context context) {
        boolean darkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return darkMode ? ON_SURFACE_DARK : ON_SURFACE_LIGHT;
    }

    public static int resolveOnSurfaceVariantColor(Context context) {
        return ColorUtils.setAlphaComponent(resolveOnSurfaceColor(context), 178);
    }

    public static int resolveOutlineColor(Context context) {
        return ColorUtils.setAlphaComponent(resolveOnSurfaceColor(context), 56);
    }

    public static int resolveAccentColor(Context context) {
        return context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE)
                .getInt(KEY_TIMETABLE_THEME_COLOR, ColorPaletteProvider.defaultThemeColor());
    }

    public static void hideStatusBar(AppCompatActivity activity) {
        if (activity == null) return;
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.statusBars());
        }
    }

    public static int resolveGlassCardColor(Context context) {
        boolean darkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        return darkMode
                ? ColorUtils.setAlphaComponent(Color.parseColor("#1C1D21"), 190)
                : ColorUtils.setAlphaComponent(Color.WHITE, 168);
    }

    public static void styleGlassCard(MaterialCardView card, Context context) {
        if (card == null) return;
        int onSurface = resolveOnSurfaceColor(context);
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(resolveGlassCardColor(context));
    }

    public static void styleGlassToolbar(MaterialToolbar toolbar, Context context) {
        if (toolbar == null) return;
        int onSurface = resolveOnSurfaceColor(context);
        toolbar.setBackgroundColor(resolveGlassCardColor(context));
        toolbar.setTitleTextColor(onSurface);
        toolbar.setElevation(0f);
        if (toolbar.getNavigationIcon() != null) {
            toolbar.setNavigationIconTint(onSurface);
        }
        View parent = (View) toolbar.getParent();
        if (parent instanceof AppBarLayout) {
            AppBarLayout appBar = (AppBarLayout) parent;
            appBar.setBackgroundColor(Color.TRANSPARENT);
            appBar.setElevation(0f);
        }
    }

    public static void applySecondaryPageBackground(View root, Context context) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup rootGroup = (ViewGroup) root;
        int bgColor = resolvePageBackgroundColor(context);
        rootGroup.setBackgroundColor(bgColor);
    }

    public static void applyGlassCards(View root, Context context) {
        if (root == null) return;
        if (root instanceof MaterialCardView) {
            styleGlassCard((MaterialCardView) root, context);
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyGlassCards(vg.getChildAt(i), context);
            }
        }
    }

}