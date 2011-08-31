package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Scrollable;


public class AutoMouseWheelAdapter implements Listener {

   int WM_VSCROLL  = Win32OS.getField("WM_VSCROLL");
   int WM_HSCROLL  = Win32OS.getField("WM_HSCROLL");
   int SB_LINEUP   = Win32OS.getField("SB_LINEUP");
   int SB_LINEDOWN = Win32OS.getField("SB_LINEDOWN");

   public AutoMouseWheelAdapter() {
      if ( SWTUtils.IS_PLATFORM_WIN32 ) {
         Display.getCurrent().addFilter(SWT.MouseWheel, this);
      }
   }

   public void handleEvent( Event event ) {
      Control cursorControl = Display.getCurrent().getCursorControl();

      if ( event.widget == cursorControl || cursorControl == null ) {
         return;
      }

      event.doit = false;
      int msg = WM_VSCROLL;
      int style = cursorControl.getStyle();

      if ( (style & SWT.V_SCROLL) != 0 && cursorControl instanceof Scrollable ) {
         ScrollBar verticalBar = ((Scrollable)cursorControl).getVerticalBar();

         if ( verticalBar != null
            && ((verticalBar.getMinimum() == 0 && verticalBar.getMaximum() == 0 && verticalBar.getSelection() == 0) || !verticalBar.isEnabled() || !verticalBar
                  .isVisible()) ) {
            msg = WM_HSCROLL;
         }
      }
      else if ( (style & SWT.H_SCROLL) == 0 ) {
         return;
      }
      else {
         msg = WM_HSCROLL;
      }

      int count = event.count;
      int wParam = SB_LINEUP;

      if ( event.count < 0 ) {
         count = -count;
         wParam = SB_LINEDOWN;
      }

      for ( int i = 0; i < count; i++ ) {
         Win32OS.call("SendMessage", new Class[] { int.class, int.class, int.class, int.class }, cursorControl.handle, msg, wParam, 0);
      }
   }
}

