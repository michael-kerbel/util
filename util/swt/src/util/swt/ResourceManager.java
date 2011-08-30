package util.swt;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.Display;


public class ResourceManager {

   private static HashMap<RGB, Color>               _colorMap            = new HashMap<RGB, Color>();
   private static HashMap<String, Image>            _classImageMap       = new HashMap<String, Image>();
   private static HashMap<Image, Map<Image, Image>> _imageToDecoratorMap = new HashMap<Image, Map<Image, Image>>();
   private static HashMap<String, Font>             _fontMap             = new HashMap<String, Font>();
   private static HashMap<Font, Font>               _fontToBoldFontMap   = new HashMap<Font, Font>();
   private static HashMap<Font, Font>               _fontToItalicFontMap = new HashMap<Font, Font>();
   private static HashMap<Integer, Cursor>          _idToCursorMap       = new HashMap<Integer, Cursor>();

   static {
      Runtime.getRuntime().addShutdownHook(new Thread() {

         @Override
         public void run() {
            dispose();
         }
      });
   }


   public static Image decorateImage( Image baseImage, Image decorator ) {
      HashMap<Image, Image> decoratedMap = (HashMap<Image, Image>)_imageToDecoratorMap.get(baseImage);
      if ( decoratedMap == null ) {
         decoratedMap = new HashMap<Image, Image>();
         _imageToDecoratorMap.put(baseImage, decoratedMap);
      }
      Image result = decoratedMap.get(decorator);
      if ( result == null ) {
         ImageData bid = baseImage.getImageData();
         ImageData did = decorator.getImageData();
         result = new Image(Display.getCurrent(), bid.width, bid.height);
         GC gc = new GC(result);
         //
         gc.drawImage(baseImage, 0, 0);
         gc.drawImage(decorator, bid.width - did.width - 1, bid.height - did.height - 1);
         //
         gc.dispose();
         decoratedMap.put(decorator, result);
      }
      return result;
   }

   /**
    * Dispose of cached objects and their underlying OS resources. This should
    * only be called when the cached objects are no longer needed (e.g. on
    * application shutdown)
    */
   public static void dispose() {
      disposeColors();
      disposeFonts();
      disposeImages();
      disposeCursors();
   }

   public static void disposeColors() {
      for ( Color color : _colorMap.values() ) {
         color.dispose();
      }
      _colorMap.clear();
   }

   public static void disposeCursors() {
      for ( Cursor cursor : _idToCursorMap.values() ) {
         cursor.dispose();
      }
      _idToCursorMap.clear();
   }

   public static void disposeFonts() {
      for ( Font font : _fontMap.values() ) {
         font.dispose();
      }
      for ( Font font : _fontToBoldFontMap.values() ) {
         font.dispose();
      }
      for ( Font font : _fontToItalicFontMap.values() ) {
         font.dispose();
      }
      _fontMap.clear();
      _fontToBoldFontMap.clear();
      _fontToItalicFontMap.clear();
   }

   public static void disposeImages() {
      for ( Image image : _classImageMap.values() ) {
         image.dispose();
      }
      _classImageMap.clear();
   }

   // CoolBar support
   public static void fixCoolBarSize( CoolBar bar ) {
      CoolItem[] items = bar.getItems();
      // ensure that each item has control (at least empty one)
      for ( int i = 0; i < items.length; i++ ) {
         CoolItem item = items[i];
         if ( item.getControl() == null ) {
            item.setControl(new Canvas(bar, SWT.NONE) {

               @Override
               public Point computeSize( int wHint, int hHint, boolean changed ) {
                  return new Point(20, 20);
               }
            });
         }
      }
      // compute size for each item
      for ( int i = 0; i < items.length; i++ ) {
         CoolItem item = items[i];
         Control control = item.getControl();
         control.pack();
         Point size = control.getSize();
         item.setSize(item.computeSize(size.x, size.y));
      }
   }

   public static Font getBoldFont( Font baseFont ) {
      Font font = _fontToBoldFontMap.get(baseFont);
      if ( font == null ) {
         FontData fontDatas[] = baseFont.getFontData();
         FontData data = fontDatas[0];
         font = new Font(Display.getCurrent(), data.getName(), data.getHeight(), SWT.BOLD);
         _fontToBoldFontMap.put(baseFont, font);
      }
      return font;
   }

   public static Color getColor( int systemColorID ) {
      Display display = Display.getCurrent();
      if ( display == null ) {
         display = Display.getDefault();
      }
      return display.getSystemColor(systemColorID);
   }

   public static Color getColor( int r, int g, int b ) {
      return getColor(new RGB(r, g, b));
   }

   public static Color getColor( RGB rgb ) {
      Color color = _colorMap.get(rgb);
      if ( color == null ) {
         Display display = Display.getCurrent();
         color = new Color(display, rgb);
         _colorMap.put(rgb, color);
      }
      return color;
   }

   public static Cursor getCursor( int id ) {
      Integer key = new Integer(id);
      Cursor cursor = _idToCursorMap.get(key);
      if ( cursor == null ) {
         cursor = new Cursor(Display.getDefault(), id);
         _idToCursorMap.put(key, cursor);
      }
      return cursor;
   }

   public static Font getFont( Font font, int height, int style ) {
      return getFont(font.getFontData()[0].getName(), height, style);
   }

   public static Font getFont( String name, int height, int style ) {
      String fullName = name + "|" + height + "|" + style;
      Font font = _fontMap.get(fullName);
      if ( font == null ) {
         font = new Font(Display.getCurrent(), name, height, style);
         _fontMap.put(fullName, font);
      }
      return font;
   }

   public static Image getImage( Class clazz, String path ) {
      String key = clazz.getName() + "|" + path;
      Image image = _classImageMap.get(key);
      if ( image == null ) {
         if ( path.length() > 0 && path.charAt(0) == '/' ) {
            String newPath = path.substring(1, path.length());
            image = getImage(clazz.getClassLoader().getResourceAsStream(newPath));
         } else {
            image = getImage(clazz.getResourceAsStream(path));
         }
         _classImageMap.put(key, image);
      }
      return image;
   }

   public static Image getImage( final int swtImageID ) {
      Display display = Display.getCurrent();
      if ( display == null ) {
         display = Display.getDefault();
      }
      final Display finalDisplay = display;
      final Image[] image = new Image[1];
      display.syncExec(new Runnable() {

         public void run() {
            image[0] = finalDisplay.getSystemImage(swtImageID);
         }
      });

      return image[0];
   }

   public static Image getImage( String path ) {
      String key = ResourceManager.class.getName() + "|" + path;
      Image image = _classImageMap.get(key);
      if ( image == null ) {
         try {
            FileInputStream fis = new FileInputStream(path);
            image = getImage(fis);
            _classImageMap.put(key, image);
            fis.close();
         }
         catch ( Exception e ) {
            return null;
         }
      }
      return image;
   }

   public static Font getItalicFont( Font baseFont ) {
      Font font = _fontToItalicFontMap.get(baseFont);
      if ( font == null ) {
         FontData fontDatas[] = baseFont.getFontData();
         FontData data = fontDatas[0];
         font = new Font(Display.getCurrent(), data.getName(), data.getHeight(), SWT.ITALIC);
         _fontToItalicFontMap.put(baseFont, font);
      }
      return font;
   }

   public static Font getSystemFont() {
      return Display.getCurrent().getSystemFont();
   }

   private static Image getImage( InputStream is ) {
      Display display = Display.getCurrent();
      ImageData data = new ImageData(is);
      if ( data.transparentPixel > 0 ) {
         return new Image(display, data, data.getTransparencyMask());
      }
      return new Image(display, data);
   }

}