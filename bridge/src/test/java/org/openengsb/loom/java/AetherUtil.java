package org.openengsb.loom.java;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.collection.CollectResult;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class AetherUtil {

	private static final Logger LOGGER = LoggerFactory
			.getLogger(AetherUtil.class);

	private static RepositorySystem newRepositorySystem() throws Exception {
		return new DefaultPlexusContainer().lookup(RepositorySystem.class);
	}

	private static RepositorySystemSession newSession(RepositorySystem system)
			throws ParserConfigurationException, SAXException, IOException {
		MavenRepositorySystemSession session = new MavenRepositorySystemSession();
		final DecimalFormat formatter = (DecimalFormat) DecimalFormat
				.getInstance();
		formatter.setGroupingUsed(true);
		session.setTransferListener(new AbstractTransferListener() {
			@Override
			public void transferProgressed(TransferEvent event)
					throws TransferCancelledException {
				LOGGER.info(String.format("Downloading %s: %s/%s", event
						.getResource().getFile().getName(), formatter
						.format(event.getTransferredBytes()), formatter
						.format(event.getResource().getContentLength())));
			}
		});
		String repoPath = getMavenRepoPath();
		LocalRepository localRepo = new LocalRepository(new File(repoPath));
		session.setLocalRepositoryManager(system
				.newLocalRepositoryManager(localRepo));
		return session;
	}

	/**
	 * Returns the Path to the Maven repository
	 */
	private static String getMavenRepoPath()
			throws ParserConfigurationException, SAXException, IOException {
		String repoPath = getMavenRepoPathFromUserSettings();
		if (repoPath == null) {
			repoPath = getMavenRepoPathFromGlobalSettings();
		}
		if (repoPath == null) {
			repoPath = getFallBackMavenRepoPath();
		}
		return repoPath;
	}

	/**
	 * Returns the Maven repository path, configured in the user's settings or,
	 * if not found, null.
	 */
	private static String getMavenRepoPathFromUserSettings()
			throws SAXException, IOException, ParserConfigurationException {
		String homedir = System.getProperty("user.home");
		String configFilePath = homedir + File.separator + ".m2"
				+ File.separator + "settings.xml";
		File configFile = new File(configFilePath);
		if (!configFile.exists()) {
			return null;
		}
		return getMavenRepositoryEntryFromSettingsFile(configFile);
	}

	/**
	 * Returns the Maven repository path, configured in the Maven global
	 * settings or, if not found, null.
	 */
	private static String getMavenRepoPathFromGlobalSettings()
			throws SAXException, IOException, ParserConfigurationException {
		String mavenDir = System.getenv("M2_HOME");
		String configFilePath = mavenDir + File.separator + "conf"
				+ File.separator + "settings.xml";
		File configFile = new File(configFilePath);
		if (!configFile.exists()) {
			return null;
		}
		return getMavenRepositoryEntryFromSettingsFile(configFile);
	}

	/**
	 * Returns the default path to the Maven repository, if no configuration was
	 * found
	 */
	private static String getFallBackMavenRepoPath() {
		String homedir = System.getProperty("user.home");
		String defaultPath = ".m2/repository";
		return homedir + File.separator + defaultPath;
	}

	/**
	 * Returns the Maven repository path from the given settings file or, if not
	 * found, null.
	 */
	private static String getMavenRepositoryEntryFromSettingsFile(
			File mavenSettingsFile) throws SAXException, IOException,
			ParserConfigurationException {
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		Document doc = dBuilder.parse(mavenSettingsFile);
		NodeList repoNodeList = doc.getElementsByTagName("localRepository");
		if (repoNodeList.getLength() <= 0) {
			return null;
		}
		repoNodeList = repoNodeList.item(0).getChildNodes();
		if (repoNodeList.getLength() <= 0) {
			return null;
		}
		Node repoNode = (Node) repoNodeList.item(0);
		return repoNode.getNodeValue();
	}

	public static File downloadArtifact(String openengsbVersion)
			throws Exception {
		RepositorySystem repoSystem = newRepositorySystem();
		RepositorySystemSession session = newSession(repoSystem);
		DefaultArtifact artifact = new DefaultArtifact(
				"org.openengsb.framework", "openengsb-framework", "zip",
				openengsbVersion);
		Dependency dependency = new Dependency(artifact, "test");
		RemoteRepository central = new RemoteRepository("central", "default",
				"http://repo1.maven.org/maven2/");
		RemoteRepository sonatypeSnapshots = new RemoteRepository(
				"sonatype-snapshots", "default",
				"https://oss.sonatype.org/content/repositories/snapshots/");

		CollectRequest collectRequest = new CollectRequest();
		collectRequest.setRoot(dependency);
		collectRequest.addRepository(sonatypeSnapshots);
		collectRequest.addRepository(central);

		LOGGER.info("collecting dependencies for OpenEngSB {}",
				openengsbVersion);
		CollectResult collectDependencies = repoSystem.collectDependencies(
				session, collectRequest);
		DependencyNode node = collectDependencies.getRoot();
		DependencyRequest dependencyRequest = new DependencyRequest(node, null);
		repoSystem.resolveDependencies(session, dependencyRequest);

		return node.getDependency().getArtifact().getFile();
	}

}
