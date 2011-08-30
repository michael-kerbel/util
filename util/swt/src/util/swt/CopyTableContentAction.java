package util.swt;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class CopyTableContentAction extends Action {

   private Table   _table;
   private int     _colNum;
   private String  _colHeader;
   private boolean _copyAll;

   public CopyTableContentAction( Table table, boolean copyAll ) {
      setText(copyAll ? "Ganze Tabelle kopieren" : "Ausgewählte Tabellenzeilen kopieren");
      _copyAll = copyAll;
      _table = table;
      _colNum = _table.getColumnCount();
      StringBuilder sb = new StringBuilder();
      for ( TableColumn c : _table.getColumns() )
         sb.append(sb.length() > 0 ? "\t" : "").append(c.getText());
      _colHeader = sb.append("\n").toString();
      if ( !_copyAll && _table.getSelectionCount() == 0 ) setEnabled(false);
   }

   @Override
   public void run() {
      StringBuilder sb = new StringBuilder();
      for ( TableItem i : _copyAll ? _table.getItems() : _table.getSelection() ) {
         for ( int j = 0; j < _colNum; j++ ) {
            sb.append(j == 0 ? "" : "\t").append(i.getText(j));
         }
         if ( sb.length() > 0 ) sb.append("\n");
      }
      sb.insert(0, _colHeader);

      final Clipboard cb = new Clipboard(Display.getCurrent());
      if ( sb.length() > 0 ) cb.setContents(new Object[] { sb.toString() }, new Transfer[] { TextTransfer.getInstance() });
   }
}