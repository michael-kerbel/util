package util.swt.event;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.swt.events.ArmEvent;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TreeEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Widget;


public class EventLogger implements Listener {

   private Logger _log = LoggerFactory.getLogger(getClass());

   private EnumSet<SWTEvent>           _eventTypes = EnumSet.allOf(SWTEvent.class);
   private WeakHashMap<Widget, Object> _controls   = new WeakHashMap<Widget, Object>();

   /**
    * Constructs an EventLogger instance which will log ALL SWT Events using log4j.
    * Add the Widgets to be monitored with registerListener(.), change the Event types to be monitored with setEventTypes(.). 
    */
   public EventLogger() {}

   public Set<SWTEvent> getEventTypes() {
      return Collections.unmodifiableSet(_eventTypes);
   }

   public void handleEvent( Event event ) {
      SWTEvent e = SWTEvent.forType(event.type);
      String toString = e.name() + " [" + event.type + "]: ";
      switch ( e ) {
      case KeyDown:
      case KeyUp:
         toString += new KeyEvent(event).toString();
         break;
      case MouseDown:
      case MouseUp:
      case MouseMove:
      case MouseEnter:
      case MouseExit:
      case MouseDoubleClick:
      case MouseWheel:
      case MouseHover:
         toString += new MouseEvent(event).toString();
         break;
      case Paint:
         toString += new PaintEvent(event).toString();
         break;
      case Move:
      case Resize:
         toString += new ControlEvent(event).toString();
         break;
      case Dispose:
         toString += new DisposeEvent(event).toString();
         break;
      case Selection:
      case DefaultSelection:
         toString += new SelectionEvent(event).toString();
         break;
      case FocusIn:
      case FocusOut:
         toString += new FocusEvent(event).toString();
         break;
      case Expand:
      case Collapse:
         toString += new TreeEvent(event).toString();
         break;
      case Iconify:
      case Deiconify:
      case Close:
      case Activate:
      case Deactivate:
         toString += new ShellEvent(event).toString();
         break;
      case Show:
      case Hide:
         toString += (event.widget instanceof Menu) ? new MenuEvent(event).toString() : event.toString();
         break;
      case Modify:
         toString += new ModifyEvent(event).toString();
         break;
      case Verify:
         toString += new VerifyEvent(event).toString();
         break;
      case Help:
         toString += new HelpEvent(event).toString();
         break;
      case Arm:
         toString += new ArmEvent(event).toString();
         break;
      case Traverse:
         toString += new TraverseEvent(event).toString();
         break;
      case HardKeyDown:
      case HardKeyUp:
      case DragDetect:
      case MenuDetect:
      case SetData:
      default:
         toString += event.toString();
      }

      _log.info(toString);
   }

   public void registerListener( Widget c, boolean addToChildren ) {
      registerListener(c);
      if ( addToChildren && c instanceof Composite ) {
         for ( Control cc : ((Composite)c).getChildren() ) {
            registerListener(cc, addToChildren);
         }
      }
   }

   public void setEventTypes( Set<SWTEvent> eventTypes ) {
      removeFromAllWidgets();
      _eventTypes = EnumSet.copyOf(eventTypes);
      addToAllWidgets();
   }

   public void unregisterListener( Widget c, boolean removeFromChildren ) {
      unregisterListener(c);
      if ( removeFromChildren && c instanceof Composite ) {
         for ( Control cc : ((Composite)c).getChildren() ) {
            unregisterListener(cc, removeFromChildren);
         }
      }
   }

   @Override
   protected void finalize() throws Throwable {
      removeFromAllWidgets();
      super.finalize();
   }

   private void addToAllWidgets() {
      for ( Widget w : _controls.keySet() ) {
         if ( w != null && !w.isDisposed() ) {
            for ( SWTEvent e : _eventTypes ) {
               w.addListener(e.getType(), this);
            }
         }
      }
   }

   private void registerListener( Widget c ) {
      unregisterListener(c);
      for ( SWTEvent e : _eventTypes ) {
         c.addListener(e.getType(), this);
         _controls.put(c, null);
      }
   }

   private void removeFromAllWidgets() {
      for ( Widget w : _controls.keySet() ) {
         if ( w != null && !w.isDisposed() ) {
            for ( SWTEvent e : _eventTypes ) {
               w.removeListener(e.getType(), this);
            }
         }
      }
   }

   private void unregisterListener( Widget c ) {
      for ( SWTEvent e : _eventTypes ) {
         c.removeListener(e.getType(), this);
         _controls.remove(c);
      }
   }

}
