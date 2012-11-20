package org.curriki.tools.loadtest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.jdom2.Document;

import java.io.IOException;

/**
 */
public class TestClusteringWorksOnTitles {


    private static Log LOG = LogFactory.getLog(TestClusteringWorksOnTitles.class);

    public static void main(String[] args) throws Exception {
        if(args.length!=5) {
            System.err.println("Usage: java TestClusteringWorksOnTitles.jar <baseURL> <username> <password> <node1> <node2>");
            System.err.println("       Consider adapting the logging priority, e.g. -Dlog4j.rootCategory=ERROR,console");
            System.exit(1);
        }
        try {
            TestClusteringWorksOnTitles t = new TestClusteringWorksOnTitles();
            t.baseURL = args[0];
            t.userName = args[1];
            t.password = args[2];
            t.node1 = args[3];
            t.node2 = args[4];
            t.init();
            t.run();
            System.exit(0);
        } catch (Throwable e) {
            LOG.error("TestClusteringWorksOnTitles failed.", e);
            System.exit(1);
        }
    }

    private String baseURL;
    private String userName, password;
    private String node1, node2;
    private XWikiHttpClient client;

    private void init() throws IOException {
        this.client = new XWikiHttpClient(baseURL, userName, password);
    }


    private void run() throws Exception {
        HttpResponse response;

        client.tryLogin();
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        String newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("-- Uploading \"" + newContent + "\" to Sandbox/X.");
        response = client.postForm(baseURL + "xwiki/bin/save/Sandbox/X", "title:" + newContent, "content:" + newContent, "comment:edited");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/Sandbox/X");

        LOG.info("-- Checking it arrived on other cluster");
        client.changeClusterNode(node1, node2);
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/Sandbox/X");
        Document doc = Checker.parseResponse(response);
        Checker.checkResponseHas("TEXTAREA", "content", doc, newContent); // [@name='content']
        LOG.info("-- It did arrive.");



        newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        LOG.info("-- Uploading \"" + newContent + "\" to Sandbox/X.");
        client.changeClusterNode(node1, node2);
        response = client.postForm(baseURL + "xwiki/bin/save/Sandbox/X", "title:" + newContent, "content:" + newContent, "comment:edited-for-testing");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/Sandbox/X");

        client.changeClusterNode(node1, node2);
        LOG.info("-- Checking it arrived on other cluster node (\"" + client.getClusterNodeOfSession() + "\")");
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/Sandbox/X");
        doc = Checker.parseResponse(response);
        Checker.checkResponseHas("TEXTAREA", "content", doc, newContent);
        LOG.info("-- It did arrive.");
        client.getPage(baseURL + "xwiki/bin/cancel/Sandbox/X");
        LOG.info("-- Cleaned up.");
    }

}
