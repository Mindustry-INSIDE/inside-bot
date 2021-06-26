package inside.util;

import reactor.util.context.Context;

import java.util.function.UnaryOperator;

public abstract class ContextUtil{
    public static final String KEY_LOCALE = "inside.locale";
    public static final String KEY_TIMEZONE = "inside.timezone";
    public static final String KEY_REPLY = "inside.reply";
    public static final String KEY_EPHEMERAL = "inside.ephemeral";

    private ContextUtil(){}

    public static UnaryOperator<Context> reset(){
        return ctx -> ctx.delete(KEY_LOCALE)
                .delete(KEY_TIMEZONE)
                .delete(KEY_REPLY)
                .delete(KEY_EPHEMERAL);
    }
}
