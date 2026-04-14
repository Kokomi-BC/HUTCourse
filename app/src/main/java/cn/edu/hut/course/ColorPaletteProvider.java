package cn.edu.hut.course;

import android.graphics.Color;

public final class ColorPaletteProvider {

    private static final int[] VIBRANT_LIGHT_PALETTE = new int[] {
            Color.parseColor("#FFD166"),
            Color.parseColor("#FFB38A"),
            Color.parseColor("#FFA8D8"),
            Color.parseColor("#C3A6FF"),
            Color.parseColor("#8EC5FF"),
            Color.parseColor("#77E4D4"),
            Color.parseColor("#9FE870"),
            Color.parseColor("#FFE36E"),
            Color.parseColor("#FFB3C1"),
            Color.parseColor("#A8B8FF"),
            Color.parseColor("#7EDCF6"),
            Color.parseColor("#8FE6B0")
    };

    private static final int DEFAULT_THEME_COLOR = Color.parseColor("#FFD166");

    private ColorPaletteProvider() {
    }

    public static int[] vibrantLightPalette() {
        return VIBRANT_LIGHT_PALETTE.clone();
    }

    public static int defaultThemeColor() {
        return DEFAULT_THEME_COLOR;
    }
}
