
package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.TableColumn;


public class TableColumnSortEventHandler extends SelectionAdapter {

   private final TableColumnMeta _gcd;
   private final TableViewer _table;

   TableColumnSortEventHandler( TableViewer table, TableColumnMeta gcd ) {
      _table = table;
      _gcd = gcd;
      
   }

   public void widgetSelected( SelectionEvent e ) {
      BuilderSorter sorter = (BuilderSorter)_table.getSorter();
      if(sorter == null)
    	  return;
      sorter.setColumnMeta(_gcd);
      _table.getTable().setRedraw(false);
      _table.refresh();
      
      TableColumn[] columns = _table.getTable().getColumns();
      for ( TableColumn column : columns ) {
         if(column.getData(ViewerBuilder.COLUMN_META_KEY).equals(_gcd)){
            _table.getTable().setSortColumn(column);
            _table.getTable().setSortDirection(sorter.isAscending()?SWT.UP:SWT.DOWN);
         }
      }
      _table.getTable().setRedraw(true);
   }

}



