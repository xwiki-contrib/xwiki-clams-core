import cz.mallat.uasparser.OnlineUpdater;
import cz.mallat.uasparser.UASparser;
import cz.mallat.uasparser.UserAgentInfo;
import junit.framework.TestCase;

public class UAParserTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        parser = new UASparser(this.getClass().getResourceAsStream("/userAgentStrings.ini"));
        new OnlineUpdater(parser);
    }

    UASparser parser;

    public void testUAParser() throws Exception {
        // chrome
        UserAgentInfo ua = parser.parse("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_8_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/34.0.1847.116 Safari/537.36");

        assertEquals("unknown", ua.getDeviceType());
        assertEquals("OS X", ua.getOsFamily());
        assertFalse("Chrome is not a robot", ua.isRobot());

        ua = parser.parse("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");
        //assertFalse("Googlebot is a robot", ua.isRobot());

        ua = parser.parse("Mozilla/5.0 (iPod; CPU iPhone OS 6_1_3 like Mac OS X) AppleWebKit/536.26 (KHTML, like Gecko) Version/6.0 Mobile/10B329 Safari/8536.25");
        assertFalse("Safari on iPad is not robot", ua.isRobot());
        assertEquals("iOS", ua.getOsFamily());
        //assertEquals("Tablet", ua.getDeviceType());

        ua = parser.parse("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.64 Safari/537.31");
        assertFalse("Chrome on Windows is not robot", ua.isRobot());
        assertEquals("Windows", ua.getOsFamily());


    }

}
