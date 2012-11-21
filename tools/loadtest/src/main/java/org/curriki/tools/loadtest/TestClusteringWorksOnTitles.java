package org.curriki.tools.loadtest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.filter.ElementFilter;

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
    private String page = "Sandbox/TempForTestingClustering";
    private XWikiHttpClient client;

    private void init() throws IOException {
        this.client = new XWikiHttpClient(baseURL, userName, password);
    }


    private void run() throws Exception {
        HttpResponse response;
        Document doc;

        client.tryLogin();
        LOG.info("--- Now on " + client.getClusterNodeOfSession());

        /*
        // disabled: check and deletion
        // get first to verify if there
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
        Thread.sleep(10000);
        */


        String newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("-- Uploading \"" + newContent + "\" to "+ page +".");
        response = client.postForm(baseURL + "xwiki/bin/save/" + page + "?language=en", "title:" + newContent, "content:" + newContent, "comment:edited");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/" + page);
        Thread.sleep(10000);

        LOG.info("-- Checking it arrived on other cluster node.");
        client.changeClusterNode(node1, node2);
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/" + page);
        doc = Checker.parseResponse(response);
        Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent); // [@name='content']
        LOG.info("-- It did arrive.");



        Thread.sleep(10000);
        newContent = "Title Edited On " + client.getClusterNodeOfSession() + " " + ((int) (Math.random()*10000));
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        LOG.info("-- Uploading \"" + newContent + "\" to "+ page +".");
        client.changeClusterNode(node1, node2);
        response = client.postForm(baseURL + "xwiki/bin/save/" + page + "?language=en", "title:" + newContent, "content:" + newContent, "comment:edited-for-testing");
        Checker.assertResponseIsRedirect(response, baseURL + "xwiki/bin/view/" + page);

        Thread.sleep(10000);
        client.changeClusterNode(node1, node2);
        LOG.info("-- Checking it arrived on other cluster node (\"" + client.getClusterNodeOfSession() + "\")");
        LOG.info("--- Now on " + client.getClusterNodeOfSession());
        response = client.getPage(baseURL + "xwiki/bin/edit/" + page + "?language=en");
        doc = Checker.parseResponse(response);
        Checker.checkDocumentHas("TEXTAREA", "content", doc, newContent);
        LOG.info("-- It did arrive.");
        client.getPage(baseURL + "xwiki/bin/cancel/" + page + "?language=en");
        LOG.info("-- Cleaned up.");
    }

}
