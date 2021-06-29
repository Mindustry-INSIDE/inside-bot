package inside;

import inside.util.MessageUtil;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

public class DurationParseTest{

    @Test
    public void parseUnsupported(){
        assertNull(MessageUtil.parseDuration("1y"));
        assertNull(MessageUtil.parseDuration("1mon"));
        assertNull(MessageUtil.parseDuration("1w"));
        assertNull(MessageUtil.parseDuration("1y1mon1w"));
    }

    @Test
    public void parseSimpleWithSpaces(){
        assertEquals(Duration.ofSeconds(13).plusMinutes(100), MessageUtil.parseDuration("100   min13s"));
        assertEquals(Duration.ofMinutes(76).plusDays(15), MessageUtil.parseDuration("15 d76 m"));
        assertEquals(Duration.ofMinutes(76).plusDays(15).plusSeconds(11), MessageUtil.parseDuration("15d 76 m 11s"));
        assertEquals(Duration.ofHours(Integer.MAX_VALUE).plusDays(Integer.MAX_VALUE), MessageUtil.parseDuration("2147483647d2147483647  h"));
    }

    @Test
    public void parseSingle(){
        assertEquals(Duration.ofSeconds(1), MessageUtil.parseDuration("1s"));
        assertEquals(Duration.ofMinutes(1), MessageUtil.parseDuration("1m"));
        assertEquals(Duration.ofHours(1), MessageUtil.parseDuration("1h"));
        assertEquals(Duration.ofDays(1), MessageUtil.parseDuration("1d"));
    }

    @Test
    public void parseMultiple(){
        assertEquals(Duration.ofSeconds(13).plusMinutes(100), MessageUtil.parseDuration("100min13s"));
        assertEquals(Duration.ofMinutes(76).plusDays(15), MessageUtil.parseDuration("15d76m"));
        assertEquals(Duration.ofHours(Integer.MAX_VALUE).plusDays(Integer.MAX_VALUE), MessageUtil.parseDuration("2147483647d2147483647h"));

        assertNotEquals(Duration.ofDays(Integer.MIN_VALUE), MessageUtil.parseDuration("-2147483648d"));
        assertNotEquals(Duration.ofSeconds(10).plusMinutes(1)
                .plusHours(10).plusDays(100), MessageUtil.parseDuration("10s1min10h100d"));
    }

    @Test
    public void fallback(){
        assertEquals(Duration.ofSeconds(13).plusMinutes(100), MessageUtil.parseDuration("PT1H40M13S"));
        assertEquals(Duration.ofMinutes(76).plusDays(15), MessageUtil.parseDuration("PT361H16M"));
        assertEquals(Duration.ofHours(Integer.MIN_VALUE).plusDays(Integer.MIN_VALUE), MessageUtil.parseDuration("PT-53687091200H"));
        assertEquals(Duration.ofHours(Integer.MAX_VALUE), MessageUtil.parseDuration("PT2147483647H"));
    }
}
