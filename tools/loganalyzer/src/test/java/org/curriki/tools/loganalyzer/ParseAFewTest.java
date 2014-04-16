package org.curriki.tools.loganalyzer;

import junit.framework.TestCase;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.text.SimpleDateFormat;

public class ParseAFewTest extends TestCase {

    public ParseAFewTest(String name) {
        super(name);
    }

    File tempFile;

    public void setUp() throws IOException {
        System.setProperty("user.lang","en");
        System.setProperty("user.local","en-US");
        tempFile = File.createTempFile("tempLog", "log");
        IOUtils.copy(this.getClass().getResourceAsStream("/sample-log.log"),
                new FileOutputStream(tempFile));

    }

    public void tearDown() throws Exception {
        tempFile.delete();
    }

    public void testParseAFew() throws Throwable {
        MaximizingLogAnalysisCursor cursor = new MaximizingLogAnalysisCursor("","", false);
        cursor.setFakeStartTime(new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss ZZZZZ").parse("15/Apr/2014:15:10:40 -0700").getTime());
        LogCollector collector = new LogCollector(tempFile.getParent(), tempFile.getName(), true, true, cursor);
        for(int i=0; i<30; i++){
            Thread.sleep(1000);
            cursor.calculateAndReport();
        }

        assertEquals("One robot.", 1, cursor.numberOfRobots);
        assertEquals("One iPad", 1, cursor.numberOfMobiles);
    }



}