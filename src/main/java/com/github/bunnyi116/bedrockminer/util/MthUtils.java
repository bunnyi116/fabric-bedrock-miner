package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.util.Util;

public class MthUtils {
    private final static float[] SIN = Util.make(new float[65536], fs -> {
        for (int ix = 0; ix < fs.length; ix++) {
            fs[ix] = (float) Math.sin(ix / 10430.378350470453);
        }
    });

    public static float sin(double d) {
        return SIN[(int) ((long) (d * 10430.378350470453) & 65535L)];
    }

    public static float cos(double d) {
        return SIN[(int) ((long) (d * 10430.378350470453 + 16384.0) & 65535L)];
    }
}