package dev.litebuild;

/** Conversión sRGB (0xRRGGBB) a CIELAB (iluminante D65): L en 0-100, a y b en torno a -128..127. */
public final class Lab {
    private Lab() {
    }

    public static double[] fromRgb(int rgb) {
        double r = linear(((rgb >> 16) & 255) / 255.0), g = linear(((rgb >> 8) & 255) / 255.0), b = linear((rgb & 255) / 255.0);
        double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
        double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
        double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double f(double t) {
        return t > 216.0 / 24389.0 ? Math.cbrt(t) : (24389.0 / 27.0 * t + 16) / 116;
    }
}
