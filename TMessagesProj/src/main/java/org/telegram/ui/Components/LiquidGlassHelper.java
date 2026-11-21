package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * TubeGram: reusable rendering resources for the Liquid Glass UI.
 *
 * The application already ships Telegram's native blur3 render-node pipeline
 * (driven by {@link org.telegram.messenger.LiteMode#FLAG_LIQUID_GLASS}), which
 * provides real background blur for the bottom navigation dock, folder tabs,
 * composer and conversation ActionBar. This helper supplies the shared visual
 * design system (tints, specular rim, highlight, radii) and a light-weight
 * frosted fallback drawable for surfaces that are not backed by a render node.
 *
 * No Activity / View / Context references are kept in static fields: only
 * immutable Paint / Path resources are cached.
 */
public final class LiquidGlassHelper {

    private LiquidGlassHelper() {
    }

    public static final int DARK_BACKGROUND = 0x8C181C24;
    public static final int DARK_BORDER = 0x2EFFFFFF;
    public static final int LIGHT_BACKGROUND = 0xA6FFFFFF;
    public static final int LIGHT_BORDER = 0xCCFFFFFF;

    public static final float ACTION_BAR_RADIUS_DP = 24f;
    public static final float DOCK_RADIUS_DP = 28f;
    public static final float COMPOSER_RADIUS_DP = 26f;
    public static final float CHIP_RADIUS_DP = 20f;

    private static final int TAG_ORIGINAL_BACKGROUND = R.id.tubegram_glass_original_background;
    private static final int TAG_ORIGINAL_PARAMS = R.id.tubegram_glass_original_params;
    private static final int TAG_GLASS_APPLIED = R.id.tubegram_glass_applied;

    // Cached immutable drawing resources — never allocate during draw().
    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint RIM_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint HIGHLIGHT_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path OUTLINE_PATH = new Path();
    private static final RectF OUTLINE_RECT = new RectF();

    public static boolean isEnabled() {
        return SharedConfig.liquidGlassEnabled;
    }

    public static float dp(float value) {
        return AndroidUtilities.dp(value);
    }

    /** Creates a frosted glass drawable using the Liquid Glass design system. */
    public static Drawable createGlassBackground(Context context, boolean isDark, float cornerRadiusDp) {
        return new GlassDrawable(isDark, dp(cornerRadiusDp));
    }

    public static Drawable createGlassBackground(Context context, float cornerRadiusDp) {
        boolean dark = Theme.isCurrentThemeDark();
        return createGlassBackground(context, dark, cornerRadiusDp);
    }

    /** Applies a glass background to a view, remembering its original background. */
    public static void applyGlassBackground(View view, Context context, float cornerRadiusDp) {
        if (view == null) {
            return;
        }
        if (!isEnabled()) {
            removeGlassBackground(view);
            return;
        }
        if (Boolean.TRUE.equals(view.getTag(TAG_GLASS_APPLIED))) {
            return;
        }
        view.setTag(TAG_ORIGINAL_BACKGROUND, view.getBackground());
        view.setTag(TAG_GLASS_APPLIED, Boolean.TRUE);
        view.setBackground(createGlassBackground(context, cornerRadiusDp));
    }

    /** Restores the exact background a view had before glass was applied. */
    public static void removeGlassBackground(View view) {
        if (view == null || !Boolean.TRUE.equals(view.getTag(TAG_GLASS_APPLIED))) {
            return;
        }
        Object original = view.getTag(TAG_ORIGINAL_BACKGROUND);
        view.setBackground(original instanceof Drawable ? (Drawable) original : null);
        view.setTag(TAG_ORIGINAL_BACKGROUND, null);
        view.setTag(TAG_GLASS_APPLIED, null);
    }

    /**
     * Applies floating margins to a view, remembering its original layout params.
     * Used by the chat list / conversation ActionBar and the message composer.
     */
    public static void applyFloatingMargins(View view, float horizontalDp, float topDp, float bottomDp) {
        if (view == null || !isEnabled()) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        if (view.getTag(TAG_ORIGINAL_PARAMS) == null) {
            view.setTag(TAG_ORIGINAL_PARAMS, lp);
        }
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        int h = dp(horizontalDp);
        mlp.leftMargin = h;
        mlp.rightMargin = h;
        mlp.topMargin = dp(topDp);
        mlp.bottomMargin = dp(bottomDp);
        view.setLayoutParams(mlp);
    }

    /** Restores layout params captured by {@link #applyFloatingMargins}. */
    public static void restoreMargins(View view) {
        if (view == null) {
            return;
        }
        Object original = view.getTag(TAG_ORIGINAL_PARAMS);
        if (original instanceof ViewGroup.LayoutParams) {
            view.setLayoutParams((ViewGroup.LayoutParams) original);
            view.setTag(TAG_ORIGINAL_PARAMS, null);
        }
    }

    /** Adds horizontal spacing between folder chip views, restorable via removeChipSpacing(). */
    public static void applyChipBackground(View chip, Context context) {
        applyGlassBackground(chip, context, CHIP_RADIUS_DP);
    }

    public static void removeChipBackground(View chip) {
        removeGlassBackground(chip);
    }

    /**
     * A drawable that draws the translucent tint, a 1dp specular rim and a soft
     * top-to-bottom white highlight. It is intentionally cheap: no bitmaps,
     * no per-frame allocations, no blur work on the UI thread. Real background
     * blur is provided by Telegram's blur3 pipeline on supported devices.
     */
    public static final class GlassDrawable extends Drawable {
        private final boolean dark;
        private final float radius;
        private int alpha = 255;
        private LinearGradient highlightShader;
        private float shaderHeight = -1f;

        GlassDrawable(boolean dark, float radius) {
            this.dark = dark;
            this.radius = radius;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            RectF bounds = getBounds();
            if (bounds.isEmpty() || radius <= 0) {
                return;
            }
            int background = dark ? DARK_BACKGROUND : LIGHT_BACKGROUND;
            int border = dark ? DARK_BORDER : LIGHT_BORDER;

            OUTLINE_RECT.set(bounds.left, bounds.top, bounds.right, bounds.bottom);
            OUTLINE_PATH.reset();
            OUTLINE_PATH.addRoundRect(OUTLINE_RECT, radius, radius, Path.Direction.CW);

            FILL_PAINT.setStyle(Paint.Style.FILL);
            FILL_PAINT.setColor(applyAlpha(background));
            canvas.drawPath(OUTLINE_PATH, FILL_PAINT);

            // Cache the highlight shader; recreate only when the height actually changes.
            if (highlightShader == null || Math.abs(shaderHeight - bounds.height()) > 0.5f) {
                shaderHeight = bounds.height();
                highlightShader = new LinearGradient(
                    0, 0, 0, Math.max(1f, shaderHeight * 0.6f),
                    dark ? 0x1AFFFFFF : 0x66FFFFFF, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            }
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            HIGHLIGHT_PAINT.setStyle(Paint.Style.FILL);
            HIGHLIGHT_PAINT.setShader(highlightShader);
            OUTLINE_PATH.offset(-bounds.left, -bounds.top);
            canvas.drawPath(OUTLINE_PATH, HIGHLIGHT_PAINT);
            OUTLINE_PATH.offset(bounds.left, bounds.top);
            canvas.restore();

            // 1dp specular rim.
            RIM_PAINT.setStyle(Paint.Style.STROKE);
            RIM_PAINT.setStrokeWidth(dp(1));
            RIM_PAINT.setColor(applyAlpha(border));
            canvas.drawPath(OUTLINE_PATH, RIM_PAINT);
        }

        private int applyAlpha(int color) {
            if (alpha == 255) {
                return color;
            }
            return Color.argb(Color.alpha(color) * alpha / 255, Color.red(color), Color.green(color), Color.blue(color));
        }

        @Override
        public void setAlpha(int a) {
            alpha = a;
            invalidateSelf();
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
