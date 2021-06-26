package inside.util;

import java.io.*;
import java.time.Duration;
import java.util.Locale;

public interface DurationPrinter{

    int calculatePrintedLength(Duration duration, Locale locale);

    int countFieldsToPrint(Duration duration, int stopAt, Locale locale);

    void printTo(StringBuffer buf, Duration duration, Locale locale);

    void printTo(Writer out, Duration duration, Locale locale) throws IOException;
}
