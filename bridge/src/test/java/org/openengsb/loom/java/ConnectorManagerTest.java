package org.openengsb.loom.java;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
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

    private static final String OPENENGSB_VERSION = "3.0.0-SNAPSHOT";
    private static final String OPENENGSB_FRAMEWORK = "openengsb-framework-" + OPENENGSB_VERSION;

    private static final TemporaryFolder tmpFolder = new TemporaryFolder();

    private static final int MAX_ATTEMPTS = 120;
    private static final int POLL_INTERVAL = 1000; // 1 second

    @BeforeClass
    public static void setUpOpenEngSB() throws Exception {
        File fullZIPPath = AetherUtil.downloadArtifact(OPENENGSB_VERSION);
        tmpFolder.create();
        LOGGER.info("unpacking openengsb to " + tmpFolder.getRoot().getAbsolutePath());
        unzip(fullZIPPath, tmpFolder.getRoot());
        File openengsbRoot = new File(tmpFolder.getRoot(), OPENENGSB_FRAMEWORK + "/");
        LOGGER.info("injecting features");
        FileUtils.copyURLToFile(ClassLoader.getSystemResource("features.xml"), new File(openengsbRoot,
            "deploy/features.xml"));
        File executable;
        if (!isWindows()) {
            executable = new File(openengsbRoot, "bin/openengsb");
        } else {
            executable = new File(openengsbRoot, "bin/openengsb.bat");
        }
        executable.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
        LOGGER.info("starting openengsb ...");
        pb.start();
        URL url = new URL("http://localhost:8090/openengsb/");
        int i = 0;
        while (i++ < MAX_ATTEMPTS) {
            try {
                URLConnection openConnection = url.openConnection();
                openConnection.connect();
                openConnection.getContent();
            } catch (Exception e) {
                Thread.sleep(POLL_INTERVAL);
                LOGGER.error("openengsb is not ready yet. {} - {}", e.getClass().getName(), e.getMessage());
                continue;
            }
            LOGGER.info("OpenEngSB seems to be completely booted up after {} attempts, starting tests...", i);
            return;
        }
        fail("openengsb could not be started after " + i + " attempts");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return (os.indexOf("win") >= 0);
    }

    @AfterClass
    public static void cleanupTmp() throws Exception {
        LOGGER.info("shutting down ...");
        executeBatchFile("stop");
        Thread.sleep(30 * 1000);
        LOGGER.info("deleting openengsb");
        tmpFolder.delete();
    }

    private static void executeBatchFile(String batchFileName) throws IOException {
        File openengsbRoot = new File(tmpFolder.getRoot(), OPENENGSB_FRAMEWORK + "/");
        File executable;
        if (!isWindows()) {
            executable = new File(openengsbRoot, "bin/" + batchFileName);
        } else {
            executable = new File(openengsbRoot, "bin/" + batchFileName + ".bat");
        }
        executable.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
        pb.start();
    }

    private static void unzip(File source, File target) throws IOException {
        ZipFile zipFile = new ZipFile(source);
        Enumeration<?> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();
            if (entry.isDirectory()) {
                // Assume directories are stored parents first then children.
                LOGGER.debug("Extracting directory: " + entry.getName());
                // This is not robust, just for demonstration purposes.
                (new File(entry.getName())).mkdir();
                continue;
            }
            LOGGER.debug("Extracting file: " + entry.getName());
            FileUtils.copyInputStreamToFile(zipFile.getInputStream(entry), new File(target, entry.getName()));
        }
        zipFile.close();
    }
}
