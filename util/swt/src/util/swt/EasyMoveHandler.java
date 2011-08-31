package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Text;


public class EasyMoveHandler implements MouseListener, MouseMoveListener {

   protected static int SWP_NOSIZE       = Win32OS.getField("SWP_NOSIZE");
   protected static int SWP_NOZORDER     = Win32OS.getField("SWP_NOZORDER");
   protected static int SWP_NOACTIVATE   = Win32OS.getField("SWP_NOACTIVATE");
   protected static int WM_NCLBUTTONDOWN = Win32OS.getField("WM_NCLBUTTONDOWN");

   private Shell        _shell;
   private int          _lastMouseUpTime;

   private Point        _mouseLocationOnClick;

   private Point        _originalShellLocation;

   public EasyMoveHandler( Shell shell ) {
      _shell = shell;
      MouseListener l = this;
      if ( SWTUtils.IS_PLATFORM_WIN32 ) {
         l = new Win32EasyMoveHandler();
      }
      _shell.addMouseListener(l);
      addListeners(_shell, l);
   }

   public EasyMoveHandler( Shell shell, Composite[] draggableParents ) {
      _shell = shell;
      MouseListener l = this;
      if ( SWTUtils.IS_PLATFORM_WIN32 ) {
         l = new Win32EasyMoveHandler();
      }
      _shell.addMouseListener(l);
      for ( Composite c : draggableParents ) {
         addListener(c, l);
         addListeners(c, l);
      }
   }

   public void mouseDoubleClick( MouseEvent e ) {}

   public void mouseDown( MouseEvent e ) {
      _mouseLocationOnClick = ((Control)e.widget).toDisplay(e.x, e.y);
      _originalShellLocation = _shell.getLocation();
      ((Control)e.widget).addMouseMoveListener(this);
   }

   public void mouseMove( MouseEvent e ) {
      Point mouseLocation = ((Control)e.widget).toDisplay(e.x, e.y);
      _shell.setLocation(_originalShellLocation.x - (_mouseLocationOnClick.x - mouseLocation.x), _originalShellLocation.y
         - (_mouseLocationOnClick.y - mouseLocation.y));
   }

   public void mouseUp( MouseEvent e ) {
      ((Control)e.widget).removeMouseMoveListener(this);
   }


   protected void move( MouseEvent e ) {
      Win32OS.call("ReleaseCapture", new Class[] {});
      Win32OS.call("SendMessage", new Class[] { int.class, int.class, int.class, int.class, }, _shell.handle, WM_NCLBUTTONDOWN, 2, 0); // 2 == HT_CAPTION
      //         Win32OS.call("SetCapture", new Class[]{int.class}, _shell.handle);
   }

   private void addListener( Control control, MouseListener listener ) {
      if ( (control instanceof Text && (control.getStyle() & SWT.READ_ONLY) != 0) || control instanceof Group || control instanceof Label
         || control instanceof Shell || control instanceof TabFolder || control instanceof CLabel || control instanceof CLabel || control instanceof CTabFolder
         || (control instanceof StyledText && (control.getStyle() & SWT.READ_ONLY) != 0) || control.getClass() == Composite.class /* no inherited classes allowed */) {
         control.addMouseListener(listener);
      }
   }

   private void addListeners( Composite parent, MouseListener listener ) {
      for ( Control control : parent.getChildren() ) {
         addListener(control, listener);
         if ( control instanceof Composite ) {
            addListeners((Composite)control, listener);
         }
      }
   }

   private class Win32EasyMoveHandler implements MouseListener {

      public void mouseDoubleClick( MouseEvent e ) {}

      public void mouseDown( final MouseEvent e ) {
         final int mouseDownTime = e.time;
         final Point mouseLocationOnClick = _shell.getDisplay().getCursorLocation();
         _shell.getDisplay().timerExec(200, new Runnable() {

            public void run() {
               if ( _lastMouseUpTime >= mouseDownTime || _shell.isDisposed() ) {
                  return;
               }
               Point currentMouseLocation = _shell.getDisplay().getCursorLocation();
               Point shellLocation = _shell.getLocation();
               int x = shellLocation.x - (mouseLocationOnClick.x - currentMouseLocation.x);
               int y = shellLocation.y - (mouseLocationOnClick.y - currentMouseLocation.y);
               Win32OS.call("SetWindowPos", new Class[] { int.class, int.class, int.class, int.class, int.class, int.class, int.class, }, _shell.handle, 0, x,
                  y, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
               // _shell.setlocation macht aus dem Hintergrund Müll, wenn die Shell durchsichtig ist
               move(e);
            };
         });
      }

      public void mouseUp( MouseEvent e ) {
         _lastMouseUpTime = e.time;
      }

   }

}