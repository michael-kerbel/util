package util.swt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.core.DefaultNamingPolicy;
import net.sf.cglib.core.NamingPolicy;
import net.sf.cglib.core.Predicate;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.Factory;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.objenesis.ObjenesisStd;

import util.reflection.Reflection;


public class SWTUtils {

   public static final boolean                           IS_PLATFORM_WIN32                = SWT.getPlatform().equals("win32");
   public static final boolean                           IS_PLATFORM_GTK                  = SWT.getPlatform().equals("gtk");
   public static final boolean                           IS_PLATFORM_WPF                  = SWT.getPlatform().equals("wpf");
   public static final boolean                           IS_PLATFORM_CARBON               = SWT.getPlatform().equals("carbon");
   public static final boolean                           IS_PLATFORM_MOTIF                = SWT.getPlatform().equals("motif");
   public static final boolean                           IS_PLATFORM_PHOTON               = SWT.getPlatform().equals("photon");

   protected static Image                                IMAGE_ERROR;

   protected static Logger _log = LoggerFactory.getLogger(SWTUtils.class);
   private static final float                            RGB_VALUE_MULTIPLIER             = 0.6f;

   private static final ThreadLocal<Method>              LAST_PREPARED_METHOD             = new ThreadLocal<Method>();
   private static final ThreadLocal<Object[]>            LAST_PREPARED_METHOD_ARGS        = new ThreadLocal<Object[]>();
   private static final ThreadLocal<Object>              LAST_PREPARED_CALLEE             = new ThreadLocal<Object>();
   private static final ThreadLocal<StackTraceElement[]> LAST_PREPARED_CALLSTACK          = new ThreadLocal<StackTraceElement[]>();
   private static final ThreadLocal<Preparation>         LAST_PREPARATION                 = new ThreadLocal<Preparation>();

   private static final Map<Class, Class>                PROXY_CACHE                      = new HashMap<Class, Class>();

   private static Object[]                               EMPTY_PARAMETER                  = new Object[0];
   private static Class[]                                EVENT_SIGNATURE                  = { Event.class };

   private static final NamingPolicy                     NAMING_POLICY_FOR_SIGNED_CLASSES = new DefaultNamingPolicy() {

                                                                                             @Override
                                                                                             public String getClassName( String prefix, String source,
                                                                                                   Object key, Predicate names ) {
                                                                                                // by moving the generated class to a different package, 
                                                                                                // we are not in the same package as the signed class
                                                                                                // -> we don't need the same signer information
                                                                                                return "codegen."
                                                                                                   + super.getClassName(prefix, source, key, names);
                                                                                             }
                                                                                          };


   /**
    * Fluent API version of {@link #asyncExecPrepared(Display)}. 
    * 
    * A way of executing a method of <code>callee</code> in the UI thread without the need
    * of a anonymous inner class for the <code>Runnable</code> argument of {@link Display#asyncExec(Runnable)}.<p/>
    * 
    * Using this API you can write refactoring and IDE friendly code like this:
    * 
    * <code><pre>
    *   ...
    *   SWTUtils.asyncExec(display).on(this.getClass(), this).runInUIThread(123);
    * }
    * 
    * protected void runInUIThread(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use 
    * {@link #asyncExec(Display, Object, String, Object...)} or 
    * {@link #asyncExec(Display, Object, String, Class[], Object...)}.
    */
   public static Preparation asyncExec( Display display ) {
      return new AsyncExecPreparation(display);
   }

   public static void asyncExec( Display display, final Object callee, final String methodName, Class[] argumentClasses, final Object... additionalArguments ) {
      exec(display, true, callee, methodName, argumentClasses, additionalArguments);
   }

   public static void asyncExec( Display display, final Object callee, final String methodName, final Object... additionalArguments ) {
      exec(display, true, callee, methodName, additionalArguments);
   }

   /**
    * A way of executing a method of <code>callee</code> in the UI thread without the need
    * of a anonymous inner class for the <code>Runnable</code> argument of {@link Display#asyncExec(Runnable)}.<p/>
    * 
    * Using this method in combination with {@link #prepareCallOn(Class, Object)} you can write refactoring 
    * and IDE friendly code like this:
    * 
    * <code><pre>
    *   ...
    *   SWTUtils.prepareCallOn(this.getClass(), this).runInUIThread(123);
    *   SWTUtils.asyncExecPrepared(display);
    * }
    * 
    * protected void runInUIThread(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    *
    * Don't separate the call to {@link SWTUtils#prepareCallOn(Class)}
    * and this method. You can use the example above as pattern.<p/>
    * @see #asyncExec(Display) asyncExec(Display) fluent version of this method - to be prefered
    * @see #prepareCallOn(Class)
    */
   public static void asyncExecPrepared( Display display ) {
      Method method = getLastPreparedMethod();
      Object[] additionalArguments = getLastPreparedMethodArguments();
      Object callee = getLastPreparedCallee();
      display.asyncExec(new ExecRunnable(method, callee, additionalArguments));
   }

   /**
    * @param originalImageData The ImageData to be average blurred.
    * Transparency information will be ignored.
    * @param radius the number of radius pixels to consider when blurring image.
    * @return A blurred copy of the image _data, or null if an error occured.
    */
   public static ImageData blur( ImageData originalImageData, int radius ) {
      /*
       * This method will vertically blur all the pixels in a row at once.
       * This blurring is performed incrementally to each row.
       * 
       * In order to vertically blur any given pixel, maximally (radius * 2 + 1)
       * pixels must be examined. Since each of these pixels exists in the same column,
       * they span across a series of consecutive rows. These rows are horizontally
       * blurred before being cached and used as input for the vertical blur.
       * Blurring a pixel horizontally and then vertically is equivalent to blurring
       * the pixel with both its horizontal and vertical neighbours at once.
       * 
       * Pixels are blurred under the notion of a 'summing scope'. A certain scope
       * of pixels in a column are summed then averaged to determine a target pixel's
       * resulting RGB value. When the next lower target pixel is being calculated,
       * the topmost pixel is removed from the summing scope (by subtracting its RGB) and
       * a new pixel is added to the bottom of the scope (by adding its RGB).
       * In this sense, the summing scope is moving downward.
       */
      if ( radius < 1 ) {
         return originalImageData;
      }
      // prepare new image _data with 24-bit direct palette to hold blurred copy of image
      ImageData newImageData = new ImageData(originalImageData.width, originalImageData.height, 24, new PaletteData(0xFF, 0xFF00, 0xFF0000));
      if ( radius >= newImageData.height || radius >= newImageData.width ) {
         radius = Math.min(newImageData.height, newImageData.width) - 1;
      }
      // initialize cache
      ArrayList rowCache = new ArrayList();
      int cacheSize = radius * 2 + 1 > newImageData.height ? newImageData.height : radius * 2 + 1; // number of rows of imageData we cache
      int cacheStartIndex = 0; // which row of imageData the cache begins with
      for ( int row = 0; row < cacheSize; row++ ) {
         // row _data is horizontally blurred before caching
         rowCache.add(rowCache.size(), blurRow(originalImageData, row, radius));
      }
      // sum red, green, and blue values separately for averaging
      RGB[] rowRGBSums = new RGB[newImageData.width];
      int[] rowRGBAverages = new int[newImageData.width];
      int topSumBoundary = 0; // current top row of summed values scope
      int targetRow = 0; // row with RGB averages to be determined
      int bottomSumBoundary = 0; // current bottom row of summed values scope
      int numRows = 0; // number of rows included in current summing scope
      for ( int i = 0; i < newImageData.width; i++ ) {
         rowRGBSums[i] = new RGB(0, 0, 0);
      }
      while ( targetRow < newImageData.height ) {
         if ( bottomSumBoundary < newImageData.height ) {
            do {
               // sum pixel RGB values for each column in our radius scope
               for ( int col = 0; col < newImageData.width; col++ ) {
                  rowRGBSums[col].red += ((RGB[])rowCache.get(bottomSumBoundary - cacheStartIndex))[col].red;
                  rowRGBSums[col].green += ((RGB[])rowCache.get(bottomSumBoundary - cacheStartIndex))[col].green;
                  rowRGBSums[col].blue += ((RGB[])rowCache.get(bottomSumBoundary - cacheStartIndex))[col].blue;
               }
               numRows++;
               bottomSumBoundary++; // move bottom scope boundary lower
               if ( bottomSumBoundary < newImageData.height && (bottomSumBoundary - cacheStartIndex) > (radius * 2) ) {
                  // grow cache
                  rowCache.add(rowCache.size(), blurRow(originalImageData, bottomSumBoundary, radius));
               }
            }
            while ( bottomSumBoundary <= radius ); // to initialize rowRGBSums at start
         }
         if ( (targetRow - topSumBoundary) > (radius) ) {
            // subtract values of top row from sums as scope of summed values moves down
            for ( int col = 0; col < newImageData.width; col++ ) {
               rowRGBSums[col].red -= ((RGB[])rowCache.get(topSumBoundary - cacheStartIndex))[col].red;
               rowRGBSums[col].green -= ((RGB[])rowCache.get(topSumBoundary - cacheStartIndex))[col].green;
               rowRGBSums[col].blue -= ((RGB[])rowCache.get(topSumBoundary - cacheStartIndex))[col].blue;
            }
            numRows--;
            topSumBoundary++; // move top scope boundary lower
            rowCache.remove(0); // remove top row which is out of summing scope
            cacheStartIndex++;
         }
         // calculate each column's RGB-averaged pixel
         for ( int col = 0; col < newImageData.width; col++ ) {
            rowRGBAverages[col] = newImageData.palette.getPixel(new RGB(rowRGBSums[col].red / numRows, rowRGBSums[col].green / numRows, rowRGBSums[col].blue
               / numRows));
         }
         // replace original pixels
         newImageData.setPixels(0, targetRow, newImageData.width, rowRGBAverages, 0);
         targetRow++;
      }
      return newImageData;
   }

   public static Color darker( Color color ) {
      return ResourceManager.getColor((int)(color.getRed() * RGB_VALUE_MULTIPLIER), (int)(color.getGreen() * RGB_VALUE_MULTIPLIER),
         (int)(color.getBlue() * RGB_VALUE_MULTIPLIER));
   }

   /**
    * Findet zu einem Punkt (typischerweise aus den Koordinaten eines Events generiert) und einem TabellenIndex den dazugehörigen TableItem.
    * Man kann hiermit nur auf eine bestimmte Spalte prüfen. Diese Methode funktioniert für alle Table Instanzen, sie müssen nicht SWT.FULL_SELECTION
    * gesetzt haben. 
    */
   public static TableItem getItem( Table table, Point pt, int columnIndex ) {
      Rectangle clientArea = table.getClientArea();
      int index = table.getTopIndex();
      while ( index < table.getItemCount() ) {
         boolean visible = false;
         TableItem item = table.getItem(index);
         Rectangle rect = item.getBounds(columnIndex);
         if ( rect.contains(pt) ) {
            return item;
         }
         if ( !visible && rect.intersects(clientArea) ) {
            visible = true;
         }
         if ( !visible ) {
            return null;
         }
         index++;
      }
      return null;
   }

   public static Object getLastPreparedCallee() {
      Object object = LAST_PREPARED_CALLEE.get();
      LAST_PREPARED_CALLEE.remove();
      return object;
   }

   public static Method getLastPreparedMethod() {
      Object callee = LAST_PREPARED_CALLEE.get();
      Method method = LAST_PREPARED_METHOD.get();
      LAST_PREPARED_METHOD.remove();
      if ( method == null ) {
         throw new IllegalStateException("no preceeding call to the returned proxy of SWTUtils.prepareCallOn(.). Maybe the called method was private? "
            + getLastPreparedCallstack() + "exception stacktrace:");
      }
      if ( !method.getDeclaringClass().isInstance(callee) ) {
         throw new IllegalStateException("no preceeding call to the returned proxy of SWTUtils.prepareCallOn(.). " + method.getDeclaringClass()
            + " not instanceof " + callee.getClass() + ". " + getLastPreparedCallstack() + "exception stacktrace:");
      }
      return method;
   }

   public static Object[] getLastPreparedMethodArguments() {
      Object[] args = LAST_PREPARED_METHOD_ARGS.get();
      LAST_PREPARED_METHOD_ARGS.remove();
      return args;
   }

   public static boolean isRemoteDesktopSession() {
      if ( IS_PLATFORM_WIN32 ) {
         return Win32OS.isRemoteDesktopSession();
      }
      return false;
   }

   public static Color lighter( Color rgb ) {
      int r = rgb.getRed(), g = rgb.getGreen(), b = rgb.getBlue();

      return ResourceManager.getColor(Math.max(2, Math.min((int)(r / RGB_VALUE_MULTIPLIER), 255)), Math.max(2, Math.min((int)(g / RGB_VALUE_MULTIPLIER), 255)),
         Math.max(2, Math.min((int)(b / RGB_VALUE_MULTIPLIER), 255)));
   }

   public static Color mixColors( Color c1, Color c2 ) {
      return ResourceManager.getColor((c1.getRed() + c2.getRed()) / 2, (c1.getGreen() + c2.getGreen()) / 2, (c1.getBlue() + c2.getBlue()) / 2);
   }

   public static Color mixColors( Color c1, Color c2, double weight ) {
      return ResourceManager.getColor((int)(c1.getRed() * weight + c2.getRed() * (1 - weight)), (int)(c1.getGreen() * weight + c2.getGreen() * (1 - weight)),
         (int)(c1.getBlue() * weight + c2.getBlue() * (1 - weight)));
   }

   /**
    * Creates a proxy of the given type and records method calls to it. In order to use 
    * this properly you will have to call a method on the returned proxy. The parameters
    * you pass during this call will also be remembered. When calling a utility method
    * using this mechanism (see links below), it will forward actions to the method you 
    * called on the proxy to <code>callee</code> later at some point.<p/>
    * 
    * This solves several problems: You don't need ugly anonymous inner classes
    * for Listeners (see {@link OnEvent}), progress bars or when executing something in 
    * the UI thread. While this API induces its own ugliness (two seperated calls 
    * depending on each other, cglib dependency) it enables cleaner code and stays IDE
    * friendly as well as refactorable.<p/>
    * 
    * For examples see the list of methods below.<p/> 
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use the other
    * versions of your API call, like {@link #asyncExec(Display, Object, String, Object...)}
    * or {@link #runWithDelayedProgressBar(IRunnableContext, String, long, Object, Method, Object...)}. 
    * 
    * @see {@link OnEvent#OnEvent()} constructor
    * @see {@link OnSelect#OnSelect()} constructor
    * @see {@link OnEdit#OnEdit()} constructor
    * @see #asyncExecPrepared(Display)
    * @see #syncExecPrepared(Display)
    * @see #runPreparedWithDelayedProgressBar(IRunnableContext, String, long)
    * @see #runPreparedWithProgressBar(IRunnableContext, boolean, boolean)
    */
   public static <T> T prepareCallOn( Class<T> target, Object callee ) {
      if ( callee == null ) {
         throw new IllegalArgumentException("parameter 'callee' may not be null.");
      }
      if ( LAST_PREPARED_CALLEE.get() != null ) {
         throw new IllegalStateException(
            "Two consecutive calls to prepareCallOn without using the prepared method. This is most probably a bug in the usage of prepareCallOn.");
      }
      LAST_PREPARED_CALLEE.set(callee);

      if ( LAST_PREPARED_METHOD.get() != null ) {
         throw new IllegalStateException(
            "Two consecutive calls to prepareCallOn without using the prepared method. This is most probably a bug in the usage of prepareCallOn.");
      }

      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      LAST_PREPARED_CALLSTACK.set(stackTrace);

      try {
         Class c = createProxy(target);
         Factory proxy = (Factory)new ObjenesisStd(true).getInstantiatorOf(c).newInstance();
         proxy.setCallbacks(new Callback[] { new MethodInterceptorImplementation() });
         return target.cast(proxy);
      }
      catch ( CodeGenerationException e ) {
         if ( Modifier.isPrivate(target.getModifiers()) ) {
            throw new IllegalArgumentException("SWTUtils cannot proxy this class: " + target + ". Most likely it is a private class that is not visible.");
         }
         throw new IllegalArgumentException("SWTUtils cannot proxy this class: " + target + ". SWTUtils can only proxy visible & non-final classes");
      }
   }

   /**
    * Runs a method of <code>callee</code>. If it takes longer than <code>delay</code> millis, 
    * a progress bar is shown, displaying <code>message</code>.<p/>
    * The method to run is determined by calling {@link #prepareCallOn(Class, Object)} before 
    * this method. This is IDE and refactoring friendly, but unfortunately forces you to use 
    * two API calls like this:
    * <code><pre>
    *   ...
    *   SWTUtils.prepareCallOn(this.getClass(), this).runSomethingPotentiallySlow(123);
    *   SWTUtils.runPreparedWithDelayedProgressBar(window, "calculating...", 500);
    * }
    * 
    * protected void runSomethingPotentiallySlow(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    * 
    * Don't separate the call to {@link SWTUtils#prepareCallOn(Class)}
    * and this method. You can use the example above as pattern.<p/>
    * @see #prepareCallOn(Class)
    */
   public static void runPreparedWithDelayedProgressBar( IRunnableContext context, String message, long delay ) {
      Method method = getLastPreparedMethod();
      Object[] additionalArguments = getLastPreparedMethodArguments();
      Object callee = getLastPreparedCallee();
      runWithDelayedProgressBar(context, message, delay, callee, method, additionalArguments);
   }

   /**
    * Runs a method of <code>callee</code> while the <code>context</code> is showing a progress 
    * bar during execution.<p/>
    * The method to run is determined by calling {@link #prepareCallOn(Class, Object)} before 
    * this method. This is IDE and refactoring friendly, but unfortunately forces you to use 
    * two API calls like this:
    * <code><pre>
    *   ...
    *   SWTUtils.prepareCallOn(this.getClass(), this).runWithProgressBar(null, 123);
    *   SWTUtils.runPreparedWithProgressBar(window, true, false);
    * }
    * 
    * protected void runWithProgressBar(IProgressMonitor mon, int arbitraryArgument){
    *   mon.beginTask("Working...", IProgressMonitor.UNKNOWN);
    *   // do something taking long
    *   mon.done();
    * }
    * </pre></code>
    * 
    * Don't separate the call to {@link SWTUtils#prepareCallOn(Class)}
    * and this method. You can use the example above as pattern.<p/>
    * 
    * You must prepare a method having <code>IProgressMonitor</code> as first parameter type.  
    * @see #prepareCallOn(Class)
    */
   public static void runPreparedWithProgressBar( IRunnableContext context, boolean fork, boolean cancelable ) {
      Method method = getLastPreparedMethod();
      if ( method.getParameterTypes().length == 0 || method.getParameterTypes()[0].isInstance(IProgressMonitor.class) ) {
         throw new IllegalArgumentException("Last prepared method " + method
            + " does not have IProgressMonitor as first parameter. This is needed in order to use SWTUtils.runPreparedWithProgressBar(.).");
      }

      Object[] additionalArguments = getLastPreparedMethodArguments();
      Object[] args = new Object[additionalArguments.length - 1];
      System.arraycopy(additionalArguments, 1, args, 0, args.length);

      Object callee = getLastPreparedCallee();

      runWithProgressBar(context, fork, cancelable, callee, method, args);
   }

   /**
    * Fluent API version of {@link #runPreparedWithDelayedProgressBar(IRunnableContext, String, long)}. 
    * 
    * Runs a method of <code>callee</code>. If it takes longer than <code>delay</code> millis, 
    * a progress bar is shown, displaying <code>message</code>.<p/>
    * The method to run is determined like this:
    * <code><pre>
    *   ...
    *   SWTUtils.runWithDelayedProgressBar(window, "calculating...", 500).on(this.getClass(), this).runSomethingPotentiallySlow(123);
    * }
    * 
    * protected void runSomethingPotentiallySlow(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use 
    * {@link #runWithDelayedProgressBar(IRunnableContext, String, long, Object, String, Object...) runWithDelayedProgressBar} or 
    * {@link #runWithDelayedProgressBar(IRunnableContext, String, long, Object, String, Class[], Object...) runWithDelayedProgressBar}.
    * @see #runWithDelayedProgressBar(IRunnableContext, String, long) runWithDelayedProgressBar(IRunnableContext, String, long) fluent version of this method - to be prefered
    * @see #prepareCallOn(Class)
    */
   public static Preparation runWithDelayedProgressBar( IRunnableContext context, String message, long delay ) {
      return new DelayedProgressBarPreparation(context, message, delay);
   }

   /**
    * Führt eine Methode des callee aus, und zeigt eine ProgressBar an, falls sie nach <code>delay</code> Millisekunden nicht beendet ist.
    * Die Methode muss die Signatur <code>methodName([Klassen aus additionalArgumentClasses])</code> haben.
    */
   public static void runWithDelayedProgressBar( IRunnableContext context, final String message, long delay, final Object callee, final String methodName,
         Class[] additionalArgumentClasses, final Object... additionalArguments ) {

      if ( callee == null ) {
         _log.error("callee is null", new IllegalArgumentException());
         return;
      }

      final Method method;
      try {
         method = Reflection.getMethod(callee.getClass(), methodName, additionalArgumentClasses);
      }
      catch ( Exception e ) {
         _log.error("No method " + methodName + " with proper signature in class " + callee.getClass() + ".", e);
         return;
      }

      runWithDelayedProgressBar(context, message, delay, callee, method, additionalArguments);
   }

   /**
    * Führt eine Methode des callee aus, und zeigt eine ProgressBar an, falls sie nach <code>delay</code> Millisekunden nicht beendet ist.
    * Die Methode muss die Signatur <code>methodName([Klassen der Objekte aus additionalArgumentClasses])</code> haben.
    * Die Klassen der Instancen aus additionalArguments vervollständigen die Signatur. Keine der Instanzen darf also null sein. 
    */
   public static void runWithDelayedProgressBar( IRunnableContext context, final String message, long delay, final Object callee, final String methodName,
         final Object... additionalArguments ) {
      Class[] argClasses = new Class[additionalArguments.length];
      for ( int i = 0, length = additionalArguments.length; i < length; i++ ) {
         argClasses[i] = additionalArguments[i].getClass();
      }
      runWithDelayedProgressBar(context, message, delay, callee, methodName, argClasses, additionalArguments);
   }

   /**
    * Fluent API version of {@link #runPreparedWithProgressBar(IRunnableContext, boolean, boolean)}. 
    * 
    * Runs a method of <code>callee</code> while the <code>context</code> is showing a progress 
    * bar during execution.<p/>
    * The method to run is determined like this:
    * <code><pre>
    *   ...
    *   SWTUtils.runWithProgressBar(window, true, false).on(this.getClass(), this).runWithProgressBar(null, 123);
    * }
    * 
    * protected void runWithProgressBar(IProgressMonitor mon, int arbitraryArgument){
    *   mon.beginTask("Working...", IProgressMonitor.UNKNOWN);
    *   // do something taking long
    *   mon.done();
    * }
    * </pre></code>
    * You must prepare a method having <code>IProgressMonitor</code> as first parameter type.
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use 
    * {@link #runWithProgressBar(IRunnableContext, boolean, boolean, Object, String, Object...) runWithProgressBar} or 
    * {@link #runWithProgressBar(IRunnableContext, boolean, boolean, Object, String, Class[], Object...) runWithProgressBar}.
    * @see #runWithProgressBar(IRunnableContext, boolean, boolean) runWithProgressBar(IRunnableContext, boolean, boolean) fluent version of this method - to be prefered
    * @see #prepareCallOn(Class)
    */
   public static Preparation runWithProgressBar( IRunnableContext context, boolean fork, boolean cancelable ) {
      return new ProgressBarPreparation(context, fork, cancelable);
   }

   /**
    * Führt eine Methode des callee mit ProgressBar aus.
    * Die Methode muss die Signatur <code>methodName(IProgressMonitor, [Klassen aus additionalArgumentClasses])</code> haben.
    */
   public static void runWithProgressBar( IRunnableContext context, boolean fork, boolean cancelable, final Object callee, final String methodName,
         Class[] additionalArgumentClasses, final Object... additionalArguments ) {

      if ( callee == null ) {
         _log.error("callee is null", new IllegalArgumentException());
         return;
      }

      Class[] argClasses = new Class[additionalArgumentClasses.length + 1];
      argClasses[0] = IProgressMonitor.class;
      for ( int i = 0, length = additionalArgumentClasses.length; i < length; i++ ) {
         argClasses[i + 1] = additionalArgumentClasses[i];
      }

      final Method method;
      try {
         method = Reflection.getMethod(callee.getClass(), methodName, argClasses);
      }
      catch ( Exception e ) {
         _log.error("No method " + methodName + " with proper signature in class " + callee.getClass() + ".", e);
         return;
      }

      runWithProgressBar(context, fork, cancelable, callee, method, additionalArguments);
   }

   /**
    * Führt eine Methode des callee mit ProgressBar aus.
    * Die Methode muss die Signatur <code>methodName(IProgressMonitor, ...)</code> haben.
    * Die Klassen der Instancen aus additionalArguments vervollständigen die Signatur. Keine der Instanzen darf also null sein. 
    */
   public static void runWithProgressBar( IRunnableContext context, boolean fork, boolean cancelable, final Object callee, final String methodName,
         final Object... additionalArguments ) {

      Class[] argClasses = new Class[additionalArguments.length];
      for ( int i = 0, length = additionalArguments.length; i < length; i++ ) {
         argClasses[i] = additionalArguments[i].getClass();
      }
      runWithProgressBar(context, fork, cancelable, callee, methodName, argClasses, additionalArguments);
   }

   /**
    * Fluent API version of {@link #syncExecPrepared(Display)}. 
    * 
    * A way of executing a method of <code>callee</code> in the UI thread without the need
    * of a anonymous inner class for the <code>Runnable</code> argument of {@link Display#syncExec(Runnable)}.<p/>
    * 
    * Using this API you can write refactoring and IDE friendly code like this:
    * 
    * <code><pre>
    *   ...
    *   SWTUtils.syncExec(display).on(this.getClass(), this).runInUIThread(123);
    * }
    * 
    * protected void runInUIThread(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use 
    * {@link #syncExec(Display, Object, String, Object...)} or 
    * {@link #syncExec(Display, Object, String, Class[], Object...)}.
    */
   public static Preparation syncExec( Display display ) {
      return new SyncExecPreparation(display);
   }

   public static void syncExec( Display display, final Object callee, final String methodName, Class[] argumentClasses, final Object... additionalArguments ) {
      exec(display, false, callee, methodName, argumentClasses, additionalArguments);
   }

   public static void syncExec( Display display, final Object callee, final String methodName, final Object... additionalArguments ) {
      exec(display, false, callee, methodName, additionalArguments);
   }

   /**
    * A way of executing a method of <code>callee</code> in the UI thread without the need
    * of a anonymous inner class for the <code>Runnable</code> argument of {@link Display#syncExec(Runnable)}.<p/>
    * 
    * Using this method in combination with {@link #prepareCallOn(Class, Object)} you can write refactoring 
    * and IDE friendly code like this:
    * 
    * <code><pre>
    *   ...
    *   SWTUtils.prepareCallOn(this.getClass(), this).runInUIThread(123);
    *   SWTUtils.syncExecPrepared(display);
    * }
    * 
    * protected void runInUIThread(int arbitraryArgument){
    *   // do something in the UI thread 
    * }
    * </pre></code>
    * 
    * Don't separate the call to {@link SWTUtils#prepareCallOn(Class)}
    * and this method. You can use the example above as pattern.<p/>
    * @see #syncExec(Display) syncExec(Display) fluent version of this method - to be prefered
    * @see #prepareCallOn(Class)
    */
   public static void syncExecPrepared( Display display ) {
      Method method = getLastPreparedMethod();
      Object[] additionalArguments = getLastPreparedMethodArguments();
      Object callee = getLastPreparedCallee();
      display.syncExec(new ExecRunnable(method, callee, additionalArguments));
   }

   public static Rectangle toDisplay( Control control, Rectangle bounds ) {
      Point p = control.toDisplay(bounds.x, bounds.y);
      bounds.x = p.x;
      bounds.y = p.y;
      return bounds;
   }

   /**
    * Average blurs a given row of image _data. Returns the blurred row as a
    * matrix of separated RGB values.
    */
   private static RGB[] blurRow( ImageData originalImageData, int row, int radius ) {
      RGB[] rowRGBAverages = new RGB[originalImageData.width]; // resulting rgb averages 
      int[] lineData = new int[originalImageData.width];
      originalImageData.getPixels(0, row, originalImageData.width, lineData, 0);
      int r = 0, g = 0, b = 0; // sum red, green, and blue values separately for averaging
      int leftSumBoundary = 0; // beginning index of summed values scope
      int targetColumn = 0; // column of RGB average to be determined
      int rightSumBoundary = 0; // ending index of summed values scope
      int numCols = 0; // number of columns included in current summing scope
      RGB rgb;
      while ( targetColumn < lineData.length ) {
         if ( rightSumBoundary < lineData.length ) {
            // sum RGB values for each pixel in our radius scope
            do {
               rgb = originalImageData.palette.getRGB(lineData[rightSumBoundary]);
               r += rgb.red;
               g += rgb.green;
               b += rgb.blue;
               numCols++;
               rightSumBoundary++;
            }
            while ( rightSumBoundary <= radius ); // to initialize summing scope at start
         }
         // subtract sum of left pixel as summing scope moves right
         if ( (targetColumn - leftSumBoundary) > (radius) ) {
            rgb = originalImageData.palette.getRGB(lineData[leftSumBoundary]);
            r -= rgb.red;
            g -= rgb.green;
            b -= rgb.blue;
            numCols--;
            leftSumBoundary++;
         }
         // calculate RGB averages
         rowRGBAverages[targetColumn] = new RGB(r / numCols, g / numCols, b / numCols);
         targetColumn++;
      }
      return rowRGBAverages;
   }

   private static <T> Class createProxy( Class<T> type ) {
      Class c = PROXY_CACHE.get(type);
      if ( c == null ) {
         Enhancer enhancer = new Enhancer();
         enhancer.setSuperclass(type);
         enhancer.setCallbackType(MethodInterceptor.class);
         if ( type.getSigners() != null ) {
            enhancer.setNamingPolicy(NAMING_POLICY_FOR_SIGNED_CLASSES);
         }
         c = enhancer.createClass();
         PROXY_CACHE.put(type, c);
      }
      return c;
   }

   private static void exec( Display display, boolean async, final Object callee, final String methodName, Class[] argumentClasses,
         final Object... additionalArguments ) {
      if ( callee == null ) {
         _log.error("callee is null", new IllegalArgumentException());
         return;
      }
      try {
         final Method method = Reflection.getMethod(callee instanceof Class ? (Class)callee : callee.getClass(), methodName, argumentClasses);
         if ( async ) {
            display.asyncExec(new ExecRunnable(method, callee, additionalArguments));
         } else {
            display.syncExec(new ExecRunnable(method, callee, additionalArguments));
         }
      }
      catch ( Exception e ) {
         _log.error("No method " + methodName + " with proper signature in class " + callee.getClass() + ".", e);
      }
   }

   private static void exec( Display display, boolean async, final Object callee, final String methodName, final Object... additionalArguments ) {
      Class[] argClasses = new Class[additionalArguments.length];
      for ( int i = 0, length = additionalArguments.length; i < length; i++ ) {
         argClasses[i] = additionalArguments[i].getClass();
      }

      exec(display, async, callee, methodName, argClasses, additionalArguments);
   }

   private static Image getImageError() {
      if ( IMAGE_ERROR == null ) {
         IMAGE_ERROR = ResourceManager.getImage(SWT.ICON_ERROR);
      }
      return IMAGE_ERROR;
   }

   private static String getLastPreparedCallstack() {
      StackTraceElement[] stackTrace = LAST_PREPARED_CALLSTACK.get();
      if ( stackTrace == null ) {
         return "";
      }
      return "\ncall stack of last call to SWTUtils.prepareCallOn: " + toString(stackTrace);
   }

   private static void handleThrowable( Throwable e, final Object callee, final Method method, final StackTraceElement[] stackTrace ) throws Error {
      while ( e.getCause() != null && e.getCause() != e && e instanceof InvocationTargetException ) {
         e = e.getCause();
      }

      _log.error("Failed to invoke method " + method.getName() + " in " + callee.getClass() + ".", e);
      StringBuilder s = new StringBuilder("\n");
      for ( int i = 1; i < stackTrace.length; i++ ) {
         s.append("\tat ").append(stackTrace[i]).append('\n');
      }
      _log.error("invokation stacktrace: " + s);

      new MessageDialog(e, new Image[] { getImageError() }).open(false);
   }

   private static void runWithDelayedProgressBar( IRunnableContext context, final String message, long delay, final Object callee, final Method method,
         final Object... additionalArguments ) {
      Thread runner = null;
      final Object sync = new Object();
      final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      runner = new Thread() {

         @Override
         public void run() {
            setName("SWTUtils.runWithDelayedProgressBar-worker");
            setPriority(getPriority() - 1);
            try {
               method.invoke(callee, additionalArguments);
            }
            catch ( Throwable e ) {
               handleThrowable(e, callee, method, stackTrace);
            }
            finally {
               synchronized ( sync ) {
                  setName("done");
                  sync.notify();
               }
            }
         }
      };
      runner.start();

      synchronized ( sync ) {
         try {
            if ( !runner.getName().equals("done") ) {
               sync.wait(delay);
            }
         }
         catch ( InterruptedException e ) {}
      }

      if ( !runner.getName().equals("done") && context != null ) {
         try {
            final Thread rrunner = runner;
            context.run(true, false, new IRunnableWithProgress() {

               public void run( IProgressMonitor monitor ) throws InvocationTargetException, InterruptedException {
                  monitor.beginTask(message, IProgressMonitor.UNKNOWN);
                  synchronized ( sync ) {
                     try {
                        if ( !rrunner.getName().equals("done") ) {
                           sync.wait();
                        }
                     }
                     catch ( InterruptedException e ) {}
                  }
                  monitor.done();
               }
            });
         }
         catch ( Exception e ) {
            _log.error("Failed to show delayed progress bar.", e);
         }
      }
   }

   private static void runWithProgressBar( IRunnableContext context, boolean fork, boolean cancelable, final Object callee, final Method method,
         final Object... additionalArguments ) {
      final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      try {
         context.run(fork, cancelable, new IRunnableWithProgress() {

            public void run( IProgressMonitor monitor ) throws InvocationTargetException, InterruptedException {
               try {
                  Object[] args = new Object[additionalArguments.length + 1];
                  args[0] = monitor;
                  System.arraycopy(additionalArguments, 0, args, 1, additionalArguments.length);
                  method.invoke(callee, args);
               }
               catch ( Throwable e ) {
                  handleThrowable(e, callee, method, stackTrace);
               }
               finally {
                  monitor.done();
               }
            }
         });
      }
      catch ( InvocationTargetException e ) {
         _log.error("unexpected exception", e);
      }
      catch ( InterruptedException e ) {
         _log.error("unexpected exception", e);
      }
   }

   private static String toString( final StackTraceElement[] stackTrace ) {
      StringBuilder s = new StringBuilder("\n");
      for ( int i = 1; i < stackTrace.length; i++ ) {
         s.append("\tat ").append(stackTrace[i]).append('\n');
      }
      return s.toString();
   }


   public abstract static class Preparation {

      /**
       * Part of the fluent API around {@link SWTUtils#prepareCallOn(Class, Object)}. Methods like 
       * {@link SWTUtils#asyncExec(Display)} provide instances of <code>Preparation</code>, on which
       * you are supposed to invoke this method. You get a proxy of the type you passed, on which
       * you can call any non private, non final method. This method will be called on the original 
       * <code>callee</code> instance you passed at some time later. 
       */
      public <T> T on( Class<T> target, Object callee ) {
         LAST_PREPARATION.set(this);
         return prepareCallOn(target, callee);
      }

      protected abstract void finishPreparation();
   }

   private static class AsyncExecPreparation extends Preparation {

      private final Display _display;


      private AsyncExecPreparation( Display display ) {
         _display = display;
      }

      @Override
      protected void finishPreparation() {
         asyncExecPrepared(_display);
      }
   }

   private static class DelayedProgressBarPreparation extends Preparation {

      private final IRunnableContext _context;
      private final String           _message;
      private final long             _delay;


      private DelayedProgressBarPreparation( IRunnableContext context, String message, long delay ) {
         _context = context;
         _message = message;
         _delay = delay;
      }

      @Override
      protected void finishPreparation() {
         runPreparedWithDelayedProgressBar(_context, _message, _delay);
      }
   }

   private static class ExecRunnable implements Runnable {

      private final Method        _method;
      private final Object        _callee;
      private final Object[]      _additionalArguments;
      private StackTraceElement[] _stackTrace;


      public ExecRunnable( Method method, Object callee, Object[] additionalArguments ) {
         _method = method;
         _callee = callee;
         _additionalArguments = additionalArguments;
         _stackTrace = Thread.currentThread().getStackTrace();
      }

      public void run() {
         try {
            _method.invoke(_callee, _additionalArguments);
         }
         catch ( Exception e ) {
            handleThrowable(e, _callee, _method, _stackTrace);
         }
      }

   }

   private static class MethodInterceptorImplementation implements MethodInterceptor {

      public Object intercept( Object obj, Method method, Object[] args, MethodProxy proxy ) throws Throwable {
         method.setAccessible(true);
         LAST_PREPARED_METHOD.set(method);
         LAST_PREPARED_METHOD_ARGS.set(args);

         Preparation preparation = LAST_PREPARATION.get();
         if ( preparation != null ) {
            LAST_PREPARATION.remove();
            preparation.finishPreparation();
         }

         return null;
      }
   }

   private static class ProgressBarPreparation extends Preparation {

      private final IRunnableContext _context;
      private final boolean          _fork;
      private final boolean          _cancelable;


      private ProgressBarPreparation( IRunnableContext context, boolean fork, boolean cancelable ) {
         _context = context;
         _fork = fork;
         _cancelable = cancelable;
      }

      @Override
      protected void finishPreparation() {
         runPreparedWithProgressBar(_context, _fork, _cancelable);
      }
   }

   private static class SyncExecPreparation extends Preparation {

      private final Display _display;


      private SyncExecPreparation( Display display ) {
         _display = display;
      }

      @Override
      protected void finishPreparation() {
         syncExecPrepared(_display);
      }
   }
}
