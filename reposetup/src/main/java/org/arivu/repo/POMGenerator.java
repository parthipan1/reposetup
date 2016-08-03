/**
 * 
 */
package org.arivu.repo;

import static java.nio.file.FileVisitResult.CONTINUE;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Parthipan
 *
 */
public class POMGenerator {

	final static String serverHost = "http://localhost:8080";
	final static String repoName = "internal";
	final static String repoId = "archiva.internal";
	static final String POM_FOLDER = ".\\pom\\";
	static final String DEFAULT_VERSION_0_0 = "0.0";
	
	final static String mavenCmd1 = "mvn deploy:deploy-file -Dfile=";
	final static String mavenCmd2 = " -DpomFile=";
	final static String mavenCmd3 = " "+
    " -DrepositoryId="+repoId+" "+
    " -Durl="+serverHost+"/repository/"+repoName+"/";// -e -X
	
	static final Collection<POM> poms = new ArrayList<POMGenerator.POM>();
	
	static class POM{
		
		String groupId,artifactId,version;
		final Path file;
		final String filename;

		public POM(Path file) {
			super();
			this.file = file;
			this.filename = file.getFileName().toString();
			setValues();
		}

		String replaceAll(String str,char oldC,char newC){
			if( str==null ) return null;
			int length = str.length();
			char[] ncs = new char[length];
			char[] ocs = str.toCharArray();
			for(int i=0;i<length;i++){
				char c = ocs[i];
				if( c == oldC ){
					c = newC;
				}
				ncs[i] = c;
			}
			return new String(ncs);
		}
		
		void setValues() {
//			System.out.println(filename);
			String name = replaceAll(filename.replaceAll(".jar", ""), '.', ':')  ;//.replaceAll(".", ":");
			String[] split = name.split(":");
			String v = null;
			int i=0,j=0;
			for(String s:split){
				try {
					if( v==null ){
						Integer.parseInt(s);
						v = s;
						i=j;
					}else{
						v = v+"."+s;
					}
				} catch (NumberFormatException e) {
				}
				j++;
			}
			String s2 = null;
			if( i==0){
				v = DEFAULT_VERSION_0_0;
			}else if( i>0 ){
				String fv = split[i-1];
				String regex = "-";
				String[] split2 = fv.split(regex);
				if( split2.length ==0 ){
					regex = "_";
					split2 = fv.split(regex);
				}
				if( split2.length ==0 ){
					s2 = fv;
				}else{
					String s1 = split2[split2.length-1];
					try {
						Integer.parseInt(s1);
						v = s1+"."+v;
						for( String g:split2 ){
							if (!g.equals(s1)) {
								if (s2 == null) {
									s2 = g;
								} else {
									s2 = s2 + regex + g;
								}
							}
						}				
					} catch (NumberFormatException e) {
						s2 = fv;
					}
				}
			}
			
			this.version = replaceAll(v, ':', '.');

			if(split.length<=1){
				this.groupId = name;
				this.artifactId = name;
			}else if (DEFAULT_VERSION_0_0.equals(v)){
				this.groupId = name;
				this.artifactId = name;
			}else{
				String g = null;
				String a = null;
				
				for(int h=2;h<split.length-2;h++){
					if(a==null) a=split[h];
					else a=a+"."+split[h];
				}
				
				if(a==null){
					 a =  s2;
					 g = a;
				}else{
					 a = a+"."+s2;
					 g = split[0]+"."+split[1];
				}
				this.groupId = replaceAll(g, ':', '.');
				this.artifactId = replaceAll(a, ':', '.');
			}
		}

		String getPomXml(){
			return "<project> "+
					  "<modelVersion>4.0.0</modelVersion> "+
					  "<groupId>"+groupId+"</groupId> "+
					  "<artifactId>"+artifactId+"</artifactId> "+
					  "<version>"+version+"</version> "+
					  "</project>";
		}

		void createPomFile(){
			File f = new File(getMavenPomFileName());
			if(f.exists()){
				f.delete();
			}
			try {
				f.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			try
			{
			    BufferedWriter writer = new BufferedWriter(new FileWriter(f));
			    writer.write (getPomXml());
			    writer.close();
			} catch(Exception e)
			{
			    throw new RuntimeException(e);
			}
		}
		
		String getGradleDependency(){
			return " compile '"+groupId+":"+artifactId+":"+version+"'";
		}
		
		String getMavenPomFileName(){
			return new File(POM_FOLDER+filename.replaceAll(".jar", ".pom")).getAbsolutePath();
		}
		
		String getMavenCommand(){
			String path = file.toAbsolutePath().toString();
			String mavenPomFileName = getMavenPomFileName();
//			return  mavenCmd.replaceAll("%jar%", path)
//								.replaceAll("%pom%", mavenPomFileName);
			return "call "+mavenCmd1+path+mavenCmd2+mavenPomFileName+mavenCmd3+"\npause";
		}

		@Override
		public String toString() {
			return "[groupId=" + groupId + ", artifactId=" + artifactId
					+ ", version=" + version + "]";
		}
		
	}

	static class PrintFiles
    extends SimpleFileVisitor<Path> {

    // Print information about
    // each type of file.
    @Override
    public FileVisitResult visitFile(Path file,
                                   BasicFileAttributes attr) {
        if (attr.isSymbolicLink()) {
            System.out.format("Symbolic link: %s ", file);
        } else if (attr.isRegularFile()) {
//            System.out.format("Regular file: %s ", file);
            if( file.getFileName().toString().endsWith("jar") ){
//            	System.out.format("Regular file: %s ", file);
//            	System.out.println(new POM(file).getGradleDependency());
            	poms.add(new POM(file));
//            	System.out.println(new POM(file.getFileName().toString()).getGradleDependency());
            }
        } else {
            System.out.format("Other: %s ", file);
        }
//        System.out.println("(" + attr.size() + "bytes)");
        return CONTINUE;
    }

    // Print each directory visited.
    @Override
    public FileVisitResult postVisitDirectory(Path dir,
                                          IOException exc) {
//        System.out.format("Directory: %s%n", dir);
        return CONTINUE;
    }

    // If there is some error accessing
    // the file, let the user know.
    // If you don't override this method
    // and an error occurs, an IOException 
    // is thrown.
    @Override
    public FileVisitResult visitFileFailed(Path file,
                                       IOException exc) {
        System.err.println(exc);
        return CONTINUE;
    }
}
	
	/**
	 * @param args
	 * @throws URISyntaxException 
	 * @throws IOException 
	 */
	public static void main(String[] args) throws URISyntaxException, IOException {
//		Path startingDir = Paths.get(new File( "C:\\soft\\apache-tomcat-7.0.56\\shared\\lib_bak" ).toURI() );
		Path startingDir = Paths.get(new File( "C:\\Users\\AadityaD\\Desktop\\snw\\IOT\\app\\jiolib" ).toURI() );
		PrintFiles pf = new PrintFiles();
		Files.walkFileTree(startingDir, pf);
		
		File pomFolder = new File(POM_FOLDER);
		pomFolder.mkdirs();
		
		File mavenCmdFile = new File("import.cmd");
		if(mavenCmdFile.exists()){
			mavenCmdFile.delete();
		}
		try {
			mavenCmdFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		File gradleDepFile = new File("gradle.dependency");
		if(gradleDepFile.exists()){
			gradleDepFile.delete();
		}
		try {
			gradleDepFile.createNewFile();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try
		{
		    BufferedWriter mvnCmdWriter = new BufferedWriter(new FileWriter(mavenCmdFile));
		    BufferedWriter grdDepWriter = new BufferedWriter(new FileWriter(gradleDepFile));
//		    writer.write (getPomXml());
		    
		    for(POM pom:poms){
		    	pom.createPomFile();
		    	mvnCmdWriter.write(pom.getMavenCommand()+"\n");
		    	grdDepWriter.write(pom.getGradleDependency()+"\n");
		    }
		    
		    grdDepWriter.close();
		    mvnCmdWriter.close();
		} catch(Exception e)
		{
		    throw new RuntimeException(e);
		}
		
//		System.out.println(new POM("apache-log4j-extras-1.2.17.jar").getGradleDependency());
		
	}

}
