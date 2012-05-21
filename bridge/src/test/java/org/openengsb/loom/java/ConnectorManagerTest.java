package org.openengsb.loom.java;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorManagerTest extends ConnectorManagerUT {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorManagerTest.class);

    private static final String zipPath = ""
            + ".m2/repository/org/openengsb/framework/openengsb-framework/"
            + "3.0.0-SNAPSHOT/openengsb-framework-3.0.0-SNAPSHOT.zip";

    private static TemporaryFolder tmpFolder = new TemporaryFolder();

    private static Process process;

    @BeforeClass
    public static void setUpOpenEngSB() throws Exception {
        File fullZIPPath = new File(FileUtils.getUserDirectory(), zipPath);
        tmpFolder.create();
        LOGGER.info("unpacking openengsb");
        unzip(fullZIPPath, tmpFolder.getRoot());
        File openengsbRoot = new File(tmpFolder.getRoot(), "openengsb-framework-3.0.0-SNAPSHOT/");
        LOGGER.info("injecting features");
        FileUtils.copyURLToFile(ClassLoader.getSystemResource("features.xml"), new File(openengsbRoot,
            "deploy/features.xml"));
        File file = new File(openengsbRoot, "etc/system.properties");
        List<String> sysPropLines = FileUtils.readLines(file);
        sysPropLines.add("org.openengsb.security.noverify=true");
        sysPropLines.add("org.openengsb.jms.noencrypt=true");
        FileUtils.writeLines(file, sysPropLines);
        File executable =
            new File(openengsbRoot, "bin/openengsb");
        executable.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
        LOGGER.info("startup openengsb");
        process = pb.start();
        Thread.sleep(30000);
    }

    @AfterClass
    public static void cleanupTmp() throws Exception {
        process.destroy();
        tmpFolder.delete();
    }

    private static void unzip(File source, File target) throws IOException {
        ZipFile zipFile = new ZipFile(source);
        Enumeration<?> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
                // Assume directories are stored parents first then children.
                LOGGER.info("Extracting directory: " + entry.getName());
                // This is not robust, just for demonstration purposes.
                (new File(entry.getName())).mkdir();
                continue;
            }
            LOGGER.info("Extracting file: " + entry.getName());
            FileUtils.copyInputStreamToFile(zipFile.getInputStream(entry), new File(target, entry.getName()));
        }
        zipFile.close();
    }
}
