package inside.util;

public final class Mathf{
    private static final float FLOAT_ROUNDING_ERROR = 0.000001f;
    private static final int BIG_ENOUGH_INT = 16 * 1024;
    private static final double BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT;
    private static final double CEIL = 0.9999999;
    private static final double BIG_ENOUGH_ROUND = BIG_ENOUGH_INT + 0.5f;

    public static int digits(int n){
        return n < 100000 ?
               n < 100 ?
               n < 10 ? 1 : 2 :
               n < 1000 ? 3 :
               n < 10000 ? 4 : 5 :
               n < 10000000 ?
               n < 1000000 ? 6 : 7 :
               n < 100000000 ? 8 :
               n < 1000000000 ? 9 : 10;
    }

    public static int digits(long n){
        return n == 0 ? 1 : (int)(Math.log10(n) + 1);
    }

    public static int sign(float f){
        return f < 0 ? -1 : 1;
    }

    public static int sign(boolean b){
        return b ? 1 : -1;
    }

    public static int num(boolean b){
        return b ? 1 : 0;
    }

    public static float pow(float a, float b){
        return (float)Math.pow(a, b);
    }

    public static int pow(int a, int b){
        return (int)Math.ceil(Math.pow(a, b));
    }

    public static int nextPowerOfTwo(int value){
        if(value == 0) return 1;
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        return value + 1;
    }

    public static boolean isPowerOfTwo(int value){
        return value != 0 && (value & value - 1) == 0;
    }

    public static short max(short a, short b) {
        return a >= b ? a : b;
    }

    public static short min(short a, short b) {
        return a <= b ? a : b;
    }

    public static short clamp(short value, short min, short max){
        return max(min, min(max, value));
    }

    public static int clamp(int value, int min, int max){
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max){
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max){
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value){
        return clamp(value, 0f, 1f);
    }

    public static double clamp(double value, double min, double max){
        return Math.max(min, Math.min(max, value));
    }

    public static float maxZero(float val){
        return Math.max(val, 0);
    }

    public static int floor(float value){
        return (int)(value + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
    }

    public static int floorPositive(float value){
        return (int)value;
    }

    public static int ceil(float value){
        return BIG_ENOUGH_INT - (int)(BIG_ENOUGH_FLOOR - value);
    }

    public static int ceilPositive(float value){
        return (int)(value + CEIL);
    }

    public static int round(float value){
        return (int)(value + BIG_ENOUGH_ROUND) - BIG_ENOUGH_INT;
    }

    public static int round(int value, int step){
        return value / step * step;
    }

    public static float round(float value, float step){
        return (int)(value / step) * step;
    }

    public static int round(float value, int step){
        return (int)(value / step) * step;
    }

    public static int roundPositive(float value){
        return (int)(value + 0.5f);
    }

    public static boolean zero(float value){
        return Math.abs(value) <= FLOAT_ROUNDING_ERROR;
    }

    public static boolean zero(double value){
        return Math.abs(value) <= FLOAT_ROUNDING_ERROR;
    }

    public static boolean zero(float value, float tolerance){
        return Math.abs(value) <= tolerance;
    }

    public static boolean equal(float a, float b){
        return Math.abs(a - b) <= FLOAT_ROUNDING_ERROR;
    }

    public static boolean equal(float a, float b, float tolerance){
        return Math.abs(a - b) <= tolerance;
    }

    public static float log(float a, float value){
        return (float)(Math.log(value) / Math.log(a));
    }

    public static float log2(float value){
        return (float)Math.log(value) / 0.301029996f;
    }

    public static float mod(float f, float n){
        return (f % n + n) % n;
    }

    public static int mod(int x, int n){
        return (x % n + n) % n;
    }
}
