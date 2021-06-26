package inside.util;

import reactor.util.annotation.Nullable;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class DurationFormat{

    private static final String BUNDLE_NAME = "duration_messages";

    private static final ConcurrentMap<Locale, DurationFormatter> FORMATTERS = new ConcurrentHashMap<>();

    protected DurationFormat(){
    }

    public static DurationFormatter getDefault(){
        return wordBased(Locale.ENGLISH);
    }

    public static DurationFormatter wordBased(){
        return wordBased(Locale.getDefault());
    }

    public static DurationFormatter wordBased(Locale locale){
        DurationFormatter formatter = FORMATTERS.get(locale);
        if(formatter == null){
            DynamicWordBased dynamic = new DynamicWordBased(buildWordBased(locale));
            formatter = new DurationFormatter(dynamic, locale);
            DurationFormatter existing = FORMATTERS.putIfAbsent(locale, formatter);
            if(existing != null){
                formatter = existing;
            }
        }
        return formatter;
    }

    private static DurationFormatter buildWordBased(Locale locale){
        ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
        if(bundle.containsKey("duration-format.regex.separator")){
            return buildRegExFormatter(bundle, locale);
        }
        return buildNonRegExFormatter(bundle, locale);
    }

    private static DurationFormatter buildRegExFormatter(ResourceBundle bundle, Locale locale){
        String regExSeparator = bundle.getString("duration-format.regex.separator");

        DurationFormatBuilder builder = new DurationFormatBuilder();
        builder.appendYears();
        if(bundle.containsKey("duration-format.years.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.years.regex").split(regExSeparator),
                    bundle.getString("duration-format.years.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.year"), bundle.getString("duration-format.years"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendMonths();
        if(bundle.containsKey("duration-format.months.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.months.regex").split(regExSeparator),
                    bundle.getString("duration-format.months.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.month"), bundle.getString("duration-format.months"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendWeeks();
        if(bundle.containsKey("duration-format.weeks.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.weeks.regex").split(regExSeparator),
                    bundle.getString("duration-format.weeks.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.week"), bundle.getString("duration-format.weeks"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendDays();
        if(bundle.containsKey("duration-format.days.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.days.regex").split(regExSeparator),
                    bundle.getString("duration-format.days.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.day"), bundle.getString("duration-format.days"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendHours();
        if(bundle.containsKey("duration-format.hours.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.hours.regex").split(regExSeparator),
                    bundle.getString("duration-format.hours.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.hour"), bundle.getString("duration-format.hours"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendMinutes();
        if(bundle.containsKey("duration-format.minutes.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.minutes.regex").split(regExSeparator),
                    bundle.getString("duration-format.minutes.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.minute"), bundle.getString("duration-format.minutes"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendSeconds();
        if(bundle.containsKey("duration-format.seconds.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.seconds.regex").split(regExSeparator),
                    bundle.getString("duration-format.seconds.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.second"), bundle.getString("duration-format.seconds"));
        }

        builder.appendSeparator(bundle.getString("duration-format.comma-space"), bundle.getString("duration-format.space-and-space"));
        builder.appendMillis();
        if(bundle.containsKey("duration-format.milliseconds.regex")){
            builder.appendSuffix(
                    bundle.getString("duration-format.milliseconds.regex").split(regExSeparator),
                    bundle.getString("duration-format.milliseconds.list").split(regExSeparator));
        }else{
            builder.appendSuffix(bundle.getString("duration-format.millisecond"), bundle.getString("duration-format.milliseconds"));
        }
        return builder.toFormatter().withLocale(locale);
    }

    private static DurationFormatter buildNonRegExFormatter(ResourceBundle bundle, Locale locale){
        return new DurationFormatBuilder()
                .appendYears()
                .appendSuffix(bundle.getString("duration-format.year"), bundle.getString("duration-format.years"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendMonths()
                .appendSuffix(bundle.getString("duration-format.month"), bundle.getString("duration-format.months"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendWeeks()
                .appendSuffix(bundle.getString("duration-format.week"), bundle.getString("duration-format.weeks"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendDays()
                .appendSuffix(bundle.getString("duration-format.day"), bundle.getString("duration-format.days"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendHours()
                .appendSuffix(bundle.getString("duration-format.hour"), bundle.getString("duration-format.hours"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendMinutes()
                .appendSuffix(bundle.getString("duration-format.minute"), bundle.getString("duration-format.minutes"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendSeconds()
                .appendSuffix(bundle.getString("duration-format.second"), bundle.getString("duration-format.seconds"))
                .appendSeparator(bundle.getString("duration-format.comma-space"),
                        bundle.getString("duration-format.space-and-space"))
                .appendMillis()
                .appendSuffix(bundle.getString("duration-format.millisecond"), bundle.getString("duration-format.milliseconds"))
                .toFormatter().withLocale(locale);
    }

    static class DynamicWordBased implements DurationPrinter{
        private final DurationFormatter formatter;

        DynamicWordBased(DurationFormatter formatter){
            this.formatter = Objects.requireNonNull(formatter, "formatter");
        }

        @Override
        public int countFieldsToPrint(Duration duration, int stopAt, Locale locale){
            return getPrinter(locale).countFieldsToPrint(duration, stopAt, locale);
        }

        @Override
        public int calculatePrintedLength(Duration duration, Locale locale){
            return getPrinter(locale).calculatePrintedLength(duration, locale);
        }

        @Override
        public void printTo(StringBuffer buf, Duration duration, Locale locale){
            getPrinter(locale).printTo(buf, duration, locale);
        }

        @Override
        public void printTo(Writer out, Duration duration, Locale locale) throws IOException{
            getPrinter(locale).printTo(out, duration, locale);
        }

        private DurationPrinter getPrinter(@Nullable Locale locale){
            if(locale != null && !locale.equals(formatter.getLocale())){
                return wordBased(locale).getPrinter();
            }
            return formatter.getPrinter();
        }
    }
}
