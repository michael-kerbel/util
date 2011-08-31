package util.dumpass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TableItem;

import util.reflection.FieldAccessor;


public class CopyColumnMenuManager extends MenuManager {

   boolean             _enabled = true;
   MenuItem            _menuItem;
   TableItem[]         _items;
   List<FieldAccessor> _fieldAccessors;

   public CopyColumnMenuManager( String menuName, TableItem[] items, List<FieldAccessor> fieldAccessors ) {
      super(menuName);
      _items = items;
      _fieldAccessors = fieldAccessors;

      if ( items == null || items.length == 0 || fieldAccessors == null || fieldAccessors.size() == 0 ) {
         _enabled = false;
      }
      if ( fieldAccessors != null ) {
      ArrayList<FieldAccessor> fas = new ArrayList<FieldAccessor>(fieldAccessors);
      Collections.sort(fas, new Comparator<FieldAccessor>() {

         public int compare( FieldAccessor o1, FieldAccessor o2 ) {
            return o1.getName().compareTo(o2.getName());
         }
      });

      for ( FieldAccessor fa : fas ) {
         add(new CopyItemAction(fa));
      }}
      add(new Separator());
      add(new CopyItemAction(null));
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

      private final FieldAccessor _accessor;

      public CopyItemAction( FieldAccessor fa ) {
         _accessor = fa;
         setText(fa == null ? "All data" : fa.getName());
      }

      @Override
      public void run() {
         StringBuilder data = new StringBuilder();
         for ( TableItem item : _items ) {
            for ( int i = 0, length = _fieldAccessors.size(); i < length; i++ ) {
               if ( _accessor == null ) {
                  data.append(data.length() > 0 ? "\n" : "").append(_fieldAccessors.get(i).getName()).append(": ").append(item.getText(i));
               } else if ( _fieldAccessors.get(i).equals(_accessor) ) {
                  data.append(data.length() > 0 ? "\n" : "").append(item.getText(i));
               }
            }
         }

         final Clipboard cb = new Clipboard(Display.getCurrent());
         if ( data != null ) {
            cb.setContents(new Object[] { data.toString() }, new Transfer[] { TextTransfer.getInstance() });
         }
      }
   }
}
