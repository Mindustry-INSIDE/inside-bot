package inside.util;

import reactor.util.annotation.Nullable;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class DurationFormatter{

    private final DurationPrinter printer;
    private final Locale locale;

    DurationFormatter(DurationPrinter printer){
        this(printer, null);
    }

    DurationFormatter(DurationPrinter printer, @Nullable Locale locale){
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

    public void formatTo(Appendable appendable, Duration duration){
        try{
            if(appendable instanceof StringBuilder buf){
                printer.formatTo(buf, duration, locale);
            }else{
                // buffer output to avoid writing to appendable in case of error
                StringBuilder buf = new StringBuilder(printer.calculateFormattedLength(duration, locale));
                printer.formatTo(buf, duration, locale);
                appendable.append(buf);
            }
        }catch(IOException ex){
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public String format(Duration duration){
        StringBuilder buf = new StringBuilder(printer.calculateFormattedLength(duration, locale));
        formatTo(buf, duration);
        return buf.toString();
    }
}
