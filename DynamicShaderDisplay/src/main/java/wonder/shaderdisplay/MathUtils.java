package wonder.shaderdisplay;

public class MathUtils {

    public static int pmod(int x, int m) {
        return (x %= m) < 0 ? x+m : x;
    }

    public static float pmod(float x, float m) {
        return (x %= m) < 0 ? x+m : x;
    }

    public static long pmod(long x, long m) {
        return (x %= m) < 0 ? x+m : x;
    }

    public static float wrapInBounds(float x, float min, float max) {
        return min + pmod(x - min, max - min);
    }

}
