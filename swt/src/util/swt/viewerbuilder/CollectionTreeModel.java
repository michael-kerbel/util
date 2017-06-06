
package util.swt.viewerbuilder;

import java.util.Collection;
import java.util.Collections;

import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;


public class CollectionTreeModel extends CollectionTableModel implements ITreeContentProvider {

   public CollectionTreeModel( StructuredViewer viewer ) {
      super(viewer);
   }

   CollectionTableColumnMeta parentProp;
   CollectionTableColumnMeta childProp;
   
   CollectionTableColumnMeta createParentMeta() {
      CollectionTableColumnMeta m = new CollectionTableColumnMeta();
      //if(_sortCol == null) _sortCol = m;
      parentProp = m;
      return parentProp;
   }

   CollectionTableColumnMeta createChildMeta() {
      CollectionTableColumnMeta m = new CollectionTableColumnMeta();
      //if(_sortCol == null) _sortCol = m;
      childProp = m;
      return childProp;
   }
   
   public Object[] getChildren( Object obj ) {
      Collection col = (Collection)childProp.invoke(obj);
      if(col == null)
         col = Collections.EMPTY_LIST;
      if(_isVirtual)
         return filterAndSort(col);
      return col.toArray();
   }

   public Object getParent( Object obj ) {
      if(parentProp==null)return null;
      return parentProp.invoke(obj);
   }

   public boolean hasChildren( Object obj ) {
      if(childProp==null)return false;
      Collection col = (Collection)childProp.invoke(obj);
      return col != null && !col.isEmpty();
   }

   @Override
   public Object[] getElements(Object source) {
	   if (!(source instanceof Collection)) {
		   return getChildren(source);
	   }
	   return super.getElements(source);
   }

}



