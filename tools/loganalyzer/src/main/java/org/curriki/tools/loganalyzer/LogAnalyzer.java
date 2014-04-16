package org.curriki.tools.loganalyzer;

import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import java.util.Date;
import java.util.List;

public class LogAnalyzer {

    public static void main(String[] args) throws Exception {
        Options opts = new Options();
        opts.addOption(OptionBuilder.withArgName("server").hasArg(true).
            withDescription("Zabbix server to send to.").create("zabbix"));
        opts.addOption(OptionBuilder.hasArg(false).
                withDescription("Resume with last file's content.").create("resume"));
        opts.addOption(OptionBuilder.hasArg(false).
                withDescription("display status messages in the consolet.").create("debug"));
        opts.addOption(OptionBuilder.hasArg(false).
                withDescription("Get a usage message.").create("help"));

        CommandLine cli = new PosixParser().parse(opts, args);

        if(cli.getArgList().size()!=2 || cli.hasOption("help")) {
            System.err.println(
                    "Usage: java -jar loganalyzer.jar <options> <base-directory> <pattern-1> <pattern-2>\n" +
                            "\n" +
                            "  Scans the logs in directory <base-directory>, selecting the last following \n" +
                            "  each pattern and optionally sends its analysis to the Zabbix server.\n" +
                            "\n" +
                            "  Options\n" +
                            "    --resume : start by dissecting the lines of first matching entry.\n" +
                            "    --debug  : display status messages in the console.\n" +
                            "    --zabbix-server: host-name of the zabbix server.\n" +
                            "    --help: prints this help message.\n" +
                            "\n" +
                            "  See https://github.com/xwiki-contrib/xwiki-clams-core in tools/loganalyzer\n" +
                            "  Report any bugs to polx@curriki.org.\n");
        }
        
        if(cli.hasOption("version") || cli.getArgList().size()!=2 || cli.hasOption("help")) {
            // META-INF/maven/org.curriki/xclams-tools-loganalyzer/pom.properties
            List<String> lines = IOUtils.readLines(LogAnalyzer.class.getResourceAsStream("/META-INF/maven/org.curriki/xclams-tools-loganalyzer/pom.properties"));
            String buildDate = lines.get(1).substring(1);
            String version = lines.get(2).substring("version=".length());
            System.err.println(
                    "  Version: " + version + "\n" +
                            "  Built on " + buildDate + ".\n");
            System.exit(1);
        }
        LogCollector collector = new LogCollector((String) cli.getArgList().get(0),
                (String) cli.getArgList().get(1), cli.hasOption("resume"),
                cli.hasOption("debug"), null);
        System.err.println("LogCollector started (" + new Date() + ").");
    }
}