package org.curriki.tools.loadtest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.jdom2.*;
import org.jdom2.filter.ElementFilter;
import org.jdom2.filter.Filter;
import org.jdom2.input.SAXBuilder;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;

/**
 */
class Checker {

    private static Log LOG = LogFactory.getLog(Checker.class);

    static Document parseResponse(HttpResponse x) throws IOException, JDOMException {

        String content = org.apache.commons.io.IOUtils.toString(x.getEntity().getContent(), "utf-8");
        content = content.replaceAll("curriki:", "").replaceAll("Curriki:","");

        SAXBuilder builder = new SAXBuilder("org.cyberneko.html.parsers.SAXParser");
        builder.setProperty("http://cyberneko.org/html/properties/default-encoding","UTF-8");
        return builder.build(new StringReader(content));
    }

    static void checkResponseHas(String elementName, String id, Document doc, String mustFind) {
        Element found = null;
        Filter filter = new ElementFilter("TEXTAREA");
        Iterator<Content> it = doc.getRootElement().getDescendants(filter);
        while(it.hasNext()) {
            Element elt = (Element) it.next();
            if(id.equals(elt.getAttributeValue("id"))) {
                found = elt; break;
            }
        }
        String value = found.getTextNormalize();
        if(value==null)
            throw new IllegalStateException("No element following pattern \"" + elementName + "\" with id \""+id+"\" has been found.");
        else if(mustFind.equals(value)) {
            // nothing to do
            LOG.info("----  Expected text is found.");
        } else {
            throw new IllegalStateException("Element's text differs." );
        }
    }

    static void assertResponseIsRedirect(HttpResponse x, String location) {
        if(x.getStatusLine().getStatusCode()!=302) throw new IllegalStateException("Did not receive a redirect!");
        Header[] headers = x.getHeaders("Location");
        if(headers.length!=1) throw new IllegalStateException("Was expecting one Location header!");
        if(location.equals(headers[0].getValue())) {
            // ok
        } else {
            throw new IllegalStateException("Different location target: received \"" + headers[0].getValue() + "\" but expected \"" + location + "\".");
        }
    }
}
