
package util.swt.viewerbuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.viewers.ILazyTreeContentProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;

import util.swt.SWTUtils;


public class CollectionVirtualTreeModel extends CollectionTreeModel implements ILazyTreeContentProvider{

   private Map<Object, Object[]> childrenCache = new HashMap<Object, Object[]>();
   
   public CollectionVirtualTreeModel( StructuredViewer viewer ) {
      super(viewer);
   }
   
   public void resetChildrenCache(){
      childrenCache.clear();
   }

   @Override
   public Object[] getChildren( Object obj ) {
      Object[] children = childrenCache.get(obj);
      if(children != null)
         return children;
      Collection col = (Collection)childProp.invoke(obj);
      if(col == null)
         col = Collections.EMPTY_LIST;
      
      children = filterAndSort(col);
      childrenCache.put(obj, children);
      return children;
   }

   @Override
   public void inputChanged( Viewer arg0, Object arg1, Object arg2 ) {
      super.inputChanged(arg0, arg1, arg2);
      if(arg0 != null && !arg0.getControl().isDisposed()){
         SWTUtils.asyncExec(arg0.getControl().getDisplay(), arg0, "refresh");
      }
   }


   public void updateChildCount( Object obj, int currentChildCount ) {
      int number = 0;
      Object[] col = getElements(obj);
      if( col != null )
         number = col.length;

      if(number != currentChildCount)
         ((TreeViewer)_viewer).setChildCount(obj, number);
   }

   public void updateElement( Object parent, int index ) {
      Object[] children = getElements(parent);
      if(children != null && children.length>index){
         ((TreeViewer)_viewer).replace(parent, index, children[index]);
         int number = 0;
         Object[] col = getElements(children[index]);
         if( col != null )
            number = col.length;
         ((TreeViewer)_viewer).setChildCount(children[index], number);
      }
   }
}



