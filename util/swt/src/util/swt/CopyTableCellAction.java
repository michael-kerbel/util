package util.swt;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class CopyTableCellAction extends Action {

   protected final Table       _table;
   protected final TableItem[] _items;
   protected int               _columnIndex;

   public CopyTableCellAction( Event e, Table table, TableItem[] items ) {
      this(new Point(e.x, e.y), table, items);
   }

   public CopyTableCellAction( Point loc, Table table, TableItem[] items ) {
      _table = table;
      _items = items;

      Point pt = new Point(loc.x, loc.y);
      TableItem item = table.getItem(pt);
      _columnIndex = -1;
      if ( item != null ) {
         for ( int i = 0, length = table.getColumnCount(); i < length; i++ ) {
            Rectangle rect = item.getBounds(i);
            if ( rect.contains(pt) ) {
               _columnIndex = i;
            }
         }
      }
      setEnabled(_columnIndex >= 0);
      setText(_columnIndex >= 0 ? table.getColumn(_columnIndex) : null);
   }

   public CopyTableCellAction( Table table, TableItem[] items ) {
      this(table.toControl(table.getDisplay().getCursorLocation()), table, items);
   }

   @Override
   public void run() {
      StringBuilder data = new StringBuilder();
      for ( TableItem item : _items ) {
         data.append(data.length() > 0 ? "\n" : "").append(item.getText(_columnIndex));
      }

      final Clipboard cb = new Clipboard(Display.getCurrent());
      if ( data != null && data.toString().length() > 0 ) {
         cb.setContents(new Object[] { data.toString() }, new Transfer[] { TextTransfer.getInstance() });
      }
   }

   protected void setText( TableColumn col ) {
      setText(col != null ? col.getText() + " kopieren" : "Zelle kopieren");
   }
}
