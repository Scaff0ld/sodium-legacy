package me.sodiumlegacy.mod.client.ctm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates connected-texture tiles for glass at load time from whatever glass texture the
 * active resource pack provides, so no third-party tile art is shipped.
 *
 * <p>A glass texture has a frame (the outer ring of pixels). For every edge that touches an
 * identical neighbor, that part of the frame is painted with the glass interior color instead;
 * a corner pixel is only cleared when both of its edges are connected <i>and</i> the diagonal
 * neighbor is connected too, which leaves the little inner-corner dot where it belongs.</p>
 *
 * <p>Canonical mask bits: 0 top, 1 right, 2 bottom, 3 left, 4 top-right, 5 bottom-right,
 * 6 bottom-left, 7 top-left (a corner bit is only ever set when both adjacent edges are set).</p>
 */
public final class GlassTileGenerator {
    public static final int TOP = 1, RIGHT = 2, BOTTOM = 4, LEFT = 8, TR = 16, BR = 32, BL = 64, TL = 128;

    private GlassTileGenerator() {
    }

    public static int canonicalMask(boolean top, boolean right, boolean bottom, boolean left,
                                    boolean tr, boolean br, boolean bl, boolean tl) {
        int m = 0;
        if (top) m |= TOP;
        if (right) m |= RIGHT;
        if (bottom) m |= BOTTOM;
        if (left) m |= LEFT;
        if (top && right && tr) m |= TR;
        if (bottom && right && br) m |= BR;
        if (bottom && left && bl) m |= BL;
        if (top && left && tl) m |= TL;
        return m;
    }

    /** @return the 47 distinct canonical masks */
    public static int[] allMasks() {
        Map<Integer, Boolean> seen = new HashMap<>();

        for (int edges = 0; edges < 16; edges++) {
            for (int corners = 0; corners < 16; corners++) {
                int m = canonicalMask((edges & 1) != 0, (edges & 2) != 0, (edges & 4) != 0, (edges & 8) != 0,
                        (corners & 1) != 0, (corners & 2) != 0, (corners & 4) != 0, (corners & 8) != 0);
                seen.put(m, Boolean.TRUE);
            }
        }

        int[] result = new int[seen.size()];
        int i = 0;

        for (Integer m : seen.keySet()) {
            result[i++] = m;
        }

        return result;
    }

    public static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    public static byte[] generate(BufferedImage base, int mask) throws IOException {
        int w = base.getWidth();
        int h = Math.min(base.getHeight(), w); // animated strips: use the first frame only
        int t = Math.max(1, w / 16);
        int fill = interiorColor(base, w, h, t);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = base.getRGB(x, y);

                boolean inTop = y < t, inBottom = y >= h - t, inLeft = x < t, inRight = x >= w - t;

                if (inTop || inBottom || inLeft || inRight) {
                    if (shouldClear(mask, inTop, inRight, inBottom, inLeft)) {
                        argb = fill;
                    }
                }

                out.setRGB(x, y, argb);
            }
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bytes);

        return bytes.toByteArray();
    }

    private static boolean shouldClear(int mask, boolean inTop, boolean inRight, boolean inBottom, boolean inLeft) {
        boolean top = (mask & TOP) != 0, right = (mask & RIGHT) != 0, bottom = (mask & BOTTOM) != 0, left = (mask & LEFT) != 0;

        // Corner pixels belong to two edges.
        if (inTop && inLeft) return top && left && (mask & TL) != 0;
        if (inTop && inRight) return top && right && (mask & TR) != 0;
        if (inBottom && inLeft) return bottom && left && (mask & BL) != 0;
        if (inBottom && inRight) return bottom && right && (mask & BR) != 0;

        if (inTop) return top;
        if (inBottom) return bottom;
        if (inLeft) return left;
        return right;
    }

    /** Most common pixel of the inside area: transparent for clear glass, the tint for stained glass. */
    private static int interiorColor(BufferedImage img, int w, int h, int t) {
        Map<Integer, Integer> counts = new HashMap<>();
        int best = 0;
        int bestCount = -1;

        for (int y = t; y < h - t; y++) {
            for (int x = t; x < w - t; x++) {
                int c = img.getRGB(x, y);
                int n = counts.containsKey(c) ? counts.get(c) + 1 : 1;
                counts.put(c, n);

                if (n > bestCount) {
                    bestCount = n;
                    best = c;
                }
            }
        }

        return best;
    }
}
