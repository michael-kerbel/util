package util.swt.viewerbuilder;

import java.util.Collections;

import net.miginfocom.swt.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import util.swt.BorderPainter;
import util.swt.Colors;
import util.swt.CopyTableCellAction;
import util.swt.CopyTableContentAction;
import util.swt.ResourceManager;
import util.swt.SWTUtils;
import util.swt.event.OnEvent;


public abstract class AbstractTable implements ViewerColumnSelectable {

   public static final int        ADD_FILTERBOX         = 1 << 0;
   public static final int        CUSTOM_DRAW_SELECTION = 1 << 1;

   public static final Color      COLOR_LIST_BACKGROUND = Colors.LIST_BACKGROUND;
   public static final Color      COLOR_SELECTED        = Colors.SELECTION_YELLOW;

   protected static final int     USE_GC_COLOR          = 1 << 22;

   protected Logger _log = LoggerFactory.getLogger(getClass());

   private final boolean          _addFilterbox;
   private final boolean          _customDrawSelection;
   private final GetterFacade     _facade;

   protected final ViewerColumn[] _allColumns;
   protected final ViewerColumn[] _defaultColumns;
   protected ViewerColumn[]       _columns;
   protected ViewerFilterBox      _filterBox;

   private FacadedTableBuilder    _builder;
   private Table                  _table;
   private MyTableViewer          _tableViewer;
   private int                    _tableFontHeight      = -1;


   public AbstractTable() {
      this(ADD_FILTERBOX | CUSTOM_DRAW_SELECTION);
   }

   public AbstractTable( int style ) {
      _addFilterbox = (style & ADD_FILTERBOX) != 0;
      _customDrawSelection = (style & CUSTOM_DRAW_SELECTION) != 0;
      _facade = createFacade();
      ViewerColumnCreator viewerColumnCreator = new ViewerColumnCreator(_facade);
      _allColumns = viewerColumnCreator.getAllColumns();
      _defaultColumns = viewerColumnCreator.getDefaultColumns();
   }

   public int columnIndexOf( String getterName ) {
      ViewerColumn[] currentColumns = getCurrentColumns();
      for ( int i = 0, length = currentColumns.length; i < length; i++ ) {
         if ( currentColumns[i].getGetterName().equals(getterName) ) {
            return i + 1;
         }
      }

      return -1;
   }

   public final ViewerColumn[] getAllColumns() {
      return _allColumns;
   }

   public final ViewerColumn[] getCurrentColumns() {
      return _columns;
   }

   public ViewerFilterBox getFilterBox() {
      return _filterBox;
   }

   public Table getTable() {
      return _table;
   }

   public TableViewer getTableViewer() {
      return _tableViewer;
   }

   public void refresh() {
      _table.setRedraw(false);
      _tableViewer.refresh();
      _table.setRedraw(true);
   }

   public void setColumns( ViewerColumn[] columns ) {
      _columns = columns;
      _table.setRedraw(false);
      Object input = _tableViewer.getInput();
      _tableViewer.setInput(Collections.EMPTY_LIST);
      _builder.reset();
      addColumns();
      setLabelProvider();
      _tableViewer.setInput(input);
      _tableViewer.refresh();
      _table.setRedraw(true);
      if ( _filterBox != null ) {
         _filterBox.resetToStringCache();
      }
   }

   public void setInput( Object input ) {
      _tableViewer.setInput(input);
      _tableViewer.refresh();
   }

   public void setSortColumn( String columnGetterName, boolean descending ) {
      BuilderSorter sorter = (BuilderSorter)_tableViewer.getSorter();
      int columnIndex = columnIndexOf(columnGetterName);
      if ( columnIndex >= 0 ) {
         sorter.setSortColumn(_tableViewer, columnIndex, descending);
         _table.setSortColumn(_table.getColumn(columnIndex));
         _table.setSortDirection(descending ? SWT.DOWN : SWT.UP);
      }
   }

   protected void addColumns() {
      _builder.addColumn(_columns[0]);
      for ( ViewerColumn col : _columns ) {
         _builder.addColumn(col);
      }

      TableColumn[] columns = _table.getColumns();
      columns[0].setWidth(0);
      columns[0].setMoveable(false);
      columns[0].setResizable(false);
      for ( int i = 1; i < columns.length; i++ ) {
         columns[i].setMoveable(true);
      }

      if ( _filterBox != null ) {
         _filterBox.resetToStringCache();
      }
   }

   protected void addTableMenuActions( MenuManager mm ) {
      mm.add(new CopyTableContentAction(_table, true));
      mm.add(new CopyTableContentAction(_table, false));

      IStructuredSelection selection = ((IStructuredSelection)_tableViewer.getSelection());
      mm.add(new CopyTableCellAction(_table, _table.getSelection()));
      MenuManager cmm = new CopyViewerColumnMenuManager("Zelleninhalt der gewählen Zeile kopieren", selection.toArray(), _builder, getAllColumns());
      mm.add(cmm);

      mm.add(new Separator());
      mm.add(new ViewerColumnSelectorAction(this));
   }

   protected abstract GetterFacade createFacade();

   protected void createFilterBox( Composite parent ) {
      _filterBox = new ViewerFilterBox(parent, true);
      Text filterControl = _filterBox.getFilterControl();
      filterControl.setData(BorderPainter.KEY_DRAW_BORDER, BorderPainter.TEXT_BORDER);
      Layout parentLayout = parent.getLayout();
      if ( parentLayout instanceof GridLayout ) {
         ((GridData)filterControl.getLayoutData()).verticalIndent = 2;
      } else if ( parentLayout instanceof MigLayout ) {
         filterControl.setLayoutData("growx, wrap");
      }
   }

   protected void createTable( Composite parent ) {
      _table = new Table(parent, getTableStyles());
      _table.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE));
      _table.setHeaderVisible(true);
      _table.setLinesVisible(false);

      Layout parentLayout = parent.getLayout();
      if ( parentLayout instanceof GridLayout ) {
         _table.setLayoutData(new GridData(GridData.FILL_BOTH));
      } else if ( parentLayout instanceof MigLayout ) {
         _table.setLayoutData("grow, wmin 0, hmin 0");
      }

      OnEvent.addListener(_table, SWT.Selection).on(getClass(), this).selection(null);
      OnEvent.addListener(_table, SWT.MouseDoubleClick).on(getClass(), this).mouseDoubleClicked(null);
      OnEvent.addListener(_table, SWT.MenuDetect).on(getClass(), this).menuDetected(null);
      if ( _customDrawSelection ) {
         OnEvent.addListener(_table, SWT.EraseItem).on(getClass(), this).eraseItem(null);
         OnEvent.addListener(_table, SWT.MouseUp).on(getClass(), this).mouseUp(null);
      }
   }

   protected void createViewer( Composite parent ) {
      if ( !(parent.getLayout() instanceof GridLayout || parent.getLayout() instanceof MigLayout) ) {
         throw new IllegalArgumentException("parent of a AbstractTable must have a GridLayout or a MigLayout! current layout: " + parent.getLayout().getClass());
      }

      if ( _addFilterbox ) createFilterBox(parent);

      _builder = new FacadedTableBuilder() {

         @Override
         protected TableViewer createTableViewer( Table table ) {
            return new MyTableViewer(table);
         }
      };

      createTable(parent);
      _builder.beginFacadedTableGrid(_facade, _table);
      _tableViewer = (MyTableViewer)_builder.getTableViewer();

      _tableViewer.setUseHashlookup(true);

      _columns = _defaultColumns;
      addColumns();

      setLabelProvider();

      if ( _filterBox != null ) {
         _filterBox.setSlaveViewer(_tableViewer);
      }
   }

   protected void disposeMenu( Event e, Menu menu ) {
      SWTUtils.asyncExec(_table.getDisplay(), menu, "dispose");
   }

   protected void eraseItem( Event event ) {
      if ( (event.detail & SWT.SELECTED) != 0 ) {
         GC gc = event.gc;
         Rectangle area = _table.getClientArea();
         int tableWidth = 0;
         for ( TableColumn t : _table.getColumns() ) {
            tableWidth += t.getWidth();
         }
         tableWidth = Math.max(tableWidth, area.width);

         /*
          * If you wish to paint the selection beyond the end of
          * last column, you must change the clipping region.
          */
         int columnCount = _table.getColumnCount();
         if ( event.index == columnCount - 1 || columnCount == 0 ) {
            int width = area.x + tableWidth - event.x;
            if ( width > 0 ) {
               Region region = new Region();
               gc.getClipping(region);
               region.add(event.x, event.y, width, event.height);
               gc.setClipping(region);
               region.dispose();
            }
         }
         Rectangle rect = event.getBounds();
         Color foreground = gc.getForeground();
         Color background = gc.getBackground();
         if ( (event.detail & USE_GC_COLOR) == 0 ) {
            gc.setForeground(COLOR_SELECTED);
         }
         gc.setBackground(COLOR_LIST_BACKGROUND);
         gc.fillGradientRectangle(-_table.getHorizontalBar().getSelection(), rect.y, tableWidth, rect.height, false);

         /* draw selection in own color */
         event.gc.setForeground(((TableItem)event.item).getForeground(event.index));
         String string = ((TableItem)event.item).getText(event.index);
         if ( _tableFontHeight == -1 ) {
            _tableFontHeight = event.gc.getFontMetrics().getHeight();
         }

         Image columnImage = ((ITableLabelProvider)_tableViewer.getLabelProvider()).getColumnImage(((TableItem)event.item).getData(), event.index);
         int imageIndent = 0;
         if ( columnImage != null ) {
            imageIndent = columnImage.getBounds().width + (event.index == 0 ? 0 : 2);
         }

         if ( (((TableItem)event.item).getParent().getColumn(event.index).getStyle() & SWT.RIGHT) != 0 ) {
            Point extent = event.gc.stringExtent(string);
            event.gc.drawString(string, rect.x + rect.width - 6 - extent.x, event.y + (int)((rect.height - _tableFontHeight) / 2f), true);
         } else {
            event.gc.drawString(string, imageIndent + rect.x + (event.index == 0 ? 6 : 6), event.y + (int)((rect.height - _tableFontHeight) / 2f), true);
            if ( columnImage != null ) {
               event.gc.drawImage(columnImage, rect.x + (event.index == 0 ? 4 : 0), rect.y);
            }
         }

         // restore colors for subsequent drawing
         gc.setForeground(foreground);
         gc.setBackground(background);
         event.detail &= ~SWT.SELECTED;
         event.detail &= ~SWT.BACKGROUND;
         event.detail &= ~SWT.FOREGROUND;
      }
   }

   protected Object[] getCurrentViewerList() {
      return _tableViewer.getSortedChildren();
   }

   protected Object getFirstSelectedElement() {
      return ((StructuredSelection)getTableViewer().getSelection()).getFirstElement();
   }

   protected Menu getHeaderMenu( Event event ) {
      MenuManager mm = new MenuManager();
      mm.add(new ViewerColumnSelectorAction(this));
      mm.add(new Separator());
      ViewerColumnToggleAction.addToggleActions(mm, this);
      Menu menu = mm.createContextMenu(_table.getShell());
      OnEvent.addListener(menu, SWT.Hide).on(getClass(), this).disposeMenu(null, menu);
      return menu;
   }

   protected LabelProviderWrapper getLabelProvider( ITableLabelProvider labelProvider ) {
      return null;
   }

   protected Menu getTableMenu( Event e ) {
      MenuManager mm = new MenuManager();
      addTableMenuActions(mm);
      Menu menu = mm.createContextMenu(_table.getShell());
      OnEvent.addListener(menu, SWT.Hide).on(getClass(), this).disposeMenu(null, menu);
      return menu;
   }

   protected int getTableStyles() {
      return SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.MULTI;
   }

   protected void inputChanged( Object newInput, Object oldInput ) {}

   protected void menuDetected( Event event ) {
      Point pt = _table.getDisplay().map(null, _table, new Point(event.x, event.y));
      Rectangle clientArea = _table.getClientArea();
      boolean header = clientArea.y <= pt.y && pt.y < (clientArea.y + _table.getHeaderHeight());
      Menu menu = header ? getHeaderMenu(event) : getTableMenu(event);
      if ( menu != null ) _table.setMenu(menu);
   }

   protected void mouseDoubleClicked( Event e ) {

   }

   protected void mouseUp( Event event ) {
      if ( event.button != 1 ) {
         return;
      }

      boolean clickOnItem = false;
      Rectangle clientArea = _table.getClientArea();
      Point pt = new Point(event.x, event.y);
      int index = _table.getTopIndex();
      int columnCount = _table.getColumnCount();
      while ( index < _table.getItemCount() ) {
         boolean visible = false;
         TableItem item = _table.getItem(index);
         for ( int i = 0; i < columnCount; i++ ) {
            Rectangle rect = item.getBounds(i);
            if ( rect.contains(pt) ) {
               clickOnItem = true;
            }
            if ( !visible && rect.intersects(clientArea) ) {
               visible = true;
            }
         }
         if ( !visible || clickOnItem ) {
            return;
         }
         index++;
      }

      if ( !clickOnItem ) {
         _tableViewer.setSelection(StructuredSelection.EMPTY);
      }
   }

   protected void selection( Event e ) {

   }

   protected void tableItemUpdated( TableItem widget, Object element ) {}

   private void setLabelProvider() {
      CollectionTableModel model = _builder.getModel();
      LabelProviderWrapper labelProvider = getLabelProvider(model);
      if ( labelProvider != null ) {
         getTableViewer().setLabelProvider(labelProvider);
      }
   }


   private class MyTableViewer extends TableViewer {

      public MyTableViewer( Table table ) {
         super(table);
      }

      @Override
      protected void doUpdateItem( Widget widget, Object element, boolean fullMap ) {
         super.doUpdateItem(widget, element, fullMap);
         tableItemUpdated((TableItem)widget, element);
      }

      protected Object[] getSortedChildren() {
         return getSortedChildren(getRoot());
      }

      @Override
      protected void inputChanged( Object input, Object oldInput ) {
         AbstractTable.this.inputChanged(input, oldInput);
      }
   }
}
