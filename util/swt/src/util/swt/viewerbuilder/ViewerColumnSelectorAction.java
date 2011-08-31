
package util.swt.viewerbuilder;

import org.eclipse.jface.action.Action;


public class ViewerColumnSelectorAction extends Action {

   protected ViewerColumnSelectable _selectable;
   protected String                 _description;

   public ViewerColumnSelectorAction( ViewerColumnSelectable selectable ) {
      this(selectable, "Tabelle anpassen...", null);
   }

   public ViewerColumnSelectorAction( ViewerColumnSelectable selectable, String title, String description ) {
      super(title);
      _selectable = selectable;
      _description = description;
   }

   @Override
   public void run() {
      ViewerColumnSelector columnSelector = _description != null ? new ViewerColumnSelector(_selectable, getText(), _description) : new ViewerColumnSelector(
         _selectable);
      columnSelector.open(false);
   }

}



