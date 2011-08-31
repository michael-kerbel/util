package util.swt.viewerbuilder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;


public class CopyViewerColumnMenuManager extends MenuManager {

   boolean        _enabled = true;
   MenuItem       _menuItem;
   Object[]       _items;
   ViewerBuilder  _builder;
   ViewerColumn[] _columns;

   public CopyViewerColumnMenuManager( String menuName, Object item, ViewerBuilder builder, ViewerColumn[] columns ) {
      this(menuName, new Object[] { item }, builder, columns);
   }

   public CopyViewerColumnMenuManager( String menuName, Object item, ViewerBuilder builder, ViewerColumnSelectable selectable ) {
      this(menuName, item, builder, selectable.getAllColumns());
   }

   public CopyViewerColumnMenuManager( String menuName, Object[] items, ViewerBuilder builder, ViewerColumn[] columns ) {
      super(menuName);
      _items = items;
      _builder = builder;
      _columns = columns;

      if ( items == null || items.length == 0 || columns == null || columns.length == 0 ) {
         _enabled = false;
      }

      for ( ViewerColumn col : columns ) {
         String getterName = col.getGetterName();
         add(new CopyItemAction(col.getCaption(), getterName));
      }
      add(new Separator());
      add(new CopyItemAction("Alle Daten", null));
   }

   @Override
   public void fill( Menu parent, int index ) {
      super.fill(parent, index);
      _menuItem = parent.getItem(index < 0 ? parent.getItemCount() - 1 : index);
   }

   @Override
   public boolean isEnabled() {
      return _enabled;
   }

   @Override
   public void update() {}

   @Override
   protected void update( boolean force, boolean recursive ) {
      super.update(force, recursive);
      if ( _menuItem != null && !_menuItem.isDisposed() ) {
         _menuItem.setEnabled(_enabled);
      }
   }

   private class CopyItemAction extends Action {

      private final String _getterName;

      public CopyItemAction( String name, String getterName ) {
         _getterName = getterName;
         setText(name);
      }

      @Override
      public void run() {
         Map classGetter = _builder._classGetter;
         StringBuilder data = new StringBuilder();
         for ( Object item : _items ) {
            try {
               if ( _getterName != null ) {
                  data.append(data.length() > 0 ? "\n" : "").append(getData(classGetter, item, _getterName));
               } else {
                  for ( ViewerColumn col : _columns ) {
                     String getterName = col.getGetterName();
                     data.append(data.length() > 0 ? "\n" : "").append(col.getCaption()).append(": ").append(getData(classGetter, item, getterName));
                  }
               }
            }
            catch ( Exception e ) {}
         }

         final Clipboard cb = new Clipboard(Display.getCurrent());
         if ( data != null ) {
            cb.setContents(new Object[] { data.toString() }, new Transfer[] { TextTransfer.getInstance() });
         }
      }

      private String getData( Map classGetter, Object item, String getterName ) throws IllegalAccessException, InvocationTargetException {
         String data;
         if ( _builder instanceof FacadedTableBuilder ) {
            GetterFacade facade = ((FacadedTableBuilder)_builder)._facade;
            facade.setValueObject(item);
            data = ((Method)classGetter.get(getterName)).invoke(facade).toString();
         } else {
            data = ((Method)classGetter.get(getterName)).invoke(item).toString();
         }
         return data;
      }
   }
}
