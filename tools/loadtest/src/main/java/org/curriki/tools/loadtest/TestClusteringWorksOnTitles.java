package org.curriki.tools.loadtest;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.http.HttpResponse;
import org.apache.log4j.Level;
import org.apache.log4j.helpers.ISO8601DateFormat;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.ElementFilter;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 */
public class TestClusteringWorksOnTitles {


    private static Log LOG = LogFactory.getLog(TestClusteringWorksOnTitles.class);

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption("q", "quiet",    false, "Prevents output except in error cases.");
        options.addOption("h", "help",     false, "Displays this help message.");
        options.addOption("s", "sleep",    true,  "Sleep n milliseconds between calls.");
        options.addOption("d", "delete",   false, "Delete (and empties trash) before testing (sounds buggy).");
        options.addOption("u", "user",     true,  "User-name to post to (required).");
        options.addOption("p", "password", true,  "Password for that username (required).");
        options.addOption("d", "debug",    false,  "Detail all communications.");
        options.addOption("b", "backEnd1", true,  "Back-end URL 1 (optional).");
        options.addOption("c", "backEnd2", true,  "Back-end URL 2 (optional).");
        options.addOption("w", "witness",  true,  "Witness file (outputs json, optional).");
        CommandLine cli = new PosixParser().parse(options, args);
        List argsList = cli.getArgList();
        if(argsList.size()!=4 || !cli.hasOption("user") || !cli.hasOption("password")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("TestClusteringWorksOnTitles [OPTIONS] <baseURL> <Space/PageName> <route1> <route2>",
                    "A tool to test XWiki's clustering between two cluster routes which are on the back of " +
                            "a load-balancer which follows the convention to append the route-name at end" +
                            "of the JSESSIONID cookie.", options,
                    "(example: TestClusteringWorksOnTitles --user u --password p http://www.curriki.org/ Sandbox/TestClusteringPage prod-app failover-app)", true);
            System.exit(1);
        }

        TestClusteringWorksOnTitles t = new TestClusteringWorksOnTitles();
        try {
            t.baseURL = (String) argsList.get(0);
            t.backEndUrl1 = cli.getOptionValue("backEnd1");
            t.backEndUrl2 = cli.getOptionValue("backEnd2");
            t.userName = cli.getOptionValue("user");
            t.password = cli.getOptionValue("password");
            t.doDeletions = cli.hasOption("delete");
            t.doSleep = cli.hasOption("sleep");
            t.quiet = cli.hasOption("quiet");
            t.witnessFile = cli.getOptionValue("witness");
            if(t.quiet) ((Log4JLogger) LOG).getLogger().getParent().setLevel(Level.ERROR);
            if(cli.hasOption("debug")) ((Log4JLogger) LOG).getLogger().getParent().setLevel(Level.DEBUG);
            t.page = (String) argsList.get(1);
            t.node1 = (String) argsList.get(2);
            t.node2 = (String) argsList.get(3);
            t.init();
            t.run();
            t.notifyOk(true);
            System.exit(0);
        } catch (Throwable e) {
            LOG.error("TestClusteringWorksOnTitles failed.", e);
            if(t!=null) LOG.info("Traces would be visible on " + t.baseURL + "xwiki/bin/view/" + t.page);
            t.notifyOk(false);
            System.exit(1);
        }
    }

    private String baseURL, backEndUrl1 = null, backEndUrl2 = null;
    private String userName, password;
    private String node1, node2;
    private String witnessFile;
    private String page = "Sandbox/TempForTestingClustering";
    private XWikiHttpClient frontEndClient, backEndClient1 = null, backEndClient2 = null;
    private boolean doDeletions = false, quiet = false, doSleep = false;


    private void init() throws IOException {
        this.frontEndClient = new XWikiHttpClient(baseURL, userName, password);
        if(backEndUrl1!=null) this.backEndClient1 = new XWikiHttpClient(backEndUrl1, userName, password);
        if(backEndUrl2!=null) this.backEndClient2 = new XWikiHttpClient(backEndUrl2, userName, password);
    }


    private void run() throws Exception {
        HttpResponse response;
        Document doc;

        frontEndClient.tryLogin();
        if(backEndClient1!=null) backEndClient1.tryLogin();
        if(backEndClient2!=null) backEndClient2.tryLogin();
        LOG.info("--- Now on " + frontEndClient.getClusterNodeOfSession());

        // get first to verify if there
        if(doDeletions) {
            response = frontEndClient.getPage("/xwiki/bin/view/" + page + "?language=en");
            if(response.getStatusLine().getStatusCode()==200) {
                // delete first
                LOG.info("--- deleting earlier versions.");
                response = frontEndClient.getPage("/xwiki/bin/delete/"+page+"?confirm=1&language=en");
                doc = Checker.parseResponse(response);
                Checker.checkDocumentHas("LEGEND", null, doc, "Delete");
                for(Element elt: doc.getRootElement().getDescendants(new ElementFilter("A",Checker.HTMLNS))) {
                    String href = elt.getAttributeValue("href");
                    if(href!=null && href.startsWith("/xwiki/bin/delete/" + page) && href.contains("id=")) {
                        LOG.info("---- deleting: " + href);
                        frontEndClient.getPage(href.substring(1));
                    }
                }
            } else {
                LOG.info("--- earlier version not found.");
            }
        }
        if(doSleep) Thread.sleep(10000);


        String newContent = "Title Edited On " + frontEndClient.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        uploadContent(frontEndClient, newContent, newContent);

        if(doSleep) Thread.sleep(10000);

        checkItArrivedOn(newContent, frontEndClient, backEndClient2, node1, node2);

        for(int i=0; i<5; i++) {
            LOG.info("");
            LOG.info("- STEP: " + i);
            if(doSleep) Thread.sleep(10000);
            newContent = "Title Edited On " + frontEndClient.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
            if(i % 2 == 1) {
                uploadContent(frontEndClient, newContent, newContent);
                if(doSleep) Thread.sleep(10000);
                checkItArrivedOn(newContent, frontEndClient, backEndClient2, node1, node2);
            } else {
                uploadContent(frontEndClient, newContent, newContent);
                if(doSleep) Thread.sleep(10000);
                checkItArrivedOn(newContent, frontEndClient, backEndClient1, node2, node1);
            }
        }

        frontEndClient.getPage("/xwiki/bin/cancel/" + page + "?language=en");
        LOG.info("-- Cleaned up.");
        LOG.info("Traces would be visible on " + baseURL + "xwiki/bin/view/" + page);
    }

    private void checkItArrivedOn(String newContent, XWikiHttpClient frontEndClient, XWikiHttpClient backEndClient, String oldNode, String targetNode) throws IOException, JDOMException {
        LOG.info("-- Checking it arrived on other cluster node ("+targetNode+").");
        frontEndClient.changeClusterNode(oldNode, targetNode);
        HttpResponse response;
        Document doc;
        response = frontEndClient.getPage("/xwiki/bin/edit/" + page);
        LOG.info("--- Now on " + frontEndClient.getClusterNodeOfSession());
        doc = Checker.parseResponse(response);
        Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent); // [@name='content']
        String comment = Checker.findCommentStartingWith(doc, "generated on ");
        if(comment==null || !comment.toLowerCase().startsWith("generated on " + targetNode))
            LOG.error("--- wrong node delivered it: " + comment);
        else
            LOG.info("--- delivered from correct node: " + targetNode);
        LOG.info("--- It did arrive (front "+ frontEndClient+").");
        if(backEndClient!=null) {
            response = backEndClient.getPage("/xwiki/bin/edit/" + page);
            doc = Checker.parseResponse(response);
            Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent); // [@name='content']
            LOG.info("--- It did arrive (back, " + backEndClient + ").");
        }

    }

    private void uploadContent(XWikiHttpClient client, String title, String newContent) throws Exception {
        HttpResponse response;
        LOG.info("-- Uploading \"" + newContent + "\" to "+ page +".");
        response = client.postForm(baseURL + "xwiki/bin/save/" + page + "?language=en", "title:" + newContent, "content:" + newContent, "comment:edited");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/" + page);
        LOG.info("--- redirected properly.");
    }

    private void notifyOk(boolean result) {
        if(witnessFile!=null && witnessFile.length()>0) {
            File file = new File(witnessFile);
            try {
                org.apache.commons.io.FileUtils.writeStringToFile(file,
                        "window.clusteringCheckResult = [" +
                                "date: " + new ISO8601DateFormat().format(new Date()) + ", " +
                                "success: " + result +
                        "]");
            } catch (IOException e) {
                LOG.error("Can't write to \"" + file + "\". No witness.", e);
            }
        }
    }



}
