package util.swt.viewerbuilder;

import org.eclipse.swt.widgets.Table;


public class FacadedTableBuilder extends TableBuilder {

   GetterFacade _facade = null;

   public void beginFacadedTableGrid( GetterFacade facade, Table table ) {
      this._facade = facade;
      beginTableGrid(facade.getClass(), table);
   }

   @Override
   protected CollectionTableColumnMeta createColumnMeta() {
      FacadedCollectionTableColumnMeta mc = new FacadedCollectionTableColumnMeta(_facade);
      _tableModel.addColumnMeta(mc);
      return mc;
   }

}
