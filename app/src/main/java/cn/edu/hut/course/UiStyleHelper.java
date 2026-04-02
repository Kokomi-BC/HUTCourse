package cn.edu.hut.course;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.appbar.MaterialToolbar;

public final class UiStyleHelper {

    private static final String PREF_COURSE_STORAGE = "course_storage";
    private static final String TAG_BG_IMAGE = "secondary_bg_image";
    private static final String TAG_BG_SCRIM = "secondary_bg_scrim";

    private UiStyleHelper() {}

    public static int resolvePageBackgroundColor(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE);
        int[] palette = buildBackgroundPalette(context);
        int bgIndex = prefs.getInt("bg_color_index", 0);
        if (bgIndex < 0 || bgIndex >= palette.length) {
            bgIndex = 0;
        }
        return palette[bgIndex];
    }

    public static void hideStatusBar(AppCompatActivity activity) {
        if (activity == null) return;
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars());
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
        int onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        card.setCardElevation(0f);
        card.setStrokeWidth(1);
        card.setStrokeColor(ColorUtils.setAlphaComponent(onSurface, 24));
        card.setCardBackgroundColor(resolveGlassCardColor(context));
    }

    public static void styleGlassToolbar(MaterialToolbar toolbar, Context context) {
        if (toolbar == null) return;
        int onSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
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
        SharedPreferences prefs = context.getSharedPreferences(PREF_COURSE_STORAGE, Context.MODE_PRIVATE);
        String mode = prefs.getString("bg_mode", "color");
        String uriStr = prefs.getString("bg_image_uri", "");
        boolean darkMode = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int bgColor = resolvePageBackgroundColor(context);
        ImageView bgImage = ensureBackgroundImage(rootGroup, context);
        View bgScrim = ensureBackgroundScrim(rootGroup, context);

        if ("image".equals(mode) && uriStr != null && !uriStr.isEmpty()) {
            boolean loaded = false;
            try {
                bgImage.setImageURI(Uri.parse(uriStr));
                loaded = true;
            } catch (Exception ignored) {
                loaded = false;
            }
            if (loaded) {
                rootGroup.setBackgroundColor(Color.TRANSPARENT);
                bgImage.setVisibility(View.VISIBLE);
                bgScrim.setVisibility(View.VISIBLE);
                bgScrim.setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, darkMode ? 110 : 58));
                return;
            }
        }

        bgImage.setVisibility(View.GONE);
        bgScrim.setVisibility(View.GONE);
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

    private static ImageView ensureBackgroundImage(ViewGroup root, Context context) {
        View found = root.findViewWithTag(TAG_BG_IMAGE);
        if (found instanceof ImageView) {
            return (ImageView) found;
        }
        ImageView image = new ImageView(context);
        image.setTag(TAG_BG_IMAGE);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(image, 0);
        return image;
    }

    private static View ensureBackgroundScrim(ViewGroup root, Context context) {
        View found = root.findViewWithTag(TAG_BG_SCRIM);
        if (found != null) {
            return found;
        }
        View scrim = new View(context);
        scrim.setTag(TAG_BG_SCRIM);
        scrim.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(scrim, 1);
        return scrim;
    }

    private static int[] buildBackgroundPalette(Context context) {
        int colorSurface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, ContextCompat.getColor(context, android.R.color.white));
        int p = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, colorSurface);
        int s = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondaryContainer, colorSurface);
        int t = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiaryContainer, colorSurface);
        int pv = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, colorSurface);
        int sv = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, colorSurface);
        int tv = MaterialColors.getColor(context, com.google.android.material.R.attr.colorTertiary, colorSurface);

        return new int[] {
                colorSurface,
                ColorUtils.blendARGB(colorSurface, p, 0.22f),
                ColorUtils.blendARGB(colorSurface, s, 0.22f),
                ColorUtils.blendARGB(colorSurface, t, 0.22f),
                ColorUtils.blendARGB(colorSurface, p, 0.35f),
                ColorUtils.blendARGB(colorSurface, s, 0.35f),
                ColorUtils.blendARGB(colorSurface, t, 0.35f),
                ColorUtils.blendARGB(colorSurface, pv, 0.16f),
                ColorUtils.blendARGB(colorSurface, sv, 0.16f),
                ColorUtils.blendARGB(colorSurface, tv, 0.16f),
                ColorUtils.blendARGB(colorSurface, pv, 0.28f),
                ColorUtils.blendARGB(colorSurface, sv, 0.28f)
        };
    }
}