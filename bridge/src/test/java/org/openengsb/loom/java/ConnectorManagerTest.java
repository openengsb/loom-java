package org.openengsb.loom.java;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;
import org.openengsb.labs.endtoend.distribution.extractor.DistributionExtractor;
import org.openengsb.labs.endtoend.distribution.resolver.DistributionResolver;
import org.openengsb.labs.endtoend.karaf.CommandTimeoutException;
import org.openengsb.labs.endtoend.karaf.Karaf;
import org.openengsb.labs.endtoend.testcontext.TestContext;
import org.openengsb.labs.endtoend.testcontext.loader.TestContextLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectorManagerTest extends ConnectorManagerUT {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectorManagerTest.class);
    
    private static final int MAX_ATTEMPTS = 120;
    private static final int POLL_INTERVAL = 1000; // 1 second
    private static final long DEFAULT_TIMEOUT = 2L;
    
    private static final TemporaryFolder tmpFolder = new TemporaryFolder();
    private static TestContext testContext;
    
    @BeforeClass
    public static void setupOpenEngSB() throws IOException, InterruptedException {
        DistributionResolver dr = new DistributionResolver();
        tmpFolder.create();
        LOGGER.info("created tmp folder: " + tmpFolder.getRoot().getAbsolutePath());
        DistributionExtractor ds = new DistributionExtractor(tmpFolder.getRoot());
        TestContextLoader testContextLoader = new TestContextLoader(dr, ds);
        testContextLoader.loadContexts();
        testContext = testContextLoader.getDefaultTestContext();
        testContext.setup();
        
        Karaf k = testContext.getDistribution().getKaraf();
        LOGGER.info("starting openengsb ...");
        try {
            k.start(2L, TimeUnit.MINUTES);
        } catch (CommandTimeoutException e) {
            fail("karaf failed to start within 2 minutes");
        }
        
        // wait some more as karaf is not ready yet
        int i = 0;
        while (i++ < MAX_ATTEMPTS) {
            if (isFeatureInstalled(k, "openengsb-framework")) {
                break;
            }
            Thread.sleep(POLL_INTERVAL);
        }
        if (i == MAX_ATTEMPTS) {
            fail("karaf failed to start after " + i + " attempts");
        }

        installFeature(k, "openengsb-ports-jms");
        installFeature(k, "openengsb-domain-example");
        installFeature(k, "openengsb-connector-example");
        
        i = 0;
        while (i++ < MAX_ATTEMPTS) {
            if (isBundleActive(k, "OpenEngSB :: Ports :: JMS")) {
                break;
            }
            Thread.sleep(POLL_INTERVAL);
        }
        if (i == MAX_ATTEMPTS) {
            fail("failed to activate 'OpenEngSB :: Ports :: JMS' after " + i + " attempts");
        }
        
        URL url = new URL("http://localhost:8090/openengsb/");
        i = 0;
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
    
    private static void installFeature(Karaf karaf, String feature) {
        LOGGER.info("installing feature " + feature + " ...");
        try {
            karaf.installFeature(feature, DEFAULT_TIMEOUT, TimeUnit.MINUTES);
        } catch (CommandTimeoutException e1) {
            fail("failed to install feature " + feature);
        }
    }
    
    private static boolean isFeatureInstalled(Karaf karaf, String feature) {
        try {
            return karaf.isFeatureInstalled(feature, DEFAULT_TIMEOUT, TimeUnit.MINUTES);
        } catch (CommandTimeoutException e1) {
            LOGGER.info("failed to list installed features");
            return false;
        }
    }
    
    private static boolean isBundleActive(Karaf k, String bundleName) {
        try {
            return k.isBundleActive(bundleName, DEFAULT_TIMEOUT, TimeUnit.MINUTES);
        } catch (CommandTimeoutException e) {
            return false;
        }
    }
    
    @AfterClass
    public static void cleanUp() {
        testContext.teardown();
        LOGGER.info("deleting tmp folder ...");
        tmpFolder.delete();
    }
}
