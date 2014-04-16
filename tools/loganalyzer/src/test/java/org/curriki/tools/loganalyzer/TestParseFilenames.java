package org.curriki.tools.loganalyzer;

import junit.framework.TestCase;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestParseFilenames extends TestCase {

    public void testParseFilenames() throws Throwable {
        assertEquals("html",    LogAnalysisCursor.readExtension("/xwiki/bin/view/Coll_Group_IntegratingTechnologyintoSecondaryCurriculum-7533/AugmentedReality"));
        assertEquals("html",    LogAnalysisCursor.readExtension("/xwiki/bin/view/Coll_ALoom/JourneyIntoDNAAnimation?bc=&viewer=info"));
        assertEquals("txt",     LogAnalysisCursor.readExtension("/robots.txt"));
        assertEquals(null,      LogAnalysisCursor.readExtension("/"));
        assertEquals("js",      LogAnalysisCursor.readExtension("/welcome/wp-content/themes/curriki/js/jquery.bxSlider.min.js"));
        assertEquals("css",     LogAnalysisCursor.readExtension("/curricki/js//layerslider/skins/lightskin/skin.css"));
        assertEquals("png",     LogAnalysisCursor.readExtension("/xwiki/bin/skin/skins/curriki8/c109/icons/black-delete.png"));
        assertEquals("js",      LogAnalysisCursor.readExtension("/welcome/wp-includes/js/jquery/jquery.js?ver=1.10.2"));
    }

}
