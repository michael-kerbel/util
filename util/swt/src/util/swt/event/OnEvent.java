package util.swt.event;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Widget;

import util.reflection.Reflection;
import util.swt.SWTUtils;
import util.swt.SWTUtils.Preparation;


/**
 * Allows implementing SWT event listeners without the need for anonymous inner classes.
 * @see {@link OnEvent#OnEvent()} 
 * @see {@link OnEvent#addListener(Widget, int) }
 * 
 */
public class OnEvent implements Listener {

   protected static Logger _log = LoggerFactory.getLogger(OnEvent.class);

   private static Object[] EMPTY_PARAMETER = new Object[0];

   private static Class[]  EVENT_SIGNATURE = { Event.class };


   /**
    * Fluent API for {@link #OnEvent()}.
    * 
    * Allows adding a listener to a <code>Widget</code> which forwards Events to a method provided like this:
    * <code><pre>
    * protected void createTable(Composite parent){
    *   ...
    *   Table table = new Table(parent, SWT.NONE);
    *   OnEvent.addListener(table, SWT.MouseHover)
    *          .on(this.getClass(), this).offerDoubleClick(null);
    *   ...
    * }
    * 
    * protected void offerDoubleClick( Event event ) {
    *   ...
    * }
    * </pre></code>
    * 
    * If you don't need the <code>Event</code> instance in your event handling code, 
    * you can omit it from your method signature. Otherwise the real event will get
    * injected to the first method parameter of type <code>Event</code>.
    * <p/>
    *
    * Other parameters will get passed on to your method. Following is possible:
    * <code><pre>
    * protected void createTable(Composite parent){
    *   ...
    *   Table table = new Table(parent, SWT.NONE);
    *   OnEvent.addListener(table, SWT.MouseHover)
    *          .on(this.getClass(), this).offerDoubleClick(table);
    *   ...
    * }
    * 
    * protected void offerDoubleClick( Table table ) {
    *   table.getSelection(); 
    *   ...
    * }
    * </pre></code>
    * 
    * <b>Beware:</b> Does not work with <code>private</code> methods or classes! 
    * <code>final</code> classes and methods are also unsupported. For these cases use 
    * {@link OnEvent#OnEvent(Object, String)} or 
    * {@link OnEvent#OnEvent(Object, String, boolean)}.
    */
   public static Preparation addListener( Widget widget, int type ) {
      return new OnEventPreparation(widget, type);
   }


   protected Object            _target;
   protected Method            _method;
   protected Class[]           _signature;
   private int                 _signatureEventIndex = -1;
   protected Object            _args[]              = new Object[1];
   private StackTraceElement[] _stackTraceDuringConstructor;


   /**
    * An IDE and refactoring friendly way of specifying the target method for this Listener 
    * can be used in combination with this constructor:
    * <code><pre>
    * protected void createTable(Composite parent){
    *   ...
    *   Table table = new Table(parent, SWT.NONE);
    *   SWTUtils.prepareCallOn(this.getClass(), this).offerDoubleClick(null);
    *   table.addListener(SWT.MouseHover, new OnEvent());
    *   ...
    * }
    * 
    * protected void offerDoubleClick( Event event ) {
    *   ...
    * }
    * </pre></code>
    * 
    * Don't separate the call to {@link SWTUtils#prepareCallOn(Class, Object)}
    * and this constructor. You can use the example above as pattern.<p/>
    * 
    * The <code>Event</code> instance will be passed to a <code>Event</code> 
    * method parameter, if one exists. All other parameters of the call on the 
    * proxy returned by {@link SWTUtils#prepareCallOn(Class, Object)} will be
    * passed on. Following is possible:
    * <code><pre>
    * protected void createTable(Composite parent){
    *   ...
    *   Table table = new Table(parent, SWT.NONE);
    *   SWTUtils.prepareCallOn(this.getClass(), this).offerDoubleClick(table);
    *   table.addListener(SWT.MouseHover, new OnEvent());
    *   ...
    * }
    * 
    * protected void offerDoubleClick( Table table ) {
    *   table.getSelection(); 
    *   ...
    * }
    * </pre></code>
    * 
    * @see SWTUtils#prepareCallOn(Class)
    * @see OnEvent OnEvent for other usage variants
    */
   public OnEvent() {
      _method = SWTUtils.getLastPreparedMethod();
      _args = SWTUtils.getLastPreparedMethodArguments();
      _target = SWTUtils.getLastPreparedCallee();
      _signature = _method.getParameterTypes();
      _stackTraceDuringConstructor = Thread.currentThread().getStackTrace();

      for ( int i = 0, length = _signature.length; i < length; i++ ) {
         if ( _signature[i] == getEventType() ) {
            _signatureEventIndex = i;
            break;
         }
      }
   }

   /** Example usage:
    * <code><pre>
    * protected void createTable(Composite parent){
    *   ...
    *   Table table = new Table(parent, SWT.NONE);
    *   table.addListener(SWT.MouseHover, new OnEvent(this, "offerDoubleClick"));
    *   ...
    * }  
    * 
    * protected void offerDoubleClick( Event event ) {
    *   ...
    * }
    * </pre></code>
    * 
    * If possible, you should prefer {@link OnEvent#OnEvent()} or even better 
    * {@link OnEvent#addListener(Widget, int)} to this constructor. It yields cleaner
    * code, since it stays IDE and refactoring friendly.
    */
   public OnEvent( Object target, String method ) {
      init(target, method, EVENT_SIGNATURE);
   }

   public OnEvent( Object target, String method, boolean methodHasEventParameter ) {
      init(target, method, methodHasEventParameter ? EVENT_SIGNATURE : new Class[0]);
   }

   protected OnEvent( Object target, String method, Class... sign ) {
      init(target, method, sign);
   }

   public void handleEvent( Event event ) {
      invoke(event);
   }

   protected Class<Event> getEventType() {
      return Event.class;
   }

   protected void invoke( Object event ) {
      try {
         if ( _signatureEventIndex >= 0 ) {
            _args[_signatureEventIndex] = event; // this is not threadsafe, of course, but the SWT event loop is single-threaded anyway...
            _method.invoke(_target, _args);
         } else if ( _signature.length == 0 ) {
            _method.invoke(_target, EMPTY_PARAMETER);
         } else if ( _signature.length == 1 ) {
            _args[0] = event; // this is not threadsafe, of course, but the SWT event loop is single-threaded anyway...
            _method.invoke(_target, _args);
         }
      }
      catch ( Exception ex ) {
         _log.error("Failed to invoke method " + _method, ex);
         if ( _stackTraceDuringConstructor != null ) {
            StringBuilder s = new StringBuilder("\n");
            for ( int i = 1; i < _stackTraceDuringConstructor.length; i++ ) {
               s.append("\tat ").append(_stackTraceDuringConstructor[i]).append('\n');
            }
            _log.error("construction stacktrace of this OnEvent instance: " + s);

         }
      }
   }

   private void init( Object target, String methodName, Class[] sign ) throws Error {
      try {
         _signature = sign;
         this._method = Reflection.getMethod(target instanceof Class ? (Class)target : target.getClass(), methodName, _signature);
         if ( this._method == null ) {
            throw new Error("dynamic binding failed: no such " + methodName + " in class " + target.getClass());
         }
         this._args[0] = null;
         this._target = target;
      }
      catch ( Exception ex ) {
         ex.printStackTrace();
      }
   }


   protected static class OnEventPreparation extends Preparation {

      private final Widget _widget;
      private final int    _type;


      public OnEventPreparation( Widget widget, int type ) {
         _widget = widget;
         _type = type;
      }

      @Override
      protected void finishPreparation() {
         _widget.addListener(_type, new OnEvent());
      }
   }
}
