package org.arivu.repo;

import java.io.File;
import java.io.FilenameFilter;

public class NameGenerator {

  public static void main(String[] args){
    
    String baseDir = "C:\\soft\\ssh-gradle\\";
    File dir = new File(baseDir);
    
    String line1 = "pause";
    String line2 = "call mvn deploy:deploy-file -Dfile="+baseDir;
    String line3 = ".jar -DpomFile="+baseDir;
    String line4 = ".pom -DrepositoryId=archiva.plugin  -Durl=http://10.135.81.131:8080/repository/plugin/";
    
    
    File[] list = dir.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File arg0, String arg1) {
        boolean b = arg1.endsWith(".jar");//arg1
//        System.out.println(arg1+" "+b);
        return b;
      }
    });
    
    for( File f:list ){
      String name = f.getName();
      String fileName = name.substring(0, name.length()-4);
      System.out.println( line1 );
      System.out.println( line2+fileName+line3+fileName+line4 );
    }
    
    
  }
  
}
