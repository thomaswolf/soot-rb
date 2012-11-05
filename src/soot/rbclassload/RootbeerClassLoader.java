/* Soot - a J*va Optimization Framework
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/* The soot.rbclassload package is:
 * Copyright (C) 2012 Phil Pratt-Szeliga
 * Copyright (C) 2012 Marc-Andre Laverdiere-Papineau
 */

package soot.rbclassload;

import soot.G;
import soot.Singletons;
import soot.SourceLocator;
import soot.SootMethod;
import soot.SootClass;
import soot.SootResolver;
import soot.ClassSource;
import soot.CoffiClassSource;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileNotFoundException;

public class RootbeerClassLoader {

  private Map<String, SubScene> m_subScenes;
  private SubScene m_currSubScene;
  private Map<String, Set<String>> m_packageNameCache;
  private List<String> m_sourcePaths;
  private Map<String, String> m_filenameToJar;
  private String m_tempFolder;
  private List<String> m_classPaths;
  private int m_loadedCount;

  public RootbeerClassLoader(Singletons.Global g){
    m_subScenes = new HashMap<String, SubScene>();
    m_packageNameCache = new HashMap<String, Set<String>>();
    m_filenameToJar = new HashMap<String, String>();
    m_tempFolder = "temp" + File.separator;
    m_loadedCount = 0;
  }

  public static RootbeerClassLoader v() { 
    return G.v().soot_rbclassload_RootbeerClassLoader(); 
  }

  public void loadNecessaryClasses(){
    m_currSubScene = new SubScene();

    m_sourcePaths = SourceLocator.v().sourcePath();
    m_classPaths = SourceLocator.v().classPath();
    
    System.out.println("source_paths: ");
    for(String path : m_sourcePaths){
      System.out.println(path);
    }
    cachePackageNames();
    loadBuiltIns();
    loadToHierarchies();
    //doDfs(kernel_method);
    //buildFullCallGraph(kernel_method);
    findReachableMethods();
    buildHierarchy();
  }

  private void cachePackageNames(){
    List<String> paths = SourceLocator.v().classPath();
    String[] to_cache = new String[paths.size()];
    to_cache = paths.toArray(to_cache);
    Arrays.sort(to_cache);
    for(String jar : to_cache){
      if(jar.endsWith(".jar")){
        System.out.println("caching package names for: "+jar);
        try {
          JarInputStream jin = new JarInputStream(new FileInputStream(jar));
          while(true){
            JarEntry entry = jin.getNextJarEntry();
            if(entry == null){
              break;
            }
            String name = entry.getName();
            if(name.endsWith(".class")){
              name = name.replace(".class", "");
              name = name.replace("/", ".");
              name = getPackageName(name);
            } else {
              name = name.replace("/", ".");
              name = name.substring(0, name.length()-1);
            }
            if(m_packageNameCache.containsKey(name)){
              Set<String> jars = m_packageNameCache.get(name);
              if(jars.contains(jar) == false){
                jars.add(jar);
              }
            } else {
              Set<String> jars = new HashSet<String>();
              jars.add(jar);
              m_packageNameCache.put(name, jars);
            }
          }
          jin.close();
        } catch(Exception ex){
          ex.printStackTrace();
        }
      }
    }
  }

  private String getPackageName(String className) {
    String[] tokens = className.split("\\.");
    String ret = "";
    for(int i = 0; i < tokens.length - 1; ++i){
      ret += tokens[i];
      if(i < tokens.length - 2){
        ret += ".";
      }
    }
    return ret;
  }

  private void loadBuiltIns(){
    //copied from soot.Scene.addSootBasicClasses()

    System.out.println("loading built-ins...");
    addBasicClass("java.lang.Object");
    addBasicClass("java.lang.Class");

    addBasicClass("java.lang.Void");
    addBasicClass("java.lang.Boolean");
    addBasicClass("java.lang.Byte");
    addBasicClass("java.lang.Character");
    addBasicClass("java.lang.Short");
    addBasicClass("java.lang.Integer");
    addBasicClass("java.lang.Long");
    addBasicClass("java.lang.Float");
    addBasicClass("java.lang.Double");

    addBasicClass("java.lang.String");
    addBasicClass("java.lang.StringBuffer");

    addBasicClass("java.lang.Error");
    addBasicClass("java.lang.AssertionError");
    addBasicClass("java.lang.Throwable");
    addBasicClass("java.lang.NoClassDefFoundError");
    addBasicClass("java.lang.ExceptionInInitializerError");
    addBasicClass("java.lang.RuntimeException");
    addBasicClass("java.lang.ClassNotFoundException");
    addBasicClass("java.lang.ArithmeticException");
    addBasicClass("java.lang.ArrayStoreException");
    addBasicClass("java.lang.ClassCastException");
    addBasicClass("java.lang.IllegalMonitorStateException");
    addBasicClass("java.lang.IndexOutOfBoundsException");
    addBasicClass("java.lang.ArrayIndexOutOfBoundsException");
    addBasicClass("java.lang.NegativeArraySizeException");
    addBasicClass("java.lang.NullPointerException");
    addBasicClass("java.lang.InstantiationError");
    addBasicClass("java.lang.InternalError");
    addBasicClass("java.lang.OutOfMemoryError");
    addBasicClass("java.lang.StackOverflowError");
    addBasicClass("java.lang.UnknownError");
    addBasicClass("java.lang.ThreadDeath");
    addBasicClass("java.lang.ClassCircularityError");
    addBasicClass("java.lang.ClassFormatError");
    addBasicClass("java.lang.IllegalAccessError");
    addBasicClass("java.lang.IncompatibleClassChangeError");
    addBasicClass("java.lang.LinkageError");
    addBasicClass("java.lang.VerifyError");
    addBasicClass("java.lang.NoSuchFieldError");
    addBasicClass("java.lang.AbstractMethodError");
    addBasicClass("java.lang.NoSuchMethodError");
    addBasicClass("java.lang.UnsatisfiedLinkError");

    addBasicClass("java.lang.Thread");
    addBasicClass("java.lang.Runnable");
    addBasicClass("java.lang.Cloneable");

    addBasicClass("java.io.Serializable");

    addBasicClass("java.lang.ref.Finalizer");
  }

  private void addBasicClass(String class_name) {
    addBasicClass(class_name, SootClass.HIERARCHY);
  }

  private void addBasicClass(String class_name, int level) {
    SootResolver.v().resolveClass(class_name, level);
  }

  private boolean findClass(Collection<String> jars, String filename) throws Exception {
    for(String jar : jars){
      JarInputStream fin = new JarInputStream(new FileInputStream(jar));
      while(true){
        JarEntry entry = fin.getNextJarEntry();
        if(entry == null){
          break;
        }
        if(entry.getName().equals(filename) == false){
          continue;
        }
        m_filenameToJar.put(filename, jar);
        WriteJarEntry writer = new WriteJarEntry();
        writer.write(entry, fin, m_tempFolder);
        fin.close();
        return true;
      }
      fin.close();
    }
    return false;
  }

  private String classNameToFilename(String className){
    String filename = className.replace(".", "/");
    filename += ".class";
    return filename;
  }

  public void findClass(String className) throws Exception {
    String package_name = getPackageName(className);
    String filename = className.replace(".", "/");
    filename += ".class";
    Set<String> jar_cache = m_packageNameCache.get(package_name);
    if(jar_cache == null){
      if(package_name.equals("")){
        findClass(m_classPaths, filename);
        return;
      } else {
        return;
      }
    }
    if(findClass(jar_cache, filename)){
      return;
    }
    //maybe there is a class in the default package, try all if not found in cache.
    if(package_name.equals("")){
      findClass(m_classPaths, filename);
    }
  }

  public ClassSource getClassSource(String class_name) {
    String filename = classNameToFilename(class_name);

    System.out.println("loading: "+class_name);

    File file = new File(m_tempFolder + filename);
    if(!file.exists()){
      if(m_filenameToJar.containsKey(filename) == false){
        try {
          findClass(class_name);
        } catch(Exception ex){
          //ignore
        }
      } 
    }

    m_loadedCount++;

    try {
      InputStream stream = new FileInputStream(m_tempFolder + filename);
		  if(stream == null){
        return null;
      }
		  return new CoffiClassSource(class_name, stream);
    } catch(FileNotFoundException ex){
      return null;
    }
  }

  private void loadToHierarchies(){ 
    System.out.println("loaded "+m_loadedCount+" classes");
    for(String src_folder : m_sourcePaths){
      File file = new File(src_folder);
      loadToHierarchies(file);
    }
  }

  private void loadToHierarchies(File curr){
    File[] children = curr.listFiles();
    for(File child : children){
      if(child.isDirectory()){
        loadToHierarchies(child);
      } else {
        String name = child.getName();
        if(name.startsWith(".") || name.endsWith(".class") == false){
          continue;
        }
        System.out.println("to_load: "+child.getAbsolutePath());
      }
    }
  }

  private void doDfs(SootMethod method){

  }

  private void buildFullCallGraph(SootMethod method){

  }

  private void findReachableMethods(){

  }

  private void buildHierarchy(){

  }

}