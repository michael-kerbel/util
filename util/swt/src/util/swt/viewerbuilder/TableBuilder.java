package util.swt.viewerbuilder;

import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;


public class TableBuilder extends ViewerBuilder {

   protected TableViewer _tableView;
   protected Listener    _columnListener;

   public TableBuilder() {
      _columnListener = new Listener() {

         public void handleEvent( Event event ) {
            Table table = _tableView.getTable();
            int[] orders = table.getColumnOrder();
            if ( table.getColumnCount() > 0 && "".equals(table.getColumn(0).getData(ViewerBuilder.COLUMN_ID_KEY)) ) {
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
            table.setColumnOrder(orders);
            _tableView.refresh();
         }
      };
   }

   @Override
   public void addColumn( ViewerColumn col ) {
      super.addColumn(col);
      Table table = _tableView.getTable();
      TableColumn tc = table.getColumn(table.getColumnCount() - 1);
      tc.setData(ViewerBuilder.COLUMN_ID_KEY, col.getGetterName());
   }

   public void beginTableGrid( Class dispClass, Table table ) {
      initGetter(dispClass);
      _tableView = createTableViewer(table);
      _tableModel = new CollectionTableModel(_tableView);
      _tableView.setSorter(new BuilderSorter());
      _tableView.setContentProvider(_tableModel);
      _tableView.setLabelProvider(_tableModel);
   }

   @Override
   public StructuredViewer getStructuredViewer() {
      return _tableView;
   }

   public TableViewer getTableViewer() {
      return _tableView;
   }

   @Override
   public void reset() {
      if ( _tableView == null ) {
         return;
      }
      for ( TableColumn col : _tableView.getTable().getColumns() ) {
         col.dispose();
      }
      _tableModel = new CollectionTableModel(_tableView);
      _tableView.setContentProvider(_tableModel);
      _tableView.setLabelProvider(_tableModel);
   }

   protected TableViewer createTableViewer( Table table ) {
      return new TableViewer(table);
   }

   @Override
   protected void postAddColumn( TableColumnMeta gcd, int columnStyle ) {
      TableColumn col = new TableColumn(_tableView.getTable(), columnStyle);
      col.setText(gcd.getCaption());
      col.setWidth(gcd.getWidth());
      col.setMoveable(true);
      col.setResizable(true);
      col.setData(ViewerBuilder.COLUMN_META_KEY, gcd);

      Image img = gcd.getImage();
      if ( img != null ) {
         col.setImage(img);
      }

      if ( gcd.isSortable() ) {
         col.addSelectionListener(new TableColumnSortEventHandler(_tableView, gcd));
      }

      col.addListener(SWT.Move, _columnListener);
      col.addDisposeListener(new DisposeListener() {

         public void widgetDisposed( DisposeEvent e ) {
            e.widget.removeListener(SWT.Move, _columnListener);
         }
      });
   }
}
