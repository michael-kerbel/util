package util.swt.viewerbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TreeColumn;

import util.swt.FilterBoxFilter;


public class ViewerFilterBoxFilter extends FilterBoxFilter {

   private final StructuredViewer _viewer;
   private Map<Object, String>    _cache  = new HashMap<Object, String>();
   private final boolean          _cacheToStringCalls;

   private Set<Object>            parents = new HashSet<Object>();


   public ViewerFilterBoxFilter( ViewerFilterBox box, StructuredViewer slaveViewer, boolean cacheToStringCalls ) {
      super(box);
      _viewer = slaveViewer;
      _cacheToStringCalls = cacheToStringCalls;
   }

   @Override
   public void reset() {
      parents.clear();
   }

   public void resetToStringCache() {
      _cache.clear();
   }

   @Override
   public boolean select( Viewer viewer, Object parentElement, Object element ) {
      if ( viewer instanceof TreeViewer ) {
         return selectTree((TreeViewer)viewer, (ITreeContentProvider)((TreeViewer)viewer).getContentProvider(), parentElement, element);
      }
      return super.select(viewer, parentElement, element);
   }

   @Override
   protected String toLowerCaseString( Object element ) {
      String ret = _cacheToStringCalls ? _cache.get(element) : null;
      if ( ret != null ) return ret;
      List<CollectionTableColumnMeta> metas = new ArrayList<CollectionTableColumnMeta>();
      if ( _viewer instanceof TableViewer ) {
         TableViewer table = (TableViewer)_viewer;
         for ( TableColumn col : table.getTable().getColumns() ) {
            CollectionTableColumnMeta gcd = (CollectionTableColumnMeta)col.getData(ViewerBuilder.COLUMN_META_KEY);
            if ( gcd != null && gcd.isUseInFilter() ) metas.add(gcd);
         }
      } else if ( _viewer instanceof TreeViewer ) {
         TreeViewer tree = (TreeViewer)_viewer;
         for ( TreeColumn col : tree.getTree().getColumns() ) {
            CollectionTableColumnMeta gcd = (CollectionTableColumnMeta)col.getData(ViewerBuilder.COLUMN_META_KEY);
            if ( gcd != null && gcd.isUseInFilter() ) metas.add(gcd);
         }
      }

      StringBuffer sb = new StringBuffer();
      for ( CollectionTableColumnMeta meta : metas )
         sb.append(' ').append(meta.toString(element));

      ret = sb.toString().toLowerCase();
      if ( _cacheToStringCalls ) _cache.put(element, ret);
      return ret;
   }

   private boolean selectTree( TreeViewer viewer, ITreeContentProvider provider, Object parentElement, Object element ) {
      // The root will always be shown.
      if ( (parentElement == null) && (element != null) ) {
         return true;
      }

      // Children will be added, if parents are.
      if ( parents.contains(parentElement) ) {
         parents.add(element);
         return true;
      }

      // Search in children. 
      if ( provider.hasChildren(element) ) {
         Object[] children = provider.getChildren(element);
         if ( children != null ) {
            for ( int i = children.length - 1; i >= 0; i-- ) {
               if ( selectTree(viewer, provider, element, children[i]) ) {
                  // Found matching child.
                  return true;
               }
            }
         }
      }

      // Check the item.
      if ( super.select(viewer, parentElement, element) == false ) {
         return false;
      }

      // Match!
      parents.add(element);
      return true;
   }

}
