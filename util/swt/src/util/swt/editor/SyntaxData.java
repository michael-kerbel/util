package util.swt.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.swt.graphics.Color;

import util.swt.ResourceManager;


/**
 * This class contains information for syntax coloring and styling for a file extension
 */
class SyntaxData {

   private String           _extension;

   private Pattern          _multiLineCommentStart;
   private Pattern          _multiLineCommentEnd;

   private String           _bracesStart = "";
   private String           _bracesEnd   = "";

   private List<StyleGroup> _styleGroups = new ArrayList<StyleGroup>();


   public SyntaxData( String extension ) {
      this._extension = extension;
   }

   public void addStyleGroup( StyleGroup styleGroup ) {
      _styleGroups.add(styleGroup);
   }

   public String getBracesEnd() {
      return _bracesEnd;
   }

   public String getBracesStart() {
      return _bracesStart;
   }

   public String getExtension() {
      return _extension;
   }

   public Pattern getMultiLineCommentEnd() {
      return _multiLineCommentEnd;
   }

   public Pattern getMultiLineCommentStart() {
      return _multiLineCommentStart;
   }

   public List<StyleGroup> getStyleGroups() {
      return _styleGroups;
   }

   public void setBracesEnd( String bracesEnd ) {
      _bracesEnd = bracesEnd;
   }

   public void setBracesStart( String bracesStart ) {
      _bracesStart = bracesStart;
   }

   public void setMultiLineCommentEnd( String multiLineCommentEnd ) {
      this._multiLineCommentEnd = Pattern.compile(multiLineCommentEnd);
   }

   public void setMultiLineCommentStart( String multiLineCommentStart ) {
      this._multiLineCommentStart = Pattern.compile(multiLineCommentStart);
   }


   public static class StyleGroup {

      public final Pattern _regEx;
      public final Color   _foreground;
      public final Color   _background;
      public final int     _style;
      public boolean       _underline = false;
      public Color         _underlineColor;
      public int           _underlineStyle;


      public StyleGroup( String regEx, String foreground, String background, String style ) {
         _regEx = Pattern.compile(regEx);
         _foreground = getColor(foreground);
         _background = getColor(background);
         _style = Integer.parseInt(style);
      }

      Color getColor( String color ) {
         if ( color == null ) {
            return null;
         }
         String rgb = color.trim();
         if ( rgb.length() > 6 ) {
            throw new IllegalArgumentException("color " + color + " is invalid");
         }
         int r = Integer.parseInt(rgb.substring(0, 2), 16);
         int g = Integer.parseInt(rgb.substring(2, 4), 16);
         int b = Integer.parseInt(rgb.substring(4, 6), 16);
         return ResourceManager.getColor(r, g, b);
      }

   }

}
