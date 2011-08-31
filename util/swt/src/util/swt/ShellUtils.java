package util.swt;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;


public class ShellUtils {

   public static Point centerOnScreen( Shell shell ) {
      return centerOnScreen(shell, true);
   }

   /**
    * Center the dialog on the screen.
    * @param shell
    * @param doit <code>false</code> computes the location and size, <code>true</code> sets the location of the shell
    * @return
    */
   public static Point centerOnScreen( Shell shell, boolean doit ) {
      int wDialog = shell.getSize().x;
      int hDialog = shell.getSize().y;
      int wScreen = shell.getDisplay().getClientArea().width;
      int hScreen = shell.getDisplay().getClientArea().height;

      Point location = new Point((wScreen - wDialog) / 2, (hScreen - hDialog) / 2);
      if ( doit ) {
         shell.setLocation(location);
      }
      return location;
   }

   public static void fadeIn( final Shell shell, final int ms ) {
      if ( shell == null || shell.isDisposed() ) {
         return;
      }

      shell.setAlpha(0);
      shell.setVisible(true);
      if ( shell.isDisposed() ) {
         return;
      }

      final long t = System.currentTimeMillis();

      shell.getDisplay().timerExec(16, new Runnable() {

         public void run() {
            if ( shell.isDisposed() ) {
               return;
            }
            long tt = System.currentTimeMillis();
            long msPassed = tt - t;
            if ( msPassed < ms ) {
               int alpha = (int)(255 * (msPassed / (double)ms));
               if ( alpha != shell.getAlpha() ) {
                  shell.setAlpha(alpha);
               }
               shell.getDisplay().timerExec(16, this);
            }
            else {
               shell.setAlpha(255);
            }
         }
      });
   }

   public static void fadeIOut( final Shell shell, final int ms ) {
      if ( shell == null || shell.isDisposed() ) {
         return;
      }

      shell.setAlpha(255);

      final long t = System.currentTimeMillis();

      shell.getDisplay().timerExec(16, new Runnable() {

         public void run() {
            if ( shell.isDisposed() ) {
               return;
            }
            long tt = System.currentTimeMillis();
            long msPassed = tt - t;
            if ( msPassed < ms ) {
               shell.setAlpha((int)(msPassed / (double)ms));
               shell.getDisplay().timerExec(16, this);
            }
            else {
               shell.setVisible(false);
               shell.setAlpha(255);
            }
         }
      });
   }

   public static void setAlpha( Shell shell, int alpha ) {
      if ( shell == null || shell.isDisposed() ) {
         return;
      }

      shell.setAlpha(alpha);
   }
}


