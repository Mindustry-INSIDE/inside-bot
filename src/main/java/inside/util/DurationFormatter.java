package inside.util;

import reactor.util.annotation.Nullable;

import java.io.*;
import java.time.Duration;
import java.util.*;

public class DurationFormatter{

    private final DurationPrinter printer;
    @Nullable
    private final Locale locale;

    public DurationFormatter(DurationPrinter printer){
        this(printer, null);
    }

    public DurationFormatter(DurationPrinter printer, @Nullable Locale locale){
        this.printer = Objects.requireNonNull(printer, "printer");
        this.locale = locale;
    }

    public DurationPrinter getPrinter(){
        return printer;
    }

    public DurationFormatter withLocale(@Nullable Locale locale){
        if(Objects.equals(locale, this.locale)){
            return this;
        }
        return new DurationFormatter(printer, locale);
    }

    @Nullable
    public Locale getLocale(){
        return locale;
    }

    public void printTo(StringBuffer buf, Duration duration){
        printer.printTo(buf, duration, locale);
    }

    public void printTo(Writer out, Duration duration) throws IOException{
        printer.printTo(out, duration, locale);
    }

    public String print(Duration duration){
        StringBuffer buf = new StringBuffer(printer.calculatePrintedLength(duration, locale));
        printer.printTo(buf, duration, locale);
        return buf.toString();
    }
}
