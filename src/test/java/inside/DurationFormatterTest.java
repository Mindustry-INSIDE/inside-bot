package inside;

import inside.util.DurationFormat;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DurationFormatterTest{

    private static final Locale RU = new Locale("ru");

    private static final Duration all = Duration.ofDays(1).plusHours(1).plusMinutes(1).plusSeconds(1).plusMillis(1);

    //region english

    @Test
    public void defaultFormatSingle(){
        var printer = DurationFormat.getDefault();

        assertEquals("0 milliseconds", printer.format(Duration.ZERO));
        assertEquals("1 millisecond", printer.format(Duration.ofMillis(1)));
        assertEquals("2 milliseconds", printer.format(Duration.ofMillis(2)));

        assertEquals("1 second", printer.format(Duration.ofSeconds(1)));
        assertEquals("2 seconds", printer.format(Duration.ofSeconds(2)));
        assertEquals("59 seconds", printer.format(Duration.ofSeconds(59)));

        assertEquals("1 minute", printer.format(Duration.ofSeconds(60)));
        assertEquals("2 minutes", printer.format(Duration.ofMinutes(2)));

        assertEquals("1 hour", printer.format(Duration.ofHours(1)));
        assertEquals("2 hours", printer.format(Duration.ofHours(2)));

        assertEquals("1 day", printer.format(Duration.ofDays(1)));
        assertEquals("2 days", printer.format(Duration.ofDays(2)));
    }

    @Test
    public void defaultFormatAll(){
        var printer = DurationFormat.getDefault();

        assertEquals("1 day, 1 hour, 1 minute, 1 second and 1 millisecond", printer.format(all));
    }

    //endregion
    //region russian

    @Test
    public void russianWordFormatSingle(){
        var printer = DurationFormat.wordBased(RU);

        assertEquals("0 миллисекунд", printer.format(Duration.ZERO));
        assertEquals("1 миллисекунда", printer.format(Duration.ofMillis(1)));
        assertEquals("2 миллисекунды", printer.format(Duration.ofMillis(2)));

        assertEquals("1 секунда", printer.format(Duration.ofSeconds(1)));
        assertEquals("2 секунды", printer.format(Duration.ofSeconds(2)));
        assertEquals("59 секунд", printer.format(Duration.ofSeconds(59)));

        assertEquals("1 минута", printer.format(Duration.ofSeconds(60)));
        assertEquals("2 минуты", printer.format(Duration.ofMinutes(2)));

        assertEquals("1 час", printer.format(Duration.ofHours(1)));
        assertEquals("2 часа", printer.format(Duration.ofHours(2)));

        assertEquals("1 день", printer.format(Duration.ofDays(1)));
        assertEquals("2 дня", printer.format(Duration.ofDays(2)));
    }

    @Test
    public void russianWordFormatAll(){
        var printer = DurationFormat.wordBased(RU);

        assertEquals("1 день, 1 час, 1 минута, 1 секунда и 1 миллисекунда", printer.format(all));
    }

    //endregion
}
