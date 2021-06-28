package inside.util;

import java.time.Duration;
import java.util.Locale;

interface DurationPrinter{

    int calculateFormattedLength(Duration duration, Locale locale);

    int countFieldsToFormat(Duration duration, int stopAt, Locale locale);

    void formatTo(StringBuilder buf, Duration duration, Locale locale);
}
