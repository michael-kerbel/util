package util.swt;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;


public class Win32OS {

   /** Uses slide animation. By default, roll animation is used. This flag is ignored when used with AW_CENTER. */
   public static final int AW_SLIDE         = 0x00040000;
   /** Activates the window. Do not use this value with AW_HIDE. */
   public static final int AW_ACTIVATE      = 0x00020000;
   /** Uses a fade effect. This flag can be used only if hwnd is a top-level window. */
   public static final int AW_BLEND         = 0x00080000;
   /** Hides the window. By default, the window is shown. */
   public static final int AW_HIDE          = 0x00010000;
   /** Makes the window appear to collapse inward if AW_HIDE is used or expand outward if the AW_HIDE is not used. The various direction flags have no effect. */
   public static final int AW_CENTER        = 0x00000010;
   /** Animates the window from left to right. This flag can be used with roll or slide animation. It is ignored when used with AW_CENTER or AW_BLEND. */
   public static final int AW_HOR_POSITIVE  = 1;
   /** Animates the window from right to left. This flag can be used with roll or slide animation. It is ignored when used with AW_CENTER or AW_BLEND. */
   public static final int AW_HOR_NEGATIVE  = 2;
   /** Animates the window from top to bottom. This flag can be used with roll or slide animation. It is ignored when used with AW_CENTER or AW_BLEND. */
   public static final int AW_VER_POSITIVE  = 4;
   /** Animates the window from bottom to top. This flag can be used with roll or slide animation. It is ignored when used with AW_CENTER or AW_BLEND. */
   public static final int AW_VER_NEGATIVE  = 8;

   public static final int SM_REMOTESESSION = 0x1000;

   private static Class    OS               = null;
   private static Class    Gdip             = null;
   private static Class    POINT            = null;
   private static Class    SIZE             = null;
   private static Class    BLENDFUNCTION    = null;
   private static Class    TCHAR            = null;
   static {
      try {
         OS = Class.forName("org.eclipse.swt.internal.win32.OS");
         Gdip = Class.forName("org.eclipse.swt.internal.gdip.Gdip");
         POINT = Class.forName("org.eclipse.swt.internal.win32.POINT");
         SIZE = Class.forName("org.eclipse.swt.internal.win32.SIZE");
         BLENDFUNCTION = Class.forName("org.eclipse.swt.internal.win32.BLENDFUNCTION");
         TCHAR = Class.forName("org.eclipse.swt.internal.win32.TCHAR");
      }
      catch ( ClassNotFoundException e ) {}
   }

   public static void animateWindow( Shell shell, int time, int flags ) {
      call("AnimateWindow", new Class[] { int.class, int.class, int.class }, shell.handle, time, flags);
   }

   public static Object call( String name, Class[] paramClasses, Object... params ) {
      if ( OS == null ) {
         return null;
      }
      try {
         Method method = OS.getMethod(name, paramClasses);
         return method.invoke(null, params);
      }
      catch ( Exception e ) {
         //         e.printStackTrace();
      }
      return null;
   }

   public static Object callGdip( String name, Class[] paramClasses, Object... params ) {
      if ( Gdip == null ) {
         return null;
      }
      try {
         Method method = Gdip.getMethod(name, paramClasses);
         return method.invoke(null, params);
      }
      catch ( Exception e ) {
         e.printStackTrace();
      }
      return null;
   }

   public static int getField( String name ) {
      if ( OS == null ) {
         return -1;
      }
      try {
         Field field = OS.getField(name);
         return (Integer)field.get(null);

      }
      catch ( Exception e ) {}
      return -1;
   }

   public static boolean isRemoteDesktopSession() {
      return ((Integer)call("GetSystemMetrics", new Class[] { int.class }, SM_REMOTESESSION)) == 1;
   }

   /**
    * To efficiently move the window or set the global alpha value, use subsequent updateLayeredWindow() calls. 
    * @see http://msdn.microsoft.com/library/default.asp?url=/library/en-us/winui/winui/windowsuserinterface/windowing/windows/windowfeatures.asp
    */
   public static void setTransparentImage( Shell shell, String imageFilePath ) {
      try {
         Image image = new Image(Display.getDefault(), ""); // startup gdip
         image.dispose();
      }
      catch ( SWTException e ) {}

      Object tchar = newTCHAR(0, imageFilePath.toCharArray(), true);
      final int gdipHandle = (Integer)callGdip("Bitmap_new", new Class[] { char[].class, boolean.class }, getChars(tchar), false);
      if ( gdipHandle == 0 ) {
         return;
      }
      shell.addListener(SWT.Dispose, new Listener() {

         public void handleEvent( Event event ) {
            callGdip("Bitmap_delete", new Class[] { int.class }, gdipHandle);
         }
      });
      int width = (Integer)callGdip("Image_GetWidth", new Class[] { int.class }, gdipHandle);
      int height = (Integer)callGdip("Image_GetHeight", new Class[] { int.class }, gdipHandle);
      shell.setSize(width, height);
      int[] i = new int[1];
      int color = (Integer)callGdip("Color_new", new Class[] { int.class }, 0);
      callGdip("Bitmap_GetHBITMAP", new Class[] { int.class, int.class, int[].class }, gdipHandle, color, i);
      int imageHandle = i[0];
      if ( imageHandle == 0 ) {
         return;
      }

      int GWL_EXSTYLE = getField("GWL_EXSTYLE");
      int WS_EX_LAYERED = getField("WS_EX_LAYERED");
      int exstyle = (Integer)call("GetWindowLong", new Class[] { int.class, int.class, }, shell.handle, GWL_EXSTYLE);
      // to disable hittesting for this window, add WS_EX_TRANSPARENT
      call("SetWindowLong", new Class[] { int.class, int.class, int.class, }, shell.handle, GWL_EXSTYLE, exstyle | WS_EX_LAYERED);

      int hdcDst = (Integer)call("GetDC", new Class[] { int.class }, 0);
      if ( hdcDst == 0 ) {
         return;
      }
      Object pptDst = newPoint(shell.getLocation());
      Object psize = newSize(width, height);
      int hdcSrc = (Integer)call("CreateCompatibleDC", new Class[] { int.class }, hdcDst);
      if ( hdcSrc == 0 ) {
         return;
      }
      Object pptSrc = newPoint(0, 0);
      int AC_SRC_OVER = getField("AC_SRC_OVER");
      int AC_SRC_ALPHA = getField("AC_SRC_ALPHA");
      Object pblend = newBlendfunction((byte)AC_SRC_OVER, (byte)0, (byte)255, (byte)AC_SRC_ALPHA);
      int ULW_ALPHA = 2;

      int bitmap = (Integer)call("SelectObject", new Class[] { int.class, int.class, }, hdcSrc, imageHandle);
      if ( bitmap == 0 ) {
         return;
      }

      call("UpdateLayeredWindow", new Class[] { int.class, int.class, POINT, SIZE, int.class, POINT, int.class, BLENDFUNCTION, int.class }, shell.handle,
         hdcDst, pptDst, psize, hdcSrc, pptSrc, 0, pblend, ULW_ALPHA);

      // TODO fix resource leaks in error cases 
      call("ReleaseDC", new Class[] { int.class, int.class, }, 0, hdcDst);
      call("SelectObject", new Class[] { int.class, int.class }, hdcSrc, bitmap);
      call("DeleteObject", new Class[] { int.class }, imageHandle);
      call("DeleteDC", new Class[] { int.class, }, hdcSrc);
   }

   /**
    * This method is only for updating existing layeredWindows. To create one, use setTransparentImage()
    * @param globalAlpha new global alpha value for the shell, which will be multiplied with the images alpha 
    */
   public static void updateLayeredWindow( Shell shell, byte globalAlpha ) {
      int AC_SRC_OVER = getField("AC_SRC_OVER");
      int AC_SRC_ALPHA = getField("AC_SRC_ALPHA");
      Object pblend = newBlendfunction((byte)AC_SRC_OVER, (byte)0, globalAlpha, (byte)AC_SRC_ALPHA);
      int ULW_ALPHA = 2;
      call("UpdateLayeredWindow", new Class[] { int.class, int.class, POINT, SIZE, int.class, POINT, int.class, BLENDFUNCTION, int.class }, shell.handle, 0,
         null, null, 0, null, 0, pblend, ULW_ALPHA);
   }

   /**
    * This method is only for updating existing layeredWindows. To create one, use setTransparentImage()
    * @param x new x location of the shell - if negative, will stay unchanged 
    * @param y new y location of the shell - if negative, will stay unchanged 
    */
   public static void updateLayeredWindow( Shell shell, int x, int y ) {
      Object pptDst = null;
      pptDst = newPoint(x, y);
      call("UpdateLayeredWindow", new Class[] { int.class, int.class, POINT, SIZE, int.class, POINT, int.class, BLENDFUNCTION, int.class }, shell.handle, 0,
         pptDst, null, 0, null, 0, null, 0);
   }

   private static char[] getChars( Object tchar ) {
      try {
         return (char[])TCHAR.getField("chars").get(tchar);
      }
      catch ( Exception e ) {}
      return null;
   }

   private static int getImageHandle( Image image ) {
      try {
         return (Integer)image.getClass().getField("handle").get(image);
      }
      catch ( Exception e ) {}
      return 0;
   }

   private static Object newBlendfunction( byte blendOp, byte blendFlags, byte sourceConstantAlpha, byte alphaFormat ) {
      try {
         Object b = BLENDFUNCTION.newInstance();
         Field BlendOp = BLENDFUNCTION.getField("BlendOp");
         BlendOp.setByte(b, blendOp);
         Field BlendFlags = BLENDFUNCTION.getField("BlendFlags");
         BlendFlags.setByte(b, blendFlags);
         Field SourceConstantAlpha = BLENDFUNCTION.getField("SourceConstantAlpha");
         SourceConstantAlpha.setByte(b, sourceConstantAlpha);
         Field AlphaFormat = BLENDFUNCTION.getField("AlphaFormat");
         AlphaFormat.setByte(b, alphaFormat);
         return b;
      }
      catch ( Exception e ) {}
      return null;
   }

   private static Object newPoint( int x, int y ) {
      try {
         Object p = POINT.newInstance();
         Field xf = POINT.getField("x");
         xf.setInt(p, x);
         Field yf = POINT.getField("y");
         yf.setInt(p, y);
         return p;
      }
      catch ( Exception e ) {}
      return null;
   }

   private static Object newPoint( Point p ) {
      return newPoint(p.x, p.y);
   }

   private static Object newSize( int x, int y ) {
      try {
         Object s = SIZE.newInstance();
         Field xf = SIZE.getField("cx");
         xf.setInt(s, x);
         Field yf = SIZE.getField("cy");
         yf.setInt(s, y);
         return s;
      }
      catch ( Exception e ) {}
      return null;
   }

   private static Object newSize( Point p ) {
      return newSize(p.x, p.y);
   }

   private static Object newTCHAR( int i, char[] charArray, boolean b ) {
      try {
         Constructor c = TCHAR.getConstructor(int.class, char[].class, boolean.class);
         Object tchar = c.newInstance(i, charArray, b);
         return tchar;
      }
      catch ( Exception e ) {}
      return null;
   }
}
