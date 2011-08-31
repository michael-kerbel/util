
package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.TreeColumn;


public class TreeColumnSortEventHandler extends SelectionAdapter {

   TreeViewer              _tree;
   private TableColumnMeta _gcd;


   public TreeColumnSortEventHandler( TreeViewer treeView, TableColumnMeta gcd ) {
      _tree = treeView;
      _gcd = gcd;
   }


   public void widgetSelected( SelectionEvent e ) {
      BuilderSorter sorter = (BuilderSorter)_tree.getSorter();
      sorter.setColumnMeta(_gcd);
      _tree.getTree().setRedraw(false);
      _tree.refresh();

      TreeColumn[] columns = _tree.getTree().getColumns();
      for ( TreeColumn column : columns ) {
         if ( column.getData(ViewerBuilder.COLUMN_META_KEY).equals(_gcd) ) {
            _tree.getTree().setSortColumn(column);
            _tree.getTree().setSortDirection(sorter.isAscending() ? SWT.UP : SWT.DOWN);
         }
      }
      _tree.getTree().setRedraw(true);
   }


}



