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
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;

import java.io.IOException;
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
            t.userName = cli.getOptionValue("user");
            t.password = cli.getOptionValue("password");
            t.doDeletions = cli.hasOption("delete");
            t.doSleep = cli.hasOption("sleep");
            t.quiet = cli.hasOption("quiet");
            if(t.quiet) ((Log4JLogger) LOG).getLogger().getParent().setLevel(Level.ERROR);
            t.page = (String) argsList.get(1);
            t.node1 = (String) argsList.get(2);
            t.node2 = (String) argsList.get(3);
            t.init();
            t.run();
            System.exit(0);
        } catch (Throwable e) {
            LOG.error("TestClusteringWorksOnTitles failed.", e);
            if(t!=null) LOG.info("Traces would be visible on " + t.baseURL + "xwiki/bin/view/" + t.page);
            System.exit(1);
        }
    }

    private String baseURL;
    private String userName, password;
    private String node1, node2;
    private String page = "Sandbox/TempForTestingClustering";
    private XWikiHttpClient client;
    private boolean doDeletions = false, quiet = false, doSleep = false;


    private void init() throws IOException {
        this.client = new XWikiHttpClient(baseURL, userName, password);
    }


    private void run() throws Exception {
        HttpResponse response;
        Document doc;

        client.tryLogin();
        LOG.info("--- Now on " + client.getClusterNodeOfSession());

        // get first to verify if there
        if(doDeletions) {
            response = client.getPage(baseURL + "xwiki/bin/view/" + page + "?language=en");
            if(response.getStatusLine().getStatusCode()==200) {
                // delete first
                LOG.info("--- deleting earlier versions.");
                response = client.getPage(baseURL + "xwiki/bin/delete/"+page+"?confirm=1&language=en");
                doc = Checker.parseResponse(response);
                Checker.checkDocumentHas("LEGEND", null, doc, "Delete");
                for(Element elt: doc.getRootElement().getDescendants(new ElementFilter("A",Checker.HTMLNS))) {
                    String href = elt.getAttributeValue("href");
                    if(href!=null && href.startsWith("/xwiki/bin/delete/" + page) && href.contains("id=")) {
                        LOG.info("---- deleting: " + href);
                        client.getPage(baseURL + href.substring(1));
                    }
                }
            } else {
                LOG.info("--- earlier version not found.");
            }
        }
        if(doSleep) Thread.sleep(10000);


        String newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("-- Uploading \"" + newContent + "\" to "+ page +".");
        response = client.postForm(baseURL + "xwiki/bin/save/" + page + "?language=en", "title:" + newContent, "content:" + newContent, "comment:edited");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/" + page);
        if(doSleep) Thread.sleep(10000);

        LOG.info("-- Checking it arrived on other cluster node.");
        client.changeClusterNode(node1, node2);
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/" + page);
        doc = Checker.parseResponse(response);
        Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent); // [@name='content']
        LOG.info("-- It did arrive.");



        if(doSleep) Thread.sleep(10000);
        newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("-- Uploading \"" + newContent + "\" to " + page + ".");
        response = client.postForm(baseURL + "xwiki/bin/save/" + page + "?language=en", "title:" + newContent, "content:" + newContent, "comment:edited-for-testing");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/" + page);

        if(doSleep) Thread.sleep(10000);
        client.changeClusterNode(node1, node2);
        LOG.info("-- Checking it arrived on other cluster node (\"" + client.getClusterNodeOfSession() + "\")");
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/" + page + "?language=en");
        doc = Checker.parseResponse(response);
        Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent);
        LOG.info("-- It did arrive.");
        client.getPage(baseURL + "xwiki/bin/cancel/" + page + "?language=en");
        LOG.info("-- Cleaned up.");
        LOG.info("Traces would be visible on " + baseURL + "xwiki/bin/view/" + page);
    }

}
