package util.swt.viewerbuilder;

import java.lang.reflect.Method;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;


public class TreeBuilder extends ViewerBuilder {

   protected TreeViewer          _treeView;
   protected CollectionTreeModel _treeModel;
   protected Listener            _columnListener;

   public TreeBuilder() {
      _columnListener = new Listener() {

         public void handleEvent( Event event ) {
            Tree tree = _treeView.getTree();
            int[] orders = tree.getColumnOrder();
            if ( tree.getColumnCount() > 0 && "".equals(tree.getColumn(0).getData(ViewerBuilder.COLUMN_ID_KEY)) ) {
               int i = 0;
               while ( orders[i] != 0 ) {
                  i++;
               }
               while ( i > 0 ) {
                  orders[i] = orders[i - 1];
                  i--;
               }
               orders[i] = 0;
            }
            tree.setColumnOrder(orders);
            _treeView.refresh();
         }
      };
   }

   @Override
   public void addColumn( ViewerColumn col ) {
      super.addColumn(col);
      Tree tree = _treeView.getTree();
      TreeColumn tc = tree.getColumn(tree.getColumnCount() - 1);
      tc.setData(ViewerBuilder.COLUMN_ID_KEY, col.getGetterName());
   }

   public void beginTreeGrid( Class dispClass, Tree tree ) {
      initGetter(dispClass);
      _treeView = new TreeViewer(tree);
      //	 disabled since it doesn't work properly with filters and sorters...      
      //	      if((tree.getStyle() & SWT.VIRTUAL) != 0){
      //	         treeView.setUseHashlookup(true);
      //	         tableModel = treeModel = new CollectionVirtualTreeModel(treeView);
      //	      }
      //	      else
      _tableModel = _treeModel = new CollectionTreeModel(_treeView);
      _treeView.setSorter(new BuilderSorter());
      _treeView.setContentProvider(_treeModel);
      _treeView.setLabelProvider(_treeModel);
   }

   @Override
   public StructuredViewer getStructuredViewer() {
      return _treeView;
   }

   public TreeViewer getTreeViewer() {
      return _treeView;
   }

   @Override
   public void reset() {
      if ( _treeView == null ) return;
      for ( TreeColumn col : _treeView.getTree().getColumns() )
         col.dispose();
      String parentProperty = _treeModel.parentProp.getter.getName().substring(3);
      String childrenProperty = _treeModel.childProp.getter.getName().substring(3);
      parentProperty = Character.toLowerCase(parentProperty.charAt(0)) + parentProperty.substring(1);
      childrenProperty = Character.toLowerCase(childrenProperty.charAt(0)) + childrenProperty.substring(1);
      //	      tableModel = treeModel = (treeView.getTree().getStyle() & SWT.VIRTUAL) != 0 ? new CollectionVirtualTreeModel(treeView)
      //	            : new CollectionTreeModel(treeView);
      _tableModel = _treeModel = new CollectionTreeModel(_treeView);
      setParent(parentProperty);
      setChildren(childrenProperty);
      _treeView.setContentProvider(_treeModel);
      _treeView.setLabelProvider(_treeModel);
   }

   public void setChildren( String property ) {
      CollectionTableColumnMeta gcd = _treeModel.createChildMeta();
      gcd.getter = (Method)_classGetter.get(property);
      gcd.caption = "";
      gcd.width = 0;
      gcd.sortable = false;
      gcd.nullVal = "";
      if ( gcd.getter == null ) throw new Error("no such children getter:" + property);
   }

   /**
    * @param property must be the getter for the class of this Tree, which yields a Collection.
    */
   public void setParent( String property ) {
      CollectionTableColumnMeta gcd = _treeModel.createParentMeta();
      gcd.getter = (Method)_classGetter.get(property);
      gcd.caption = "";
      gcd.width = 0;
      gcd.sortable = false;
      gcd.nullVal = "";
      if ( gcd.getter == null ) throw new Error("no such parent getter:" + property);
   }

   @Override
   protected void postAddColumn( TableColumnMeta gcd, int columnStyle ) {
      TreeColumn col = new TreeColumn(_treeView.getTree(), columnStyle);
      col.setText(gcd.getCaption());
      col.setWidth(gcd.getWidth());
      col.setMoveable(true);
      col.setResizable(true);
      col.setData(ViewerBuilder.COLUMN_META_KEY, gcd);

      Image img = gcd.getImage();
      if ( img != null ) {
         col.setImage(img);
      }

      if ( gcd.isSortable() ) col.addSelectionListener(new TreeColumnSortEventHandler(_treeView, gcd));

      col.addListener(SWT.Move, _columnListener);
      col.addDisposeListener(new DisposeListener() {

         public void widgetDisposed( DisposeEvent e ) {
            e.widget.removeListener(SWT.Move, _columnListener);
         }
      });
   }
}
