
package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;


public class BuilderSorter extends ViewerSorter {

   private TableColumnMeta _gcd;
   private boolean         _ascending = true;

   @SuppressWarnings("unchecked")
   public int compare( Viewer viewer, Object e1, Object e2 ) {
      if ( _gcd == null )
         return 0;
      Comparable c1 = _gcd.toComparable(e1);
      Comparable c2 = _gcd.toComparable(e2);
      if ( c1 == null && c2 == null )
         return 0;
      if ( c1 == null )
         return -c2.compareTo(c1) * (_ascending ? 1 : -1);
      if ( c2 == null )
         return c1.compareTo(c2) * (_ascending ? 1 : -1);
      if ( c1 instanceof String && c2 instanceof String )
         return ((String)c1).compareToIgnoreCase((String)c2) * (_ascending ? 1 : -1);
      return c1.compareTo(c2) * (_ascending ? 1 : -1);
   }

   public void setColumnMeta( TableColumnMeta gcd ) {
      if ( _gcd == gcd )
         _ascending = !_ascending;
      else
         _ascending = true;
      _gcd = gcd;
   }

   public void setSortColumn( StructuredViewer viewer, int columnIndex ) {
	   setSortColumn(viewer, columnIndex, false);
   }
   
   public void setSortColumn( StructuredViewer viewer, int columnIndex, boolean descending ) {
	   if (viewer instanceof TreeViewer) {
		   setSortColumn((TreeViewer) viewer, columnIndex, descending);
	   } else if (viewer instanceof TableViewer) {
		   setSortColumn((TableViewer) viewer, columnIndex, descending);
	   }
   }
   
   public void setSortColumn( TreeViewer tree, int columnIndex ) {
      setColumnMeta((TableColumnMeta)tree.getTree().getColumn(columnIndex).getData(ViewerBuilder.COLUMN_META_KEY));
   }
   
   public void setSortColumn( TreeViewer tree, int columnIndex, boolean descending ) {
	  setColumnMeta((TableColumnMeta)tree.getTree().getColumn(columnIndex).getData(ViewerBuilder.COLUMN_META_KEY));
	  _ascending = !descending;
   }

   public void setSortColumn( TableViewer table, int columnIndex ) {
      setColumnMeta((TableColumnMeta)table.getTable().getColumn(columnIndex).getData(ViewerBuilder.COLUMN_META_KEY));
   }

   public void setSortColumn( TableViewer table, int columnIndex, boolean descending ) {
      setColumnMeta((TableColumnMeta)table.getTable().getColumn(columnIndex).getData(ViewerBuilder.COLUMN_META_KEY));
      _ascending = !descending;
   }

   public boolean isAscending() {
      return _ascending;
   }
}



