package util.swt.editor;

import java.util.Hashtable;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import util.swt.editor.SyntaxData.StyleGroup;


/**
 * This class manages the syntax coloring and styling data
 */
class SyntaxManager {

   private static Logger _log = Logger.getLogger(SyntaxManager.class);

   // Lazy cache of SyntaxData objects
   private static Map    data = new Hashtable();


   public static synchronized SyntaxData getSyntaxData( String extension ) {
      // Check in cache
      SyntaxData sd = (SyntaxData)data.get(extension);
      if ( sd == null ) {
         // Not in cache; load it and put in cache
         sd = loadSyntaxData(extension);
         if ( sd != null ) {
            data.put(sd.getExtension(), sd);
         }
      }
      return sd;
   }

   private static String getValue( ResourceBundle rb, String key ) {
      try {
         return rb.getString(key);
      }
      catch ( MissingResourceException argh ) {
         return null;
      }
   }

   private static SyntaxData loadSyntaxData( String extension ) {
      SyntaxData sd = null;
      try {
         ResourceBundle rb = ResourceBundle.getBundle(SyntaxManager.class.getPackage().getName() + "." + extension + "-syntax");
         sd = new SyntaxData(extension);
         sd.setMultiLineCommentStart(rb.getString("multilinecommentstart"));
         sd.setMultiLineCommentEnd(rb.getString("multilinecommentend"));
         if ( rb.containsKey("bracesstart") ) {
            sd.setBracesStart(rb.getString("bracesstart"));
         }
         if ( rb.containsKey("bracesend") ) {
            sd.setBracesEnd(rb.getString("bracesend"));
         }

         for ( int i = 1;; i++ ) {
            String regex = getValue(rb, "regex." + i);
            if ( regex == null ) {
               break;
            }

            String foreground = getValue(rb, "foreground." + i);

            String background = getValue(rb, "background." + i);

            String style = getValue(rb, "style." + i);
            style = style == null ? "0" : style;

            StyleGroup styleGroup = new StyleGroup(regex, foreground, background, style);

            String underline = getValue(rb, "underline." + i);
            if ( underline != null && Boolean.parseBoolean(underline) ) {
               styleGroup._underline = true;

               String underlineColor = rb.getString("underline.color." + i);
               underlineColor = underlineColor == null ? "000000" : underlineColor;
               styleGroup._underlineColor = styleGroup.getColor(underlineColor);

               String underlineStyle = rb.getString("underline.style." + i);
               underlineStyle = underlineStyle == null ? "0" : underlineStyle;
               styleGroup._underlineStyle = Integer.parseInt(underlineStyle);
            }

            sd.addStyleGroup(styleGroup);
         }

      }
      catch ( MissingResourceException e ) {
         // Ignore
         _log.warn(e.getMessage());
      }
      return sd;
   }
}