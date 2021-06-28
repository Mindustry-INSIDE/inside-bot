package inside;

import inside.util.DurationFormat;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Locale;

public class DurationFormatterTest{

    private static final Locale RU = new Locale("ru");

    @Test
    public void defaultFormatSingle(){
        var printer = DurationFormat.getDefault();
        Assertions.assertEquals("0 milliseconds", printer.format(Duration.ZERO));
        Assertions.assertEquals("1 millisecond", printer.format(Duration.ofMillis(1)));
        Assertions.assertEquals("2 milliseconds", printer.format(Duration.ofMillis(2)));

        Assertions.assertEquals("1 second", printer.format(Duration.ofSeconds(1)));
        Assertions.assertEquals("2 seconds", printer.format(Duration.ofSeconds(2)));
        Assertions.assertEquals("59 seconds", printer.format(Duration.ofSeconds(59)));

        Assertions.assertEquals("1 minute", printer.format(Duration.ofSeconds(60)));
        Assertions.assertEquals("2 minutes", printer.format(Duration.ofMinutes(2)));

        Assertions.assertEquals("1 hour", printer.format(Duration.ofHours(1)));
        Assertions.assertEquals("2 hours", printer.format(Duration.ofHours(2)));

        Assertions.assertEquals("1 day", printer.format(Duration.ofDays(1)));
        Assertions.assertEquals("2 days", printer.format(Duration.ofDays(2)));
    }

    @Test
    public void russianWordFormatSingle(){
        var printer = DurationFormat.wordBased(RU);
        Assertions.assertEquals("0 миллисекунд", printer.format(Duration.ZERO));
        Assertions.assertEquals("1 миллисекунда", printer.format(Duration.ofMillis(1)));
        Assertions.assertEquals("2 миллисекунды", printer.format(Duration.ofMillis(2)));

        Assertions.assertEquals("1 секунда", printer.format(Duration.ofSeconds(1)));
        Assertions.assertEquals("2 секунды", printer.format(Duration.ofSeconds(2)));
        Assertions.assertEquals("59 секунд", printer.format(Duration.ofSeconds(59)));

        Assertions.assertEquals("1 минута", printer.format(Duration.ofSeconds(60)));
        Assertions.assertEquals("2 минуты", printer.format(Duration.ofMinutes(2)));

        Assertions.assertEquals("1 час", printer.format(Duration.ofHours(1)));
        Assertions.assertEquals("2 часа", printer.format(Duration.ofHours(2)));

        Assertions.assertEquals("1 день", printer.format(Duration.ofDays(1)));
        Assertions.assertEquals("2 дня", printer.format(Duration.ofDays(2)));
    }
}
