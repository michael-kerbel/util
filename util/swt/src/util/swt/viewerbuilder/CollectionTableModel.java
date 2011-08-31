package util.swt.viewerbuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;


public class CollectionTableModel implements IStructuredContentProvider, ITableLabelProvider {

   Vector                    _meta      = new Vector();
   Object                    _data[]    = null;
   CollectionTableColumnMeta _sortCol   = null;
   boolean                   _ascending = true;
   StructuredViewer          _viewer;
   boolean                   _isVirtual = false;


   public CollectionTableModel( StructuredViewer viewer ) {
      _viewer = viewer;
      _isVirtual = viewer != null && (_viewer.getControl().getStyle() & SWT.VIRTUAL) != 0;
   }

   public void addColumnMeta( TableColumnMeta tcm ) {
      _meta.add(tcm);
   }

   public void addListener( ILabelProviderListener arg0 ) {}

   public void dispose() {}

   public int getColumnCount() {
      return _meta.size();
   }

   public Image getColumnImage( Object obj, int idx ) {
      return null;
   }

   public String getColumnText( Object obj, int idx ) {
      if ( _meta.size() <= idx ) return null;
      return ((TableColumnMeta)_meta.get(idx)).toString(obj);
   }

   public Object[] getElements( Object source ) {
      if ( _isVirtual ) {
         return filterAndSort((Collection)source);
      }
      if ( _data != null )
         return _data;
      else {
         _data = ((Collection)source).toArray();
         return _data;
      }
   }

   public Image getImage( Object arg0 ) {
      return null;
   }

   public void inputChanged( Viewer arg0, Object arg1, Object arg2 ) {
      _data = null;
   }

   public boolean isLabelProperty( Object arg0, String arg1 ) {
      return false;
   }

   public void removeListener( ILabelProviderListener arg0 ) {}

   protected Object[] filterAndSort( Collection collection ) {
      final ViewerSorter sorter = _viewer.getSorter();
      ViewerFilter[] filters = _viewer.getFilters();
      List list = applyFilters(filters, collection);
      Collections.sort(list, new Comparator() {

         public int compare( Object a, Object b ) {
            return sorter.compare(_viewer, a, b);
         }
      });
      return list.toArray();
   }

   CollectionTableColumnMeta addColumnMeta() {
      CollectionTableColumnMeta m = new CollectionTableColumnMeta();
      _meta.add(m);
      return m;
   }

   Iterator getColumnMeta() {
      return _meta.iterator();
   }

   private List applyFilters( ViewerFilter[] filters, Collection collection ) {
      List result = new ArrayList(collection.size());
      out: for ( Object object : collection ) {
         for ( ViewerFilter filter : filters ) {
            if ( !filter.select(_viewer, collection, object) ) continue out;
         }
         result.add(object);
      }
      return result;
   }

}
