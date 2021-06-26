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
        Assertions.assertEquals("0 milliseconds", printer.print(Duration.ZERO));
        Assertions.assertEquals("1 millisecond", printer.print(Duration.ofMillis(1)));
        Assertions.assertEquals("2 milliseconds", printer.print(Duration.ofMillis(2)));

        Assertions.assertEquals("1 second", printer.print(Duration.ofSeconds(1)));
        Assertions.assertEquals("2 seconds", printer.print(Duration.ofSeconds(2)));
        Assertions.assertEquals("59 seconds", printer.print(Duration.ofSeconds(59)));

        Assertions.assertEquals("1 minute", printer.print(Duration.ofSeconds(60)));
        Assertions.assertEquals("2 minutes", printer.print(Duration.ofMinutes(2)));

        Assertions.assertEquals("1 hour", printer.print(Duration.ofHours(1)));
        Assertions.assertEquals("2 hours", printer.print(Duration.ofHours(2)));

        Assertions.assertEquals("1 day", printer.print(Duration.ofDays(1)));
        Assertions.assertEquals("2 days", printer.print(Duration.ofDays(2)));
    }

    @Test
    public void russianWordFormatSingle(){
        var printer = DurationFormat.wordBased(RU);
        Assertions.assertEquals("0 миллисекунд", printer.print(Duration.ZERO));
        Assertions.assertEquals("1 миллисекунда", printer.print(Duration.ofMillis(1)));
        Assertions.assertEquals("2 миллисекунды", printer.print(Duration.ofMillis(2)));

        Assertions.assertEquals("1 секунда", printer.print(Duration.ofSeconds(1)));
        Assertions.assertEquals("2 секунды", printer.print(Duration.ofSeconds(2)));
        Assertions.assertEquals("59 секунд", printer.print(Duration.ofSeconds(59)));

        Assertions.assertEquals("1 минута", printer.print(Duration.ofSeconds(60)));
        Assertions.assertEquals("2 минуты", printer.print(Duration.ofMinutes(2)));

        Assertions.assertEquals("1 час", printer.print(Duration.ofHours(1)));
        Assertions.assertEquals("2 часа", printer.print(Duration.ofHours(2)));

        Assertions.assertEquals("1 день", printer.print(Duration.ofDays(1)));
        Assertions.assertEquals("2 дня", printer.print(Duration.ofDays(2)));
    }
}
