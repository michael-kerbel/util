package util.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;



public class TableColumnPacker {

   private static final String WIDTH_KEY       = "ColumnPacker.width";
   private static final int    MIN_WIDTH       = 10;

   private Table               _table;
   private int                 _oldClientWidth;
   private boolean             _updating;
   private int                 _minWidth       = MIN_WIDTH;
   private Listener            _tableListener  = new Listener() {

                                                  public void handleEvent( Event event ) {
                                                     if ( _updating ) {
                                                        return;
                                                     }
                                                     _updating = true;
                                                     packTable();
                                                     _updating = false;
                                                  }
                                               };
   private Listener            _columnListener = new Listener() {

                                                  public void handleEvent( Event event ) {
                                                     if ( _updating ) {
                                                        return;
                                                     }
                                                     _updating = true;
                                                     pack(event);
                                                     _updating = false;
                                                  }
                                               };

   /**
    * You must call <code>register()</code> to enable packing for <code>table</code>.
    */
   public TableColumnPacker( Table table ) {
      _table = table;
   }

   public int getColumnMinWidth() {
      return _minWidth;
   }

   public void register() {
      unregister();
      _table.addListener(SWT.Resize, _tableListener);
      for ( TableColumn col : _table.getColumns() ) {
         col.addListener(SWT.Resize, _columnListener);
         col.setData(WIDTH_KEY, col.getWidth());
      }
   }

   public void setColumnMinWidth( int width ) {
      _minWidth = width;
   }

   public void unregister() {
      _table.removeListener(SWT.Resize, _tableListener);
      for ( TableColumn col : _table.getColumns() ) {
         col.removeListener(SWT.Resize, _columnListener);
      }
   }

   protected void pack( Event event ) {
      TableColumn tc = (TableColumn)event.widget;
      int width = tc.getWidth();
      if ( width < _minWidth ) {
         width = _minWidth;
         tc.setWidth(width);
      }
      Integer oldWidth = (Integer)tc.getData(WIDTH_KEY);
      if ( oldWidth == null ) {
         oldWidth = tc.getWidth();
      }
      tc.setData(WIDTH_KEY, width);

      int index = _table.indexOf(tc);
      int[] columnOrder = _table.getColumnOrder();
      TableColumn neighbour = null;
      for ( int i = 0; i < columnOrder.length; i++ ) {
         if ( columnOrder[i] == index ) {
            int widthDelta = oldWidth - width;
            if ( i + 1 == columnOrder.length ) {
               tc.setWidth(oldWidth);
               tc.setData(WIDTH_KEY, oldWidth);
            }
            else if ( widthDelta > 0 ) {
               neighbour = _table.getColumn(columnOrder[i + 1]);
               addWidth(neighbour, widthDelta);
            }
            else {
               while ( i < columnOrder.length - 1 && widthDelta < 0 ) {
                  i++;
                  neighbour = _table.getColumn(columnOrder[i]);
                  widthDelta = addWidth(neighbour, widthDelta);
               }
               if ( widthDelta < 0 ) {
                  addWidth(tc, widthDelta);
               }
            }
         }
      }
   }

   protected void packTable() {
      int clientWidth = getClientWidth();

      if ( _oldClientWidth == clientWidth ) {
         return;
      }

      _oldClientWidth = clientWidth;

      TableColumn[] columns = _table.getColumns();
      int[] widths = new int[columns.length];
      int widthSum = 0;
      for ( int i = 0, length = columns.length; i < length; i++ ) {
         widths[i] = columns[i].getWidth();
         widthSum += widths[i];
      }

      float factor = clientWidth / (float)widthSum;

      widthSum = 0;
      _table.setRedraw(false);
      for ( int i = 0, length = columns.length - 1; i < length; i++ ) {
         int newWidth = Math.round(widths[i] * factor);
         columns[i].setWidth(newWidth);
         columns[i].setData(WIDTH_KEY, newWidth);
         widthSum += newWidth;
      }
      columns[columns.length - 1].setWidth(clientWidth - widthSum);
      columns[columns.length - 1].setData(WIDTH_KEY, clientWidth - widthSum);
      _table.setRedraw(true);
   }

   private int addWidth( TableColumn col, int delta ) {
      int w = col.getWidth();
      int rest = 0;
      if ( w + delta < _minWidth ) {
         rest = delta + (w - _minWidth);
         w = _minWidth;
      }
      else {
         w += delta;
      }
      col.setWidth(w);
      col.setData(WIDTH_KEY, w);
      return rest;
   }

   private int getClientWidth() {
      Rectangle area = _table.getParent().getClientArea();
      Point preferredSize = _table.computeSize(SWT.DEFAULT, SWT.DEFAULT);
      int width = area.width - 2 * _table.getBorderWidth();
      if ( preferredSize.y > area.height ) {
         Point vBarSize = _table.getVerticalBar().getSize();
         width -= vBarSize.x;
         if ( SWTUtils.IS_PLATFORM_GTK ) {
            width -= 3;
         }
      }
      return width;
   }

   private int getColumnWidthSum() {
      TableColumn[] columns = _table.getColumns();
      int widthSum = 0;
      for ( int i = 0, length = columns.length; i < length; i++ ) {
         widthSum += columns[i].getWidth() + 1;
      }
      return widthSum;
   }
}
