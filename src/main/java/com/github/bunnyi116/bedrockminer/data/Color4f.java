package com.github.bunnyi116.bedrockminer.data;

// 浮点型（百分比1=100%）
public class Color4f {
    private float red;
    private float green;
    private float blue;
    private float alpha;

    private Color4f(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 从浮点值创建颜色 (0.0-1.0)
     */
    public static Color4f fromFloat(float red, float green, float blue, float alpha) {
        return new Color4f(
                clampFloat(red),
                clampFloat(green),
                clampFloat(blue),
                clampFloat(alpha)
        );
    }

    /**
     * 从浮点值创建颜色，alpha默认为1.0
     */
    public static Color4f fromFloat(float red, float green, float blue) {
        return fromFloat(red, green, blue, 1.0f);
    }

    /**
     * 从整型值创建颜色 (0-255)
     */
    public static Color4f fromInt(int red, int green, int blue, int alpha) {
        return new Color4f(
                intToFloat(clampInt(red)),
                intToFloat(clampInt(green)),
                intToFloat(clampInt(blue)),
                intToFloat(clampInt(alpha))
        );
    }

    /**
     * 从整型值创建颜色，alpha默认为255
     */
    public static Color4f fromInt(int red, int green, int blue) {
        return fromInt(red, green, blue, 255);
    }

    /**
     * 从RGB整数值创建颜色 (0xRRGGBB)
     */
    public static Color4f fromRGB(int rgb) {
        return fromInt(
                (rgb >> 16) & 0xFF,
                (rgb >> 8) & 0xFF,
                rgb & 0xFF
        );
    }

    /**
     * 从ARGB整数值创建颜色 (0xAARRGGBB)
     */
    public static Color4f fromARGB(int argb) {
        return fromInt(
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                (argb >> 24) & 0xFF
        );
    }

    /**
     * 从RGBA整数值创建颜色 (0xRRGGBBAA)
     */
    public static Color4f fromRGBA(int rgba) {
        return fromInt(
                (rgba >> 24) & 0xFF,
                (rgba >> 16) & 0xFF,
                (rgba >> 8) & 0xFF,
                rgba & 0xFF
        );
    }

    /**
     * 从十六进制字符串创建颜色 (#RRGGBB 或 #RRGGBBAA 或 #RGB 或 #RGBA)
     */
    public static Color4f fromHex(String hex) {
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        if (hex.length() == 3) {
            // #RGB -> #RRGGBB
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        } else if (hex.length() == 4) {
            // #RGBA -> #RRGGBBAA
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2) + hex.charAt(3) + hex.charAt(3);
        }

        if (hex.length() == 6) {
            return fromRGB(Integer.parseInt(hex, 16));
        } else if (hex.length() == 8) {
            return fromRGBA((int) Long.parseLong(hex, 16));
        } else {
            throw new IllegalArgumentException("Invalid hex color format: " + hex);
        }
    }

    /**
     * 从HSV颜色空间创建颜色
     * @param hue 色相 (0-360)
     * @param saturation 饱和度 (0-1)
     * @param value 明度 (0-1)
     * @param alpha 透明度 (0-1)
     */
    public static Color4f fromHSV(float hue, float saturation, float value, float alpha) {
        hue = hue % 360;
        if (hue < 0) hue += 360;
        saturation = clampFloat(saturation);
        value = clampFloat(value);
        alpha = clampFloat(alpha);

        float c = value * saturation;
        float x = c * (1 - Math.abs((hue / 60) % 2 - 1));
        float m = value - c;

        float r, g, b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return new Color4f(r + m, g + m, b + m, alpha);
    }

    /**
     * 从HSV颜色空间创建颜色，alpha默认为1.0
     */
    public static Color4f fromHSV(float hue, float saturation, float value) {
        return fromHSV(hue, saturation, value, 1.0f);
    }

    /**
     * 创建灰度颜色
     */
    public static Color4f grayscale(float intensity, float alpha) {
        intensity = clampFloat(intensity);
        alpha = clampFloat(alpha);
        return new Color4f(intensity, intensity, intensity, alpha);
    }

    /**
     * 创建灰度颜色，alpha默认为1.0
     */
    public static Color4f grayscale(float intensity) {
        return grayscale(intensity, 1.0f);
    }

    // ========== 常用颜色常量 ==========

    public static final Color4f WHITE = fromFloat(1.0f, 1.0f, 1.0f);
    public static final Color4f BLACK = fromFloat(0.0f, 0.0f, 0.0f);
    public static final Color4f RED = fromFloat(1.0f, 0.0f, 0.0f);
    public static final Color4f GREEN = fromFloat(0.0f, 1.0f, 0.0f);
    public static final Color4f BLUE = fromFloat(0.0f, 0.0f, 1.0f);
    public static final Color4f YELLOW = fromFloat(1.0f, 1.0f, 0.0f);
    public static final Color4f CYAN = fromFloat(0.0f, 1.0f, 1.0f);
    public static final Color4f MAGENTA = fromFloat(1.0f, 0.0f, 1.0f);
    public static final Color4f TRANSPARENT = fromFloat(0.0f, 0.0f, 0.0f, 0.0f);

    // ========== 转换方法 ==========

    /**
     * 转换为RGB整数值 (0xRRGGBB)
     */
    public int toRGB() {
        return (floatToInt(red) << 16) | (floatToInt(green) << 8) | floatToInt(blue);
    }

    /**
     * 转换为ARGB整数值 (0xAARRGGBB)
     */
    public int toARGB() {
        return (floatToInt(alpha) << 24) | (floatToInt(red) << 16) | (floatToInt(green) << 8) | floatToInt(blue);
    }

    /**
     * 转换为RGBA整数值 (0xRRGGBBAA)
     */
    public int toRGBA() {
        return (floatToInt(red) << 24) | (floatToInt(green) << 16) | (floatToInt(blue) << 8) | floatToInt(alpha);
    }

    /**
     * 转换为十六进制字符串 (#RRGGBBAA)
     */
    public String toHex() {
        return String.format("#%08X", toRGBA());
    }

    /**
     * 转换为十六进制字符串，不带alpha (#RRGGBB)
     */
    public String toHexNoAlpha() {
        return String.format("#%06X", toRGB());
    }

    // ========== 工具方法 ==========

    private static float clampFloat(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int clampInt(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float intToFloat(int value) {
        return value / 255.0f;
    }

    private static int floatToInt(float value) {
        return Math.round(value * 255);
    }

    // ========== Getter和Setter ==========

    public float getRed() {
        return red;
    }

    public void setRed(float red) {
        this.red = clampFloat(red);
    }

    public float getGreen() {
        return green;
    }

    public void setGreen(float green) {
        this.green = clampFloat(green);
    }

    public float getBlue() {
        return blue;
    }

    public void setBlue(float blue) {
        this.blue = clampFloat(blue);
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = clampFloat(alpha);
    }

    // ========== 其他实用方法 ==========

    /**
     * 创建该颜色的变体（调整亮度）
     */
    public Color4f withBrightness(float multiplier) {
        return new Color4f(
                clampFloat(red * multiplier),
                clampFloat(green * multiplier),
                clampFloat(blue * multiplier),
                alpha
        );
    }

    /**
     * 创建该颜色的变体（调整透明度）
     */
    public Color4f withAlpha(float alpha) {
        return new Color4f(red, green, blue, clampFloat(alpha));
    }

    /**
     * 线性插值 between two colors
     */
    public static Color4f lerp(Color4f start, Color4f end, float t) {
        t = clampFloat(t);
        return new Color4f(
                start.red + (end.red - start.red) * t,
                start.green + (end.green - start.green) * t,
                start.blue + (end.blue - start.blue) * t,
                start.alpha + (end.alpha - start.alpha) * t
        );
    }

    @Override
    public String toString() {
        return String.format("Color4f(%.2f, %.2f, %.2f, %.2f)", red, green, blue, alpha);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Color4f color4f = (Color4f) obj;
        return Float.compare(color4f.red, red) == 0 &&
                Float.compare(color4f.green, green) == 0 &&
                Float.compare(color4f.blue, blue) == 0 &&
                Float.compare(color4f.alpha, alpha) == 0;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(red) * 31 + Float.hashCode(green) * 17 + Float.hashCode(blue) * 7 + Float.hashCode(alpha);
    }
}