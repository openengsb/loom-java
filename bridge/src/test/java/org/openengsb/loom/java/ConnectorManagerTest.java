package org.openengsb.loom.java;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
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

    private static String OPENENGSB_VERSION = "3.0.0-SNAPSHOT";

    private static TemporaryFolder tmpFolder = new TemporaryFolder();

    private static Process process;
    private static final int MAX_ATTEMPTS = 500;

    @BeforeClass
    public static void setUpOpenEngSB() throws Exception {
        File fullZIPPath = AetherUtil.downloadArtifact(OPENENGSB_VERSION);;
        tmpFolder.create();
        LOGGER.info("unpacking openengsb");
        unzip(fullZIPPath, tmpFolder.getRoot());
        File openengsbRoot = new File(tmpFolder.getRoot(), "openengsb-framework-" + OPENENGSB_VERSION + "/");
        LOGGER.info("injecting features");
        FileUtils.copyURLToFile(ClassLoader.getSystemResource("features.xml"), new File(openengsbRoot,
            "deploy/features.xml"));
        File executable =
            new File(openengsbRoot, "bin/openengsb");
        executable.setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath());
        LOGGER.info("startup openengsb");
        process = pb.start();
        URL url = new URL("http://localhost:8090/openengsb/");
        int i = 0;
        while (i++ < MAX_ATTEMPTS) {
            try {
                URLConnection openConnection = url.openConnection();
                openConnection.connect();
                openConnection.getContent();
            } catch (ConnectException e) {
                Thread.sleep(300);
                LOGGER.info("openengsb is not ready yet. {} - {}", e.getClass().getName(), e.getMessage());
                continue;
            } catch (IOException e){
                Thread.sleep(300);
                LOGGER.info("openengsb is not ready yet. {} - {}", e.getClass().getName(), e.getMessage());
                continue;
            } catch (Exception e) {
                Thread.sleep(300);
                LOGGER.error("openengsb is not ready yet", e);
                continue;
            }
            LOGGER.info("OpenEngSB seems to be completely booted up after {} attempts, starting tests...", i);
            return;
        }
        fail("openengsb could not be started after " + i + " attempts");
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
