package util.dumpass;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;

import util.swt.ResourceManager;


public class Fonts {

   public static final Font CONSOLE_FONT       = getConsoleFont();
   public static final Font CONSOLE_FONT_BOLD  = ResourceManager.getBoldFont(CONSOLE_FONT);
   public static final Font SYSTEM_FONT        = Display.getCurrent().getSystemFont();
   public static final Font SYSTEM_FONT_BOLD   = ResourceManager.getBoldFont(SYSTEM_FONT);
   public static final Font SYSTEM_FONT_ITALIC = ResourceManager.getItalicFont(SYSTEM_FONT);
   public static final Font SYSTEM_FONT_SMALLER = getSystemFontSmaller();

   private static Font getConsoleFont() {
      Display display = Display.getCurrent();
      FontData[] fontDataList = display.getFontList(null, true);
      Font f = null;
      String fontName = "Bitstream Vera Sans Mono";
      for ( FontData fontData : fontDataList ) {
         if ( fontName.equalsIgnoreCase(fontData.getName()) ) {
            f = new Font(display, fontName, 9, SWT.NORMAL);
         }
      }
      if ( f == null ) {
         f = new Font(display, "Courier New", 9, SWT.NORMAL);
      }
      return f;
   }

   private static Font getSystemFontSmaller() {
	   FontData fontData = SYSTEM_FONT.getFontData()[0];
	   int height = fontData.getHeight();
	   return ResourceManager.getFont(fontData.getName(), height-1, SWT.NORMAL);
   }
}
