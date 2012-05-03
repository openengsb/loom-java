package org.openengsb.loom.java;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openengsb.core.api.ConnectorManager;
import org.openengsb.core.api.model.ConnectorDefinition;
import org.openengsb.core.api.model.ConnectorDescription;
import org.openengsb.core.api.security.service.UserDataManager;
import org.openengsb.domain.example.ExampleDomain;
import org.openengsb.loom.java.impl.OpenEngSB3DomainFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class ConnectorManagerTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorManagerTest.class);

    private static final String zipPath = ""
            + ".m2/repository/org/openengsb/framework/openengsb-framework/"
            + "3.0.0-SNAPSHOT/openengsb-framework-3.0.0-SNAPSHOT.zip";

    private static final String baseURL = "failover:(tcp://localhost:6549)?timeout=6000";
    private OpenEngSB3DomainFactory domainFactory;

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

    @Before
    public void setUp() throws Exception {
        domainFactory = new OpenEngSB3DomainFactory(baseURL);
    }

    @After
    public void tearDown() throws Exception {
        domainFactory.destroy();
    }

    @Test
    public void retrieveServiceProxy() throws Exception {
        UserDataManager userDataManager = domainFactory.getRemoteProxy(UserDataManager.class, null);
        Collection<String> userList = userDataManager.getUserList();
        assertThat(userList, CoreMatchers.hasItems("admin", "user"));
    }

    @Test
    public void createConnector() throws Exception {
        ConnectorManager cm = domainFactory.getRemoteProxy(ConnectorManager.class, null);
        ConnectorDefinition connectorDefinition = new ConnectorDefinition("example", "example", "test2");
        ConnectorDescription connectorDescription = new ConnectorDescription(
            ImmutableMap.of("prefix", "<><>", "level", "info"),
            new HashMap<String, Object>());
        cm.create(connectorDefinition, connectorDescription);
    }

    @Test
    public void createConnectorProxy() throws Exception {
        ExampleDomain handler = new ExampleConnector();
        String uuid = domainFactory.registerConnector("example", handler);
        ExampleDomain self =
            domainFactory.getRemoteProxy(ExampleDomain.class, "example+external-connector-proxy+" + uuid);
        assertThat(self.doSomethingWithMessage("asdf"), is("42"));
    }
}
