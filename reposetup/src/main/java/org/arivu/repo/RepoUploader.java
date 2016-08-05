/**
 * 
 */
package org.arivu.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

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
import org.xml.sax.SAXException;

/**
 * @author Parthipan
 *
 */
public class RepoUploader {
	static final Logger logger = LoggerFactory.getLogger("RepoUploader");
	private static boolean runExe = false;
	
	private static final File ROOT_DIR = new File(".");
	private static long THRESHOLD_LIMIT = 1*1024*1024L;
	
	private static String MAVEN_EXE = System.getProperty("mvn","/Users//Downloads/apache-maven-3.3.9/bin/mvn");;
	
	private static String[] MODULES = {System.getProperty("download","download")};
	private static String[] REPOS = {System.getProperty("module","common")};
	
	private static String URI = System.getProperty("repo","http://ec2-52-76-182-175.ap-southeast-1.compute.amazonaws.com/repository/");//http://10.135.81.131:8080/repository/
	
	private static boolean deleteAfterUpload = true;
	
	private static final Collection<String> BIG_FILES = new ArrayList<>();
	
	static{
		Properties p = new Properties();
		try {
			p.load(new FileInputStream(new File("repo.properties")));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		runExe = Boolean.parseBoolean(p.getProperty("runExe"));
		THRESHOLD_LIMIT = Long.parseLong(p.getProperty("fileThresholdLimit"));
		MAVEN_EXE = p.getProperty("mvn");
		MODULES = new String[]{p.getProperty("download")};
		REPOS = new String[]{p.getProperty("module")};
		URI = p.getProperty("repo");
		deleteAfterUpload = Boolean.parseBoolean(p.getProperty("deleteAfterUpload"));
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length>0){
			runExe = Boolean.parseBoolean(args[0]);
		}
		uploadModules();
		printBigFiles();
	}

	static void printBigFiles() {
		logger.info("Big Files:");
		for(String cmd:BIG_FILES){
			logger.info(cmd);
		}
	}

	private static void uploadModules() {
		LinkedList<String> moduleQueue = new LinkedList<>(Arrays.asList(MODULES));
		LinkedList<String> repoQueue = new LinkedList<>(Arrays.asList(REPOS));
		while( !moduleQueue.isEmpty() ){
			upload(moduleQueue.pop(),repoQueue.pop());
		}
	}

//	private static void uploadFiles() {
//		final String basepathname = ROOT_DIR.getAbsolutePath()+File.separator+fileModule;
//		for( String f: files ){
//			upload(fileRepo, basepathname, new File(basepathname+File.separator+f));
//		}
//	}

	static void upload( File f) {
		final String basepathname = ROOT_DIR.getAbsolutePath()+File.separator+MODULES[0];
		upload(REPOS[0], basepathname, f);
	}
	
	static void upload(final String module, final String repo) {
		final String basepathname = ROOT_DIR.getAbsolutePath()+File.separator+module;
		final File[] list = new File(basepathname).listFiles(new FilenameFilter() {
			
			@Override
			public boolean accept(File dir, String name) {
				return name.endsWith("xml")||name.endsWith("pom");
			}
		});
		List<File> rl = new ArrayList<>();
		for(File f:list){
			rl.add(f);
		}
		Collections.reverse(rl);
		for(File f:rl){
			upload(repo, basepathname, f);
		}
	}

	static void upload(final String repo, final String basepathname, File f) {
		String name = f.getName();
		CharSequence name2 = name.subSequence(0, name.length()-4);
		CharSequence name3 = name2;
		
		if( name.endsWith("xml") ){
			f.renameTo(new File(basepathname+File.separator+name2.toString()));
			name3 = name2.subSequence(0, name2.length()-4);
		}
		final String f1 = basepathname+File.separator+name3.toString();

		PackagingType packaging = getPackaging(new File(f1+".pom"));
		File artifactFile = new File(f1+"."+packaging.getExt());
		File artifactSourcesFile = new File(f1+"-sources."+packaging.getExt());
		File artifactDocFile = new File(f1+"-javadoc."+packaging.getExt());
		final String[] cmd = packaging.getCmd(f1,MAVEN_EXE,repo,URI, artifactSourcesFile.exists(), artifactDocFile.exists());
		String mvnCmd = toString(cmd);
		if(runExe){
//			logger.info(" Before executeCommand artifactFile.length() :: "+artifactFile.length()+" THRESHOLD_LIMIT :: "+THRESHOLD_LIMIT);
			if(artifactFile.length()>THRESHOLD_LIMIT){
//				logger.info(mvnCmd);
				BIG_FILES.add(mvnCmd);
			}else{
				executeCommand(cmd);
				if(deleteAfterUpload){
					File[] df = new File[]{f,artifactFile,artifactSourcesFile,artifactDocFile};
					
					for(File df1:df){
						if(df1.exists())
							df1.delete();
					}
				}
			}
		} else {
			logger.info(mvnCmd);
		}
		
	}

	static PackagingType getPackaging(File file2) {
		DocumentBuilderFactory builderFactory =
		        DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = null;
		try {
		    builder = builderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
		    e.printStackTrace();  
		}
		try {
		    Document pomDocument = builder.parse(new FileInputStream(file2));
		    XPath xPath =  XPathFactory.newInstance().newXPath();
		    Node node = (Node) xPath.compile("/project/packaging").evaluate(pomDocument, XPathConstants.NODE);
		    if(node==null)
		    	return PackagingType.jar;
		    String packaging = node.getTextContent();
		    return PackagingType.get(packaging.toLowerCase());
		} catch (SAXException|IOException|XPathExpressionException e) {
			e.printStackTrace();
		}
		return PackagingType.jar;
	}

	static String toString(String... objs){
		StringBuffer b = new StringBuffer();
		if (objs!=null) {
			for (Object o : objs) {
				if (o != null)
					b.append(o.toString()).append(" ");
				else
					b.append("null ");
			} 
		}
		return b.toString();
	}
	static void executeCommand(final String[] command) {
		try{
			ProcessBuilder pb = new ProcessBuilder(command);
	        pb.directory(ROOT_DIR);
	        logger.info(toString(command));
	        Process p = pb.start();
			p.waitFor();
			getPrint(p.getInputStream(),new Printer() {
				
				@Override
				public void println(String line) {
//					System.out.println(line);
					logger.info(line);
				}
			});
			getPrint(p.getErrorStream(),new Printer() {
				
				@Override
				public void println(String line) {
//					System.err.println(line);
					logger.error(line);
				}
			});
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	static void getPrint(final InputStream in , Printer out ){
		if ( in != null ) {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));) {
				String line = "";
				while ((line = reader.readLine()) != null) {
					out.println(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
	}
	
	static interface Printer{
		void println(String line);
	}
}
//-Dsources=./path/to/artifact-name-1.0-sources.jar \
//-Djavadoc=./path/to/artifact-name-1.0-javadoc.jar
enum PackagingType{
	jar, war, zip, ear , pom ,
	eclipse_plugin{
		@Override
		public String getExt() {
			return "jar";
		}
	}, 
	bundle{
		@Override
		public String[] getCmd(String f,String exe,String repo,String uri, boolean sourceExists, boolean javaDocsExists){
			Collection<String> cmds = new ArrayList<>();
			cmds.add(exe);
			cmds.add("deploy:deploy-file");
			cmds.add("-Dfile="+f+"."+getExt());
			cmds.add("-DpomFile="+f+".pom");
			cmds.add("-Dpackaging=jar");
			if( sourceExists )
				cmds.add("-Dsources="+f+"-sources.jar");
			
			if( javaDocsExists )
				cmds.add("-Djavadoc="+f+"-javadoc.jar");
			
			cmds.add("-DrepositoryId=archiva."+repo);
			cmds.add("-Durl="+uri+repo+"/");
			cmds.add("-e");
			return cmds.toArray(new String[]{});
			
//			return new String[]{exe,"deploy:deploy-file","-Dfile="+f+"."+getExt(),"-DpomFile="+f+".pom","-Dpackaging=jar" ,"-DrepositoryId=archiva."+repo,"-Durl="+uri+repo+"/", "-e"};
		}
		@Override
		public String getExt() {
			return "jar";
		}
	};
	
	public String[] getCmd(String f,String exe,String repo,String uri, boolean sourceExists, boolean javaDocsExists){
		Collection<String> cmds = new ArrayList<>();
		cmds.add(exe);
		cmds.add("deploy:deploy-file");
		cmds.add("-Dfile="+f+"."+getExt());
		cmds.add("-DpomFile="+f+".pom");
		
		if( sourceExists )
			cmds.add("-Dsources="+f+"-sources.jar");
		
		if( javaDocsExists )
			cmds.add("-Djavadoc="+f+"-javadoc.jar");
		
		cmds.add("-DrepositoryId=archiva."+repo);
		cmds.add("-Durl="+uri+repo+"/");
		cmds.add("-e");
		return cmds.toArray(new String[]{});
//		return new String[]{exe,"deploy:deploy-file","-Dfile="+f+"."+getExt(),"-DpomFile="+f+".pom","-DrepositoryId=archiva."+repo,"-Durl="+uri+repo+"/", "-e"};
	}

	public String getExt() {
		return name();
	}
	
	public static PackagingType get(String name){
		try {
			PackagingType valueOf = PackagingType.valueOf(name.replace('-', '_'));
			if(valueOf!=null)
				return valueOf;
			else
				return jar;
		} catch (IllegalArgumentException e) {
			System.err.println(" packaging :: "+name+" Not configured!");
			return jar;
		}
	}
	
}
