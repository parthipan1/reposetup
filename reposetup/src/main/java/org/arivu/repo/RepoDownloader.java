package org.arivu.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RepoDownloader {
	static final Logger logger = LoggerFactory.getLogger("RepoDownloader");

	static String[] repos = System
			.getProperty("repos",
					"http://repo1.maven.org/maven2/,http://central.maven.org/maven2/,http://jcenter.bintray.com/")
			.split(",");

	static File downloadFile = new File(System.getProperty("file", "list.txt"));
	static File downloadRoot = new File(System.getProperty("download", "download"));
	static boolean includeDM = Boolean.parseBoolean(System.getProperty("dm", "false"));
	static boolean pipeUpoad = false;

	static {
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(new File("repo.properties")));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		pipeUpoad = Boolean.parseBoolean(p.getProperty("pipeUpoad"));
		includeDM = Boolean.parseBoolean(p.getProperty("dm"));
		downloadRoot = new File(p.getProperty("download"));
		downloadFile = new File(p.getProperty("file"));
		repos = p.getProperty("external_repo").split(",");
	}

	public static void main(String[] args) throws FileNotFoundException, IOException {
		for (Dependency d : convert(getDependencies(args)))
			d.resolve();

		if (Dependency.notFoundsFile.isEmpty())
			logger.info("NotFound :: " + Dependency.notFoundsFile);
		
		if(pipeUpoad){
			 RepoUploader.printBigFiles();
		}

	}

	static String[] getDependencies(String[] def) throws FileNotFoundException, IOException {
		if (def != null && def.length > 0)
			return def;
		else {
			Collection<String> deps = new ArrayList<>();
			try (BufferedReader br = new BufferedReader(new FileReader(downloadFile))) {
				String line;
				while ((line = br.readLine()) != null) {
					// logger.debug("Read line :: "+line);
					if (line.trim().length() == 0) {
					} else if (line.startsWith("//")) {
					} else if (line.startsWith("compile")) {
						line = line.replace("compile ", "");
						extractDep(deps, line);
					} else {
						extractDep(deps, line);
					}
				}
			}
			// logger.debug("Read from file deps :: "+deps);
			return deps.toArray(new String[] {});
		}
	}

	static void extractDep(Collection<String> deps, String line) {
		line = line.trim().replaceAll("\"", "").replaceAll("'", "");
		if (line.startsWith("group")) {
			String[] split = line.split(",");
			String v = split[0].replaceAll("group:", "").trim().replaceAll("'", "") + ":"
					+ split[1].replaceAll("name:", "").trim().replaceAll("'", "") + ":"
					+ split[2].replaceAll("version:", "").trim().replaceAll("'", "");
			deps.add(v);
		} else {
			deps.add(line);
		}
	}

	static Collection<Dependency> convert(String[] ds) {
		Collection<Dependency> deps = new ArrayList<>();
		for (String d : ds)
			deps.add(Dependency.get(d));
		return deps;
	}

}

enum JarClassifier {
	artifact {

		@Override
		String getExt() {
			return "";
		}

	},
	sources, javadoc;

	String getExt() {
		return "-" + name();
	}
}

enum IdType {
	group, artifact, version
}

class Dependency {
	static final Logger logger = LoggerFactory.getLogger("Dependency");

	private static final Map<String, Dependency> allDependencies = new HashMap<>();

	static final Collection<String> notFoundsFile = new HashSet<>();
	static final Collection<String> completed = new HashSet<>();
	// private static int nfl=0,cl=0;
	// static{
	// final File cacheFile = new File(System.getProperty("cache",".dcache"));
	// if (cacheFile.exists()) {
	// try (ObjectInputStream oos = new ObjectInputStream(new
	// FileInputStream(cacheFile));) {
	// Collection<Object> cacheObjs = (Collection<Object>) oos.readObject();
	// notFoundsFile.addAll((Collection<String>) cacheObjs.toArray()[0]);
	// completed.addAll((Collection<String>) cacheObjs.toArray()[1]);
	// nfl=notFoundsFile.size();
	// cl=completed.size();
	// } catch (IOException e) {
	// logger.error("cacheread", e);
	// } catch (ClassNotFoundException e) {
	// logger.error("cache", e);
	// }
	// }
	//
	// Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
	// @Override
	// public void run() {
	// if(
	// nfl!=notFoundsFile.size() ||
	// cl!=completed.size()
	// ){
	// Collection<Object> cacheObjs = new ArrayList<>();
	// cacheObjs.add(notFoundsFile);
	// cacheObjs.add(completed);
	// try(ObjectOutputStream oos = new ObjectOutputStream(new
	// FileOutputStream(cacheFile));){
	// oos.writeObject(cacheObjs);
	// } catch ( IOException e) {
	// logger.error("cachewrite", e);
	// }
	// }
	// }
	// }));
	//
	// }

	public static Dependency get(String dep) {
		Dependency d = new Dependency(dep);
		Dependency dependency = allDependencies.get(d.toString());
		if (dependency != null)
			return dependency;
		else
			return d;
	}

	public static Dependency get(String groupId, String artifactId, String version) {
		Dependency d = new Dependency(groupId, artifactId, version);
		Dependency dependency = allDependencies.get(d.toString());
		if (dependency != null)
			return dependency;
		else
			return d;
	}

	private String groupId, artifactId, version;
	private boolean pathReplace = true;
	private Map<String, String> properties = new HashMap<>();
	private Dependency parent;
	private Dependency projectParent;
	private PackagingType packagingType = PackagingType.jar;
	private Set<Dependency> dependencies = null;

	private Dependency(String d) {
		super();
		String[] split = d.split(":");
		this.groupId = split[0];
		this.artifactId = split[1];
		this.version = split[2];
	};

	private Dependency(String groupId, String artifactId, String version) {
		super();
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
	};

	public void resolve() {
		// logger.info("resolve "+this);
		if (completed.contains(this.toString())) {
		} else if (hasResolvedRefs()) {
			try {
				startResolve();
				completed.add(this.toString());
				resolveParent();
				resolveChildren();
				downloadFile();
				if (RepoDownloader.pipeUpoad) {
					RepoUploader.upload(pomFile);
				}
			} catch (FileNotFoundException e) {
			} catch (Throwable e) {
				logger.error("downloadPom", e);
			}
		}
	}

	File pomFile = null;

	void startResolve() throws FileNotFoundException {
		pomFile = new File(getPomFile());
		try {
			downloadPom(pomFile);
			DocumentBuilder builder = create();
			Document pomDocument = builder.parse(new FileInputStream(pomFile));
			XPath xPath = XPathFactory.newInstance().newXPath();
			extractProperties(pomDocument, xPath);
			extractPackagingType(pomDocument, xPath);
			Set<Dependency> ret = new HashSet<>();
			extractParent(ret, pomDocument, xPath);
			extractDependencies(ret, pomDocument, xPath);
			if (RepoDownloader.includeDM) {
				extractDependencyManagementDependencies(ret, pomDocument, xPath);
			}
			dependencies = Collections.unmodifiableSet(ret);
		} catch (FileNotFoundException e1) {
			throw e1;
		} catch (Throwable e) {
			logger.error("downloadPom", e);
		}
	}

	String lastRepo = null;

	private void downloadPom(File pomFile) throws FileNotFoundException {
		FileNotFoundException exp = null;
		for (String repo : RepoDownloader.repos) {
			lastRepo = repo;
			try {
				download(pomFile, lastRepo + getPOMUri(), 0);
			} catch (FileNotFoundException e) {
				pathReplace = !pathReplace;
				try {
					download(pomFile, lastRepo + getPOMUri(), 1);
				} catch (FileNotFoundException e1) {
					exp = e1;
					pathReplace = !pathReplace;
				}
			}
			if (exp == null)
				break;
		}

		if (exp != null) {
			notFoundsFile.add(this.toString());
			throw exp;
		}
		allDependencies.put(toString(), this);
	}

	private void resolveChildren() {
		for (Dependency d : dependencies) {
			d.resolve();
		}
	}

	void resolveParent() {
		Dependency p = parent;
		while (p != null) {
			p.resolve();
			p = p.parent;
		}
	}

	boolean hasResolvedRefs() {
		decodeProp();
		return !hasProperties(version) && !hasProperties(artifactId) && !hasProperties(groupId);
	}

	boolean hasProperties(final String v) {
		if (v == null)
			return true;
		return v != null && v.indexOf("${") != -1;
	}

	void downloadFile() {
		if (packagingType != PackagingType.pom && hasResolvedRefs()) {
			for (final JarClassifier fileType : JarClassifier.values()) {
				String uri = lastRepo + getFileUri(fileType);
				if (!notFoundsFile.contains(uri)) {
					try {
						download(new File(getFile(fileType)), uri, 0);
					} catch (FileNotFoundException e) {
						notFoundsFile.add(uri);
						logger.error(this + "	" + e.getLocalizedMessage());
					}
				}
			}
			completed.add(this.toString());
		}
	}

	String getFile(JarClassifier fileType) {
		decodeProp();
		return RepoDownloader.downloadRoot.getAbsolutePath() + File.separator + this.artifactId + "-" + this.version
				+ fileType.getExt() + "." + packagingType.getExt();
	}

	private void decodeProp() {
		this.groupId = decodeProp(this.groupId, IdType.group);
		this.artifactId = decodeProp(this.artifactId, IdType.artifact);
		this.version = decodeProp(this.version, IdType.version);
	}

	String getFileUri(JarClassifier fileType) {
		decodeProp();

		if (fileType == null)
			fileType = JarClassifier.artifact;

		if (pathReplace) {
			return this.groupId.replace('.', '/') + "/" + this.artifactId.replace('.', '/') + "/" + this.version + "/"
					+ this.artifactId + "-" + this.version + fileType.getExt() + "." + packagingType.getExt();
		} else {
			return this.groupId.replace('.', '/') + "/" + this.artifactId + "/" + this.version + "/" + this.artifactId
					+ "-" + this.version + fileType.getExt() + "." + packagingType.getExt();
		}
	}

	String getPomFile() {
		decodeProp();
		return RepoDownloader.downloadRoot.getAbsolutePath() + File.separator + this.artifactId + "-" + this.version
				+ ".pom";
	}

	String getPOMUri() {
		decodeProp();
		if (pathReplace) {
			return this.groupId.replace('.', '/') + "/" + this.artifactId.replace('.', '/') + "/" + this.version + "/"
					+ this.artifactId + "-" + this.version + ".pom";
		} else {
			return this.groupId.replace('.', '/') + "/" + this.artifactId + "/" + this.version + "/" + this.artifactId
					+ "-" + this.version + ".pom";
		}
	}

	void extractPackagingType(Document pomDocument, XPath xPath) throws XPathExpressionException {
		packagingType = PackagingType.jar;
		Node node = (Node) xPath.compile("/project/packaging").evaluate(pomDocument, XPathConstants.NODE);
		if (node != null)
			packagingType = PackagingType.get(node.getTextContent().toLowerCase());
	}

	void extractProperties(Document pomDocument, XPath xPath) throws XPathExpressionException {
		Node node = (Node) xPath.compile("/project/properties").evaluate(pomDocument, XPathConstants.NODE);
		// System.out.println(this+" properties node :: "+node);
		if (node != null) {
			NodeList childNodes = node.getChildNodes();
			for (int j = 0; j < childNodes.getLength(); j++) {
				Node n = childNodes.item(j);
				properties.put(n.getNodeName(), n.getTextContent());
			}
			// System.out.println(this+" properties :: "+properties);
		}
	}

	void extractParent(Set<Dependency> ret, Document pomDocument, XPath xPath) throws XPathExpressionException {
		Node node = (Node) xPath.compile("/project/parent").evaluate(pomDocument, XPathConstants.NODE);
		if (node != null) {
			parent = getDependency(node);
		}
	}

	void extractDependencies(Set<Dependency> ret, Document pomDocument, XPath xPath) throws XPathExpressionException {
		NodeList nodeList = (NodeList) xPath.compile("/project/dependencies/dependency").evaluate(pomDocument,
				XPathConstants.NODESET);
		for (int i = 0; i < nodeList.getLength(); i++) {
			Dependency e = getDependency(nodeList.item(i));
			if (e != null) {
				e.projectParent = this;
				if (e.groupId.equalsIgnoreCase(groupId)) {
					if (parent != null)
						e.parent = parent;
					else
						e.parent = this;
				}
				e.decodeProp();
				// logger.info("dependency :: "+e);
				ret.add(e);
			}
		}
	}

	void extractDependencyManagementDependencies(Set<Dependency> ret, Document pomDocument, XPath xPath)
			throws XPathExpressionException {
		NodeList nodeList = (NodeList) xPath.compile("/project/dependencyManagement/dependencies/dependency")
				.evaluate(pomDocument, XPathConstants.NODESET);
		for (int i = 0; i < nodeList.getLength(); i++) {
			Dependency e = getDependency(nodeList.item(i));
			if (e != null) {
				e.projectParent = this;
				ret.add(e);
			}
		}
	}

	Dependency getDependency(Node dep) {
		NodeList childNodes = dep.getChildNodes();
		String g = null, a = null, v = null, s = "compile", o = "false";
		for (int j = 0; j < childNodes.getLength(); j++) {
			Node n = childNodes.item(j);
			if (n.getNodeName().equalsIgnoreCase("groupId")) {
				g = decodeProp(n.getTextContent(), IdType.group);
			} else if (n.getNodeName().equalsIgnoreCase("scope")) {
				s = decodeProp(n.getTextContent(), IdType.group);
			} else if (n.getNodeName().equalsIgnoreCase("optional")) {
				o = decodeProp(n.getTextContent(), IdType.group);
			} else if (n.getNodeName().equalsIgnoreCase("artifactId")) {
				a = decodeProp(n.getTextContent(), IdType.artifact);
			} else if (n.getNodeName().equalsIgnoreCase("version")) {
				v = decodeProp(n.getTextContent(), IdType.version);
				if (v.equals("${project.version}")) {
					v = this.version;
				}
			}
		}

		if (!Boolean.parseBoolean(o) && "compile".equalsIgnoreCase(s)) {
			Dependency dependency = Dependency.get(g, a, v);
			return dependency;
		} else {
			return null;
		}

	}

	private String decodeProp(String ver, IdType type) {
		if (ver == null) {
			if (type == IdType.version) {
				if (parent != null && parent.groupId.equalsIgnoreCase(groupId)) {
					return decodeProp(parent.version, type);
				} else {
					ver = "${" + artifactId + ".version}";
				}
			}
		} else if (type == IdType.version) {
			int s = ver.indexOf("[");
			if (s != -1) {
				int e = ver.indexOf(")");
				if (e == -1)
					e = ver.length();
				// logger.info("oldversion :: "+ver+" s :: "+s+" e :: "+e);
				String vers = ver.substring(s + 1, e);
				String[] splitVers = vers.split(",");
				String nv = vers;
				if (splitVers.length > 0)
					nv = splitVers[0];
				// logger.info("oldversion :: "+ver+" newversion :: "+nv);
				ver = nv;
			}
		}

		String txt = ver;
		if (ver.indexOf("${") != -1) {
			Dependency p = projectParent;
			while (p != null) {
				properties.putAll(p.properties);
				p = p.parent;
			}
			for (Entry<String, String> e : properties.entrySet()) {
				String k = "${" + e.getKey() + "}";
				String v = e.getValue();
				int index = txt.indexOf(k);
				while (index >= 0) {
					txt = txt.substring(0, index) + v + txt.substring(index + k.length(), txt.length());
					index = txt.indexOf(k);
				}
			}
		}
		return txt;
	}

	void download(File file, String uri, int cnt) throws FileNotFoundException {
		if (file.exists()) {
			return;
		}

		try {
			URL url = new URL(uri);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.connect();
			try (InputStream inputStream = urlConnection.getInputStream();
					OutputStream output = new FileOutputStream(file);) {
				int n;
				byte[] buffer = new byte[1024];
				while ((n = inputStream.read(buffer)) > -1) {
					output.write(buffer, 0, n);
				}
			}
			// System.out.println("Download "+url.toExternalForm()+" "+this );
			logger.info("Download " + url.toExternalForm());// +" "+this
		} catch (MalformedURLException e) {
			logger.error("download", e);
		} catch (FileNotFoundException e) {
			throw e;
		} catch (IOException e) {
			logger.error("download", e);
		}
	}

	DocumentBuilder create() {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;
		try {
			builder = builderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			logger.error("createPom", e);
		}
		return builder;
	}

	@Override
	public int hashCode() {
		// decodeProp();
		final int prime = 31;
		int result = 1;
		result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
		result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		// decodeProp();
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Dependency other = (Dependency) obj;
		if (artifactId == null) {
			if (other.artifactId != null)
				return false;
		} else if (!artifactId.equals(other.artifactId))
			return false;
		if (groupId == null) {
			if (other.groupId != null)
				return false;
		} else if (!groupId.equals(other.groupId))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version;
	}

}