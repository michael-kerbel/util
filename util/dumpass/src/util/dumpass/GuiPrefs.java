package util.dumpass;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.AbstractFileConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.MapConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.log4j.Logger;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;

import util.dumpass.GUI.BeanMetaData;
import util.string.StringTool;


public class GuiPrefs {

   public static final String GUI_WIDTH             = "window.width";
   public static final String GUI_HEIGHT            = "window.height";
   public static final String GUI_X                 = "window.x";
   public static final String GUI_Y                 = "window.y";
   public static final String GUI_MAXIMIZED         = "window.maximized";
   public static final String CLASSNAME             = "classname";
   public static final String DUMPFILENAME          = "dumpfilename";
   public static final String CLASSPATH_ELEMENT     = "classpath.element";
   public static final String CONFIRM_DELETES       = "confirm.deletes";
   public static final String ALLOW_EDITING         = "allow.edit";
   public static final String BEAN_LAST_DUMP        = ".bean.lastdump";
   public static final String DUMP_LAST_BEAN        = ".dump.lastbean";
   public static final String BEAN_COL_WIDTHS       = ".bean.colwidths";
   public static final String BEAN_COL_ORDER        = ".bean.colorder";
   public static final String CLEANUP_CREATED_FILES = "cleanup.created.files";

   AbstractConfiguration      _preferences;
   Logger                     _log                  = Logger.getLogger(getClass());
   GUI                        _gui;
   char                       _listDelimiter        = ',';
   Pattern                    _escaper              = Pattern.compile(Pattern.quote("" + _listDelimiter));


   public GuiPrefs( GUI gui ) {
      _gui = gui;

      File file = new File(System.getProperty("user.home"), ".dumpass");
      try {
         _preferences = new PropertiesConfiguration();
         //_preferences.setListDelimiter('\u0000');
         ((PropertiesConfiguration)_preferences).setFile(file);
         ((PropertiesConfiguration)_preferences).load();
      }
      catch ( Exception e ) {
         _log.warn("Failed to load preferences, creating new ones.", e);
         if ( file.exists() ) {
            boolean success = file.delete();
            if ( !success ) {
               _log.warn("Failed to create new preferences. Continueing without persistent prefs.", e);
               _preferences = new MapConfiguration(new HashMap());
            }
         }
         try {
            _preferences = new PropertiesConfiguration(file);
         }
         catch ( ConfigurationException e1 ) {
            _log.warn("Failed to create new preferences. Continueing without persistent prefs.", e);
            _preferences = new MapConfiguration(new HashMap());
         }
      }

   }

   public void addClassName( String className ) {
      addToLRUStringArray(CLASSNAME, className);
   }

   public void addClasspathElement( String classpathElement ) {
      addToLRUStringArray(CLASSPATH_ELEMENT, classpathElement);
   }

   public void addDumpFileName( String fileName ) {
      addToLRUStringArray(DUMPFILENAME, fileName);
   }

   public void flush() {
      try {
         if ( _preferences instanceof AbstractFileConfiguration ) {
            ((AbstractFileConfiguration)_preferences).save();
         }
      }
      catch ( Exception e ) {
         _log.error("Failed to store preferences.", e);
      }
   }

   public int[] getBeanColumnOrder() {
      String colWidths = _preferences.getString(_gui._class.getName() + BEAN_COL_ORDER);
      if ( colWidths == null ) {
         return null;
      }
      String[] s = StringTool.split(colWidths, '|');
      TIntList c = new TIntArrayList();
      for ( String ss : s ) {
         if ( ss.length() > 0 ) {
            c.add(Integer.parseInt(ss));
         }
      }
      return c.toArray();
   }

   public int[] getBeanColumnWidth() {
      String colWidths = _preferences.getString(_gui._class.getName() + BEAN_COL_WIDTHS);
      if ( colWidths == null ) {
         return null;
      }
      String[] s = StringTool.split(colWidths, '|');
      TIntList c = new TIntArrayList();
      for ( String ss : s ) {
         if ( ss.length() > 0 ) {
            c.add(Integer.parseInt(ss));
         }
      }
      return c.toArray();
   }

   public String getBeanForDump( String path ) {
      return _preferences.getString(path.replaceAll("[:\\\\]", "") + DUMP_LAST_BEAN);
   }

   public String[] getClassNames() {
      return _preferences.getStringArray(CLASSNAME);
   }

   public String[] getClasspathElements() {
      return _preferences.getStringArray(CLASSPATH_ELEMENT);
   }

   public String[] getDumpFileNames() {
      String[] dumpFileNames = _preferences.getStringArray(DUMPFILENAME);
      List<String> filteredFileNames = new ArrayList<String>();
      for ( String fn : dumpFileNames ) {
         if ( new File(fn).exists() ) {
            filteredFileNames.add(fn);
         }
      }
      if ( filteredFileNames.size() != dumpFileNames.length ) {
         dumpFileNames = filteredFileNames.toArray(new String[filteredFileNames.size()]);
         _preferences.setProperty(DUMPFILENAME, escape(dumpFileNames));
         flush();
      }
      return dumpFileNames;
   }

   public String getDumpForBean( String className ) {
      return _preferences.getString(className + BEAN_LAST_DUMP);
   }

   public Point getLastGuiLocation() {
      int x = _preferences.getInt(GUI_X, 0);
      int y = _preferences.getInt(GUI_Y, 0);
      return new Point(x, y);
   }

   public Point getLastGuiSize() {
      int width = _preferences.getInt(GUI_WIDTH, 1024);
      int height = _preferences.getInt(GUI_HEIGHT, 768);
      return new Point(width, height);
   }

   public boolean isAllowEditing() {
      return _preferences.getBoolean(ALLOW_EDITING, true);
   }

   public boolean isCleanupCreatedFiles() {
      return _preferences.getBoolean(CLEANUP_CREATED_FILES, true);
   }

   public boolean isConfirmDeletes() {
      return _preferences.getBoolean(CONFIRM_DELETES, true);
   }

   public boolean isGuiMaximized() {
      return _preferences.getBoolean(GUI_MAXIMIZED, false);
   }

   public void removeClassName( String classname ) {
      removeFromStringArray(CLASSNAME, classname);
   }

   public void removeClasspathElement( String classpathElement ) {
      removeFromStringArray(CLASSPATH_ELEMENT, classpathElement);
   }

   public void removeDumpFileName( String filename ) {
      removeFromStringArray(DUMPFILENAME, filename);
   }

   public void saveBeanMetaData( BeanMetaData metaData ) {
      if ( metaData != null ) {
         saveBeanColumnOrder(metaData);
         saveBeanColumnWidth(metaData);
         saveBeanForDump(metaData);
         saveDumpForBean(metaData);
      }
   }

   public void setCleanupCreatedFiles( boolean cleanupCreatedFiles ) {
      _preferences.setProperty(CLEANUP_CREATED_FILES, cleanupCreatedFiles);
   }

   protected void storePreferences() {
      Shell shell = _gui.getShell();
      if ( shell != null && !shell.isDisposed() ) {
         Rectangle bounds = shell.getBounds();
         _preferences.setProperty(GUI_WIDTH, bounds.width);
         _preferences.setProperty(GUI_HEIGHT, bounds.height);
         _preferences.setProperty(GUI_X, bounds.x);
         _preferences.setProperty(GUI_Y, bounds.y);
         _preferences.setProperty(GUI_MAXIMIZED, shell.getMaximized());

         _preferences.setProperty(ALLOW_EDITING, _gui._allowEditing.getSelection());
         _preferences.setProperty(CONFIRM_DELETES, _gui._confirmDeletes.getSelection());
         _preferences.setProperty(CLEANUP_CREATED_FILES, _gui._cleanupCreatedFiles.getSelection());

         saveBeanMetaData(_gui._beanMetaData);
      }
      flush();
   }

   private void addToLRUStringArray( String prefix, String element ) {
      String[] elements = _preferences.getStringArray(prefix);
      List<String> resortedElements = new ArrayList<String>();
      resortedElements.add(element);
      for ( String cn : elements ) {
         if ( cn.equals(element) ) {
            continue;
         }
         resortedElements.add(cn);
      }
      _preferences.setProperty(prefix, escape(resortedElements.toArray(new String[resortedElements.size()])));
      flush();
   }

   private String escape( String value ) {
      return _escaper.matcher(value).replaceAll("\\" + _listDelimiter);
   }

   private String[] escape( String[] values ) {
      for ( int i = 0, length = values.length; i < length; i++ ) {
         values[i] = escape(values[i]);
      }
      return values;
   }

   private void removeFromStringArray( String prefix, String elementToRemove ) {
      String[] oldElements = _preferences.getStringArray(prefix);

      List<String> newElements = new ArrayList<String>();
      for ( int j = 0, length = oldElements.length; j < length; j++ ) {
         String e = oldElements[j];
         if ( !e.equals(elementToRemove) ) {
            newElements.add(e);
         }
      }
      _preferences.setProperty(prefix, newElements.toArray(new String[newElements.size()]));
      flush();
   }

   private void saveBeanColumnOrder( BeanMetaData metaData ) {
      StringBuilder sb = new StringBuilder();
      for ( int c : _gui._table.getColumnOrder() ) {
         sb.append(c).append('|');
      }
      _preferences.setProperty(metaData._class.getName() + BEAN_COL_ORDER, escape(sb.toString()));
   }

   private void saveBeanColumnWidth( BeanMetaData metaData ) {
      StringBuilder sb = new StringBuilder();
      for ( TableColumn c : _gui._table.getColumns() ) {
         sb.append(c.getWidth()).append('|');
      }
      _preferences.setProperty(metaData._class.getName() + BEAN_COL_WIDTHS, escape(sb.toString()));
   }

   private void saveBeanForDump( BeanMetaData metaData ) {
      _preferences.setProperty(metaData._dumpFile.getAbsolutePath().replaceAll("[:\\\\]", "") + DUMP_LAST_BEAN, metaData._class.getName());
   }

   private void saveDumpForBean( BeanMetaData metaData ) {
      _preferences.setProperty(metaData._class.getName() + BEAN_LAST_DUMP, escape(metaData._dumpFile.getAbsolutePath()));
   }
}
