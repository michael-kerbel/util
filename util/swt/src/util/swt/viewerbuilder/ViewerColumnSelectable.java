
package util.swt.viewerbuilder;


public interface ViewerColumnSelectable {

   ViewerColumn[] getAllColumns();
   
   ViewerColumn[] getCurrentColumns();
   
   /**
    * Sets the columns that are to be displayed by the Viewer and updates the display.
    * A typical implementation looks like this:
    * <code>
    *   this._columns = columns;
    *   this._builder.reset();
    *   addColumns();
    *   this._treeViewer.refresh();
    * </code>
    */
   void setColumns(ViewerColumn[] col);
}



