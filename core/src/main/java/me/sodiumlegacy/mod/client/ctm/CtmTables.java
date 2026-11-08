package me.sodiumlegacy.mod.client.ctm;

/**
 * Tile selection tables for the OptiFine/MCPatcher "ctm" (47 tile) method.
 *
 * <p>Edges are numbered clockwise starting from the top of the texture:
 * A = top, B = right, C = bottom, D = left; corner c1 sits between A and B, c3 between B and C,
 * c5 between C and D and c7 between D and A. A corner only counts when both of its adjacent
 * edges are connected (that is what yields exactly 47 distinct tiles).</p>
 *
 */
public final class CtmTables {
    /** Quarter turns applied to the edge order (0 = A is the top edge). */
    public static final int EDGE_ROTATION = 0;
    /** Mirrors the clockwise order to counter-clockwise (A stays on top, B becomes left). */
    public static final boolean MIRROR = false;

    private static final int UNKNOWN = -1;

    private CtmTables() {
    }

    /**
     * @param top    neighbor above the face (texture top) is connected
     * @param right  neighbor to the right
     * @param bottom neighbor below
     * @param left   neighbor to the left
     * @param tr     top-right diagonal connected
     * @param br     bottom-right diagonal
     * @param bl     bottom-left diagonal
     * @param tl     top-left diagonal
     * @return tile index 0-46
     */
    public static int ctmTile(boolean top, boolean right, boolean bottom, boolean left,
                              boolean tr, boolean br, boolean bl, boolean tl) {
        // Normalize to the A,B,C,D / c1,c3,c5,c7 order, applying rotation / mirroring.
        boolean[] edges = { top, right, bottom, left };
        boolean[] corners = { tr, br, bl, tl };

        if (MIRROR) {
            boolean[] e = { edges[0], edges[3], edges[2], edges[1] };
            boolean[] c = { corners[3], corners[2], corners[1], corners[0] };
            edges = e;
            corners = c;
        }

        boolean[] re = new boolean[4];
        boolean[] rc = new boolean[4];

        for (int i = 0; i < 4; i++) {
            re[i] = edges[(i + EDGE_ROTATION) & 3];
            rc[i] = corners[(i + EDGE_ROTATION) & 3];
        }

        boolean a = re[0], b = re[1], c = re[2], d = re[3];
        boolean c1 = rc[0] && a && b;
        boolean c3 = rc[1] && b && c;
        boolean c5 = rc[2] && c && d;
        boolean c7 = rc[3] && d && a;

        int tile = lookup(a, b, c, d, c1, c3, c5, c7);

        if (tile == UNKNOWN) {
            tile = lookup(a, b, c, d, c1, c3, c5, false);
        }

        if (tile == UNKNOWN) {
            tile = lookup(a, b, c, d, c1, c3, false, false);
        }

        return tile == UNKNOWN ? 0 : tile;
    }

    private static int lookup(boolean a, boolean b, boolean c, boolean d,
                              boolean c1, boolean c3, boolean c5, boolean c7) {
        int count = (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0) + (d ? 1 : 0);

        switch (count) {
            case 0:
                return 0;
            case 1:
                return a ? 3 : b ? 12 : c ? 1 : 36;
            case 2:
                if (a && b) return c1 ? 15 : 5;
                if (b && c) return c3 ? 13 : 4;
                if (a && c) return 2;
                if (a && d) return c7 ? UNKNOWN : 17;
                if (b && d) return 24;
                return c5 ? 37 : 16; // c && d
            case 3:
                if (!d) { // A,B,C
                    return c1 ? (c3 ? 14 : 29) : (c3 ? 31 : 7);
                }
                if (!a) { // B,C,D
                    if (c5) return c3 ? UNKNOWN : 26;
                    return c3 ? 28 : 6;
                }
                if (!c) { // A,B,D
                    if (c7) return UNKNOWN;
                    return c1 ? 43 : 19;
                }
                // A,C,D
                if (c7) return UNKNOWN;
                return c5 ? 21 : 18;
            default:
                if (c7) return UNKNOWN;
                if (c5) {
                    if (c3) return UNKNOWN;
                    return c1 ? 41 : 38;
                }
                if (c1) return c3 ? 45 : 22;
                return c3 ? 9 : 46;
        }
    }

    /** horizontal / vertical: index by (previous ? 1 : 0) | (next ? 2 : 0). */
    public static final int[] LINEAR = { 3, 2, 0, 1 };
}
