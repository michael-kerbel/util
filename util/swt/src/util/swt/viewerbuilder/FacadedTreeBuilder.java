   
package util.swt.viewerbuilder;

import java.lang.reflect.Method;

import org.eclipse.swt.widgets.Tree;

public class FacadedTreeBuilder extends TreeBuilder {

	GetterFacade facade = null;
	
	public void beginFacadedTreeGrid(GetterFacade facade, Tree tree) {
		this.facade = facade;
		beginTreeGrid(facade.getClass(), tree);
	}

   @Override
	protected FacadedCollectionTableColumnMeta createColumnMeta() {
	   	FacadedCollectionTableColumnMeta mc = new FacadedCollectionTableColumnMeta(facade);
		_tableModel.addColumnMeta(mc);
		return mc;
	}

   public void setParent( String property ) {
	   
      FacadedCollectionTableColumnMeta gcd = new FacadedCollectionTableColumnMeta(facade);
      gcd.getter = (Method)_classGetter.get(property);
      gcd.caption = "";
      gcd.width = 0;
      gcd.sortable = false;
      gcd.nullVal = "";
      if(gcd.getter == null) throw new Error("no such parent getter:" + property);
      _treeModel.parentProp = gcd;
   }

   public void setChildren( String property ) {
	   
      CollectionTableColumnMeta gcd = new FacadedCollectionTableColumnMeta(facade);
      gcd.getter = (Method)_classGetter.get(property);
      gcd.caption = "";
      gcd.width = 0;
      gcd.sortable = false;
      gcd.nullVal = "";
      if(gcd.getter == null) throw new Error("no such children getter:" + property);
      _treeModel.childProp = gcd;
      
   }
	
}



