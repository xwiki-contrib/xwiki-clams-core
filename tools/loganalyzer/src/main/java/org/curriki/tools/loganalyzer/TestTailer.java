package org.curriki.tools.loganalyzer;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;

import java.io.File;

/**
 * Created by paul on 4/04/14.
 */
public class TestTailer {

    public static void main(String[] args) throws Throwable {
        Tailer tailer = new Tailer(new File("/var/log/system.log"), new TailerListener() {
            public void init(Tailer tailer) {

            }

            public void fileNotFound() {

            }

            public void fileRotated() {

            }

            public void handle(String line) {
                System.out.println("line: " + line);
            }

            public void handle(Exception ex) {

            }
        });

        Thread t = new Thread(tailer);
        t.start();
        Thread.sleep(5000);
    }
}
