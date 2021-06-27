package inside.util;

import reactor.util.annotation.Nullable;

public abstract class Strings{

    private Strings(){

    }

    public static boolean isEmpty(@Nullable CharSequence cs){
        return cs == null || cs.length() == 0 ||
                cs instanceof String s && s.isBlank();
    }

    public static boolean isNotEmpty(@Nullable CharSequence cs){
        return !isEmpty(cs);
    }

    public static int parseInt(String s){
        return parseInt(s, Integer.MIN_VALUE);
    }

    public static int parseInt(String s, int defaultValue){
        return Try.ofCallable(() -> Integer.parseInt(s)).orElse(defaultValue);
    }

    public static boolean canParseLong(String s){
        return Try.ofCallable(() -> Long.parseLong(s)).isSuccess();
    }

    public static long parseLong(String s){
        return parseLong(s, Long.MIN_VALUE);
    }

    public static long parseLong(String s, long defaultValue){
        return Try.ofCallable(() -> Long.parseLong(s)).orElse(defaultValue);
    }

    public static int levenshtein(CharSequence x, CharSequence y){
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for(int i = 0; i <= x.length(); i++){
            for(int j = 0; j <= y.length(); j++){
                if(i == 0){
                    dp[i][j] = j;
                }else if(j == 0){
                    dp[i][j] = i;
                }else{
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j - 1] + (x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1),
                            dp[i - 1][j] + 1),
                            dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }
}
