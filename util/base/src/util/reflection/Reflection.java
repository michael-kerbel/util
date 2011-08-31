package util.reflection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


public class Reflection {

   /**
    * Scans all classes accessible from the context class loader which belong to the given package and subpackages.<p/>
    * <b>Beware</b>: This fails in many cases! It should only work for classes found locally, getting really ALL classes is impossible.<p/>
    * source: http://snippets.dzone.com/posts/show/4831
    *
    * @param packageName The base package
    */
   @SuppressWarnings("unchecked")
   public static List<Class> getClasses( String packageName ) throws ClassNotFoundException, IOException {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      assert classLoader != null;
      String path = packageName.replace('.', '/');
      Enumeration<URL> resources = classLoader.getResources(path);
      List<File> dirs = new ArrayList<File>();
      while ( resources.hasMoreElements() ) {
         URL resource = resources.nextElement();
         String fileName = resource.getFile();
         String fileNameDecoded = URLDecoder.decode(fileName, "UTF-8");
         dirs.add(new File(fileNameDecoded));
      }
      ArrayList<Class> classes = new ArrayList<Class>();
      for ( File directory : dirs ) {
         classes.addAll(findClasses(directory, packageName));
      }
      return classes;
   }

   /**
    * Get a <code>Field</code> instance for any the combination of class and field name.
    * This utility method allows accessing protected, package protected and private fields - at least if the 
    * <code>SecurityManager</code> allows this hack, which it does in practically all runtimes...       
    */
   public static Field getField( Class c, String fieldName ) throws NoSuchFieldException {
      try {
         return c.getField(fieldName);
      }
      catch ( NoSuchFieldException e ) {
         try {
            // search protected, package protected and private methods
            while ( c != Object.class ) {
               for ( Field f : c.getDeclaredFields() ) {
                  if ( f.getName().equals(fieldName) ) {
                     f.setAccessible(true); // enable access to the method - ...hackity hack
                     return f;
                  }
               }
               c = c.getSuperclass();
            }
         }
         catch ( SecurityException ee ) {
            // ignore and throw the original NoSuchFieldException
         }
         // search not successful -> throw original NoSuchFieldException
         throw e;
      }
   }

   /**
    * Get a <code>Field</code> instance for any the combination of class and field name.
    * This utility method allows accessing protected, package protected and private fields - at least if the 
    * <code>SecurityManager</code> allows this hack, which it does in practically all runtimes...       
    */
   public static Field getFieldQuietly( Class c, String fieldName ) {
      try {
         return getField(c, fieldName);
      }
      catch ( NoSuchFieldException argh ) {
         return null;
      }
   }

   /**
    * Get a <code>Method</code> instance for any the combination of class, method name and parameter signature.
    * This utility method allows accessing protected, package protected and private methods - at least if the 
    * <code>SecurityManager</code> allows this hack, which it does in practically all runtimes...       
    */
   public static Method getMethod( Class c, String methodName, Class... argumentClasses ) throws NoSuchMethodException {
      try {
         return c.getMethod(methodName, argumentClasses);
      }
      catch ( NoSuchMethodException e ) {
         try {
            // search protected, package protected and private methods
            while ( c != Object.class ) {
               out: for ( Method m : c.getDeclaredMethods() ) {
                  Class[] parameterTypes = m.getParameterTypes();
                  if ( m.getName().equals(methodName) && argumentClasses.length == parameterTypes.length ) {
                     for ( int j = 0, length = argumentClasses.length; j < length; j++ ) {
                        if ( !argumentClasses[j].equals(parameterTypes[j]) ) {
                           continue out;
                        }
                     }
                     m.setAccessible(true); // enable access to the method - ...hackity hack
                     return m;
                  }
               }
               c = c.getSuperclass();
            }
         }
         catch ( SecurityException ee ) {
            // ignore and throw the original NoSuchMethodException
         }
         // search not successful -> throw original NoSuchMethodException
         throw e;
      }
   }

   /**
    * Get a <code>Method</code> instance for any the combination of class, method name and parameter signature.
    * This utility method allows accessing protected, package protected and private methods - at least if the 
    * <code>SecurityManager</code> allows this hack, which it does in practically all runtimes...       
    */
   public static Method getMethodQuietly( Class c, String methodName, Class... argumentClasses ) {
      try {
         return getMethod(c, methodName, argumentClasses);
      }
      catch ( NoSuchMethodException argh ) {
         return null;
      }
   }

   public static Object invokeQuietly( Method m, Object instance, Object... arguments ) {
      try {
         return m.invoke(instance, arguments);
      }
      catch ( Exception argh ) {
         return null;
      }
   }

   /**
    * Recursive method used to find all classes in a given directory and subdirs.
    *
    * @param directory   The base directory
    * @param packageName The package name for classes found inside the base directory
    * @return The classes
    * @throws ClassNotFoundException
    */
   @SuppressWarnings("unchecked")
   private static List<Class> findClasses( File directory, String packageName ) throws ClassNotFoundException {
      List<Class> classes = new ArrayList<Class>();
      if ( !directory.exists() ) {
         return classes;
      }
      File[] files = directory.listFiles();
      for ( File file : files ) {
         String fileName = file.getName();
         if ( file.isDirectory() ) {
            assert !fileName.contains(".");
            classes.addAll(findClasses(file, packageName + "." + fileName));
         } else if ( fileName.endsWith(".class") && !fileName.contains("$") ) {
            Class _class;
            try {
               _class = Class.forName(packageName + '.' + fileName.substring(0, fileName.length() - 6));
            }
            catch ( ExceptionInInitializerError e ) {
               // happen, for example, in classes, which depend on 
               // Spring to inject some beans, and which fail, 
               // if dependency is not fulfilled
               _class = Class.forName(packageName + '.' + fileName.substring(0, fileName.length() - 6), false, Thread.currentThread().getContextClassLoader());
            }
            classes.add(_class);
         }
      }
      return classes;
   }

}
