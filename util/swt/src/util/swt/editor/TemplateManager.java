package util.swt.editor;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;


public class TemplateManager {

   private static Logger                             _log = Logger.getLogger(TemplateManager.class);

   // cache of Template objects
   private static Map<String, Map<String, Template>> CACHE = new Hashtable<String, Map<String, Template>>();


   public static synchronized Map<String, Template> getTemplates( String extension ) {
      // Check in cache
      Map<String, Template> templates = CACHE.get(extension);
      if ( templates == null ) {
         // Not in cache; load it and put in cache
         templates = loadTemplates(extension);
         if ( templates != null ) CACHE.put(extension, templates);
      }
      return templates;
   }

   private static String getValue( ResourceBundle rb, String key ) {
      try {
         return rb.getString(key);
      }
      catch ( MissingResourceException argh ) {
         return null;
      }
   }

   private static Map<String, Template> loadTemplates( String extension ) {
      Map<String, Template> templates = new HashMap<String, Template>();
      try {
         ResourceBundle rb = ResourceBundle.getBundle(TemplateManager.class.getPackage().getName() + "." + extension + "-templates");
         for ( int i = 1;; i++ ) {
            String handle = getValue(rb, "template.handle." + i);
            if ( handle == null ) break;

            String name = rb.getString("template.name." + i);
            String template = rb.getString("template." + i);

            templates.put(handle, new Template(handle, name, template));
         }

      }
      catch ( MissingResourceException e ) {
         // Ignore
         _log.warn(e.getMessage());
      }
      return templates;
   }
}