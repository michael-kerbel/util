package util.dumpass;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.miginfocom.swt.MigLayout;

import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import util.dump.Dump;
import util.dump.DumpIndex;
import util.dump.DumpIndex.IndexMeta;
import util.dump.DumpIterator;
import util.dump.DumpUtils;
import util.dump.Externalizer.externalize;
import util.dump.InfiniteGroupIndex;
import util.dump.sort.InfiniteSorter;
import util.dump.stream.SingleTypeObjectStreamProvider;
import util.reflection.FieldAccessor;
import util.reflection.FieldFieldAccessor;
import util.string.StringFilter;
import util.swt.BorderPainter;
import util.swt.CopyTableCellAction;
import util.swt.CopyTableContentAction;
import util.swt.MessageDialog;
import util.swt.ResourceManager;
import util.swt.SWTUtils;
import util.swt.event.OnEvent;


@SuppressWarnings("unchecked")
public class GUI extends ApplicationWindow {

   private static final String SHELL_TITLE = "dump-ass";


   public static void main( String[] args ) throws FileNotFoundException {
      //      FileOutputStream fos = new FileOutputStream("errors.txt");
      //      PrintStream ps = new PrintStream(fos);
      //      System.setErr(ps);
      //      System.setOut(ps);

      GUI gui = new GUI();
      gui.setBlockOnOpen(true);
      gui.open();
   }


   private Logger             _log           = Logger.getLogger(getClass());

   Class                      _class;
   File                       _dumpFile;
   Dump                       _dump;
   List<MyInfiniteGroupIndex> _indexes;
   List<FieldAccessor>        _accessors;
   // TODO swap to disk using a dump
   TLongList                  _elementPositions;
   TLongList                  _elementPositionsFullDump;
   Image[]                    _warningImages = new Image[] { ResourceManager.getImage(SWT.ICON_WARNING) };
   GuiPrefs                   _prefs         = new GuiPrefs(this);

   Composite                  _parent;
   Table                      _table;
   CCombo                     _className;
   CCombo                     _fileName;
   ToolItem                   _loadDumpButton;
   ToolItem                   _searchDumpButton;
   ToolItem                   _repairDumpButton;
   ToolItem                   _selectDumpFile;
   ToolItem                   _openClasspathDialog;
   ControlDecoration          _classNameErrorDecoration;
   ControlDecoration          _fileNameErrorDecoration;
   CCombo                     _lookupField;
   Text                       _lookupText;
   ControlDecoration          _lookupFieldErrorDecoration;
   ToolItem                   _lookupButton;
   Text                       _searchText;
   ToolItem                   _searchButton;
   ToolItem                   _showAllButton;
   TabFolder                  _tabFolder;
   TabItem                    _openItem;
   TabItem                    _findItem;

   BeanMetaData               _beanMetaData;
   ToolItem                   _confirmDeletes;
   ToolItem                   _allowEditing;
   ToolItem                   _cleanupCreatedFiles;
   Editor                     _editor;
   Set<DumpFilesInfo>         _openedDumps   = new HashSet<DumpFilesInfo>();


   public GUI() {
      super(null);
      addStatusLine();
   }

   @Override
   public boolean close() {
      _prefs.storePreferences();

      if ( _dump != null ) {
         try {
            _dump.close();
         }
         catch ( IOException argh ) {
            _log.warn("Failed to close dump", argh);
         }
      }

      if ( _prefs.isCleanupCreatedFiles() ) {
         for ( DumpFilesInfo dfi : _openedDumps ) {
            dfi.clean();
         }
      }

      return super.close();
   }

   @Override
   protected void configureShell( Shell shell ) {
      super.configureShell(shell);
      shell.setText(SHELL_TITLE);
      shell.setImages(Images.SHELL_IMAGES);
   }

   @Override
   protected Control createContents( Composite parent ) {
      _parent = new Composite(parent, SWT.NONE);
      _parent.setLayout(new MigLayout("fillx, ins 0", "", "4[]")); // ins 0 2 0 2
      BorderPainter.paintBordersFor(_parent);

      _tabFolder = new TabFolder(_parent, SWT.TOP);
      _tabFolder.setLayoutData("wmin 0, growx, wrap");

      createOpenDumpTabFolder(_tabFolder);
      createFindTabFolder(_tabFolder);

      _table = new Table(_parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.MULTI);
      _table.setLayoutData("w 0:100%, h 0:100%");
      _table.setHeaderVisible(true);
      _table.setLinesVisible(true);

      createTopRight();

      _tabFolder.setFocus();

      return _parent;
   }

   @Override
   protected StatusLineManager createStatusLineManager() {
      StatusLineManager statusLineManager = new StatusLineManager();
      statusLineManager.setMessage(null, "");
      return statusLineManager;
   }

   protected Object getDumpElement( int index ) {
      return _dump.get(_elementPositions.get(index));
   }

   @Override
   protected Point getInitialLocation( Point initialSize ) {
      return _prefs.getLastGuiLocation();
   }

   @Override
   protected Point getInitialSize() {
      Point size = _prefs.getLastGuiSize();
      if ( size.x == Integer.MIN_VALUE || size.y == Integer.MIN_VALUE ) {
         Rectangle bounds = getShell().getDisplay().getPrimaryMonitor().getBounds();
         size.x = bounds.width * 8 / 10;
         size.y = bounds.height * 8 / 10;
      }
      return size;
   }

   @Override
   protected void initializeBounds() {
      Point size = getInitialSize();
      Point location = getInitialLocation(size);
      getShell().setBounds(new Rectangle(location.x, location.y, size.x, size.y));
      getShell().setMaximized(_prefs.isGuiMaximized());

      //        super.initializeBounds();
   }

   protected Class lookupClass() throws ClassNotFoundException {
      return _className == null ? null : lookupClass(_className.getText().trim());
   }

   protected Class lookupClass( String className ) throws ClassNotFoundException {
      Class c = null;
      try {
         c = Class.forName(className, true, createClassLoader());
      }
      catch ( ClassNotFoundException e ) {
         // try to instantiate inner class
         try {
            int lastDotIndex = className.lastIndexOf('.');
            if ( lastDotIndex > 0 ) {
               String outerclassname = className.substring(0, lastDotIndex);
               String innerclassname = className.substring(lastDotIndex + 1);
               Class outerClass = Class.forName(outerclassname, true, createClassLoader());
               for ( Class cc : outerClass.getClasses() ) {
                  if ( cc.getName().equals(outerclassname + '$' + innerclassname) ) {
                     c = cc;
                  }
               }
            }
         }
         catch ( Exception ee ) {
            // ignore
         }
         if ( c == null ) {
            throw e;
         }
      }
      return c;
   }

   @Override
   protected boolean showTopSeperator() {
      return false;
   }

   void allowEditingToggled( Event e ) {
      Editor.ALLOW_EDITING = _allowEditing.getSelection();
      _editor._cursor.setVisible(Editor.ALLOW_EDITING);
   }

   void classNameKeyUp( Event e ) {
      if ( e.character == SWT.DEL ) {
         String className = _className.getText();
         _prefs.removeClassName(className);
         _className.remove(className);
      }
   }

   void classNameModified( Event e ) {
      try {
         Class c = lookupClass();
         _classNameErrorDecoration.hide();

         boolean loadDumpPreconditionsMet = loadDumpPreconditionsMet(c, checkFileName());
         setLoadDumpButtonStatus(loadDumpPreconditionsMet);
      }
      catch ( NoClassDefFoundError argh ) {
         _classNameErrorDecoration.setDescriptionText("Failed to find class: " + argh.getMessage());
         _classNameErrorDecoration.show();
         setLoadDumpButtonStatus(false);
      }
      catch ( ClassNotFoundException argh ) {
         _classNameErrorDecoration.setDescriptionText("Failed to find class: " + argh.getMessage());
         _classNameErrorDecoration.show();
         setLoadDumpButtonStatus(false);
      }
   }

   void classNameSelected( Event e ) {
      try {
         Class c = lookupClass();
         if ( c != null ) {
            String dumpForBean = _prefs.getDumpForBean(c.getName());
            if ( dumpForBean != null && new File(dumpForBean).exists() ) {
               _fileName.setText(dumpForBean);
            }
         }
      }
      catch ( ClassNotFoundException argh ) {
         // ignore
      }
      catch ( NoClassDefFoundError argh ) {
         // ignore
      }
   }

   void cleanupCreatedFilesToggled( Event e ) {
      _prefs.setCleanupCreatedFiles(_cleanupCreatedFiles.getSelection());
   }

   void columnSorted( Event e ) {
      TableColumn sortColumn = _table.getSortColumn();
      TableColumn currentColumn = (TableColumn)e.widget;
      FieldAccessor fa = (FieldAccessor)currentColumn.getData();

      Class type = fa.getType();
      if ( !Comparable.class.isAssignableFrom(type) && type != int.class && type != long.class && type != boolean.class && type != byte.class
         && type != short.class && type != char.class && type != float.class && type != double.class ) {
         new MessageDialog("Column " + fa.getName() + " is not Comparable", // 
            "The selected column " + fa.getName() + " is not Comparable, sorting it is not possible.", _warningImages).open(true);
         return;
      }

      int dir = _table.getSortDirection();
      if ( sortColumn == currentColumn ) {
         dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
      } else {
         _table.setSortColumn(currentColumn);
         dir = SWT.UP;
      }

      // do the sort
      boolean descending = dir == SWT.DOWN;

      SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).sort(null, fa, descending);
   }

   void confirmDeletesToggled( Event e ) {
      Editor.CONFIRM_DELETES = _confirmDeletes.getSelection();
   }

   void createTable() {
      if ( _table != null && !_table.isDisposed() ) {
         _table.dispose();
      }

      _table = new Table(_parent, SWT.VIRTUAL | SWT.FULL_SELECTION | SWT.MULTI);
      _table.setLayoutData("w 0:100%, h 0:100%");
      _table.setHeaderVisible(true);
      _table.setLinesVisible(true);

      int[] beanColumnWidth = _prefs.getBeanColumnWidth();
      for ( int i = 0, length = _accessors.size(); i < length; i++ ) {
         FieldAccessor fa = _accessors.get(i);
         TableColumn col = new TableColumn(_table, SWT.NONE);
         col.setText(fa.getName());
         col.setData(fa);
         col.setWidth(beanColumnWidth != null && beanColumnWidth.length == length ? beanColumnWidth[i] : 200);
         col.setMoveable(true);
         OnEvent.addListener(col, SWT.Selection).on(GUI.class, this).columnSorted(null);
      }

      int[] beanColumnOrder = _prefs.getBeanColumnOrder();
      if ( beanColumnOrder != null && beanColumnOrder.length == _accessors.size() ) {
         _table.setColumnOrder(beanColumnOrder);
      }

      _table.addListener(SWT.SetData, new Listener() {

         public void handleEvent( Event e ) {
            try {
               TableItem item = (TableItem)e.item;
               int index = _table.indexOf(item);
               Object de = getDumpElement(index);
               String[] text = new String[_accessors.size()];
               for ( int i = 0, length = text.length; i < length; i++ ) {
                  Object o = de == null ? null : _accessors.get(i).get(de);
                  if ( o == null )
                     text[i] = "";
                  else if ( o.getClass().isArray() )
                     text[i] = ArrayUtils.toString(o);
                  else
                     text[i] = o.toString();
               }
               item.setText(text);
            }
            catch ( Exception argh ) {
               _log.error("", argh);
               new MessageDialog(argh, _warningImages).open(false);
            }
         }
      });

      _table.setItemCount(_elementPositions.size());
      OnEvent.addListener(_table, SWT.MenuDetect).on(GUI.class, this).tableMenu(null);

      _editor = new Editor(this, _table);

      _parent.layout();
   }

   void createTopRight() {
      ToolBar toolBar = new ToolBar(_parent, SWT.FLAT);
      _cleanupCreatedFiles = new ToolItem(toolBar, SWT.CHECK);
      _cleanupCreatedFiles.setImage(Images.CLEANUP16);
      _cleanupCreatedFiles.setToolTipText("Cleanup created dump and index files");
      _cleanupCreatedFiles.setSelection(_prefs.isCleanupCreatedFiles());
      OnEvent.addListener(_cleanupCreatedFiles, SWT.Selection).on(GUI.class, this).cleanupCreatedFilesToggled(null);
      _confirmDeletes = new ToolItem(toolBar, SWT.CHECK);
      _confirmDeletes.setImage(Images.CONFIRM_DELETE16);
      _confirmDeletes.setToolTipText("Confirm row deletes");
      _confirmDeletes.setSelection(_prefs.isConfirmDeletes());
      OnEvent.addListener(_confirmDeletes, SWT.Selection).on(GUI.class, this).confirmDeletesToggled(null);
      _allowEditing = new ToolItem(toolBar, SWT.CHECK);
      _allowEditing.setImage(Images.EDIT16);
      _allowEditing.setToolTipText("Allow in-place editing of the rows");
      _allowEditing.setSelection(_prefs.isAllowEditing());
      OnEvent.addListener(_allowEditing, SWT.Selection).on(GUI.class, this).allowEditingToggled(null);
      Editor.ALLOW_EDITING = _prefs.isAllowEditing();
      toolBar.setLayoutData("pos n 0 100% n");
      toolBar.moveAbove(_tabFolder);
   }

   void endSort( boolean descending ) {
      SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).initElementPositions(null);
      _table.setSortDirection(descending ? SWT.DOWN : SWT.UP);
      _table.clearAll();
   }

   void fileNameKeyUp( Event e ) {
      if ( e.character == SWT.DEL ) {
         String fileName = _fileName.getText();
         _prefs.removeDumpFileName(fileName);
         _fileName.remove(fileName);
      }
   }

   void fileNameModified( Event e ) {
      try {
         File f = checkFileName();
         if ( !f.exists() ) {
            throw new RuntimeException(f.getAbsolutePath() + " does not exist!");
         }
         _fileNameErrorDecoration.hide();

         boolean loadDumpPreconditionsMet = false;
         try {
            loadDumpPreconditionsMet = loadDumpPreconditionsMet(lookupClass(), f);
         }
         catch ( Exception argh ) {
            // ignore
         }
         setLoadDumpButtonStatus(loadDumpPreconditionsMet);

         setStatus(f.getAbsolutePath() + "   -   size: " + getHumanReadableBytes(f.length()) + "   -   last modification date: "
            + DateFormat.getDateTimeInstance().format(new Date(f.lastModified())));
      }
      catch ( Exception argh ) {
         //         _log.info("", argh);
         _fileNameErrorDecoration.setDescriptionText("Failed to find file: " + argh.getMessage());
         _fileNameErrorDecoration.show();
         setLoadDumpButtonStatus(false);
      }
   }

   void fileNameSelected( Event e ) {
      File f = checkFileName();
      if ( f.exists() ) {
         String beanForDump = _prefs.getBeanForDump(f.getAbsolutePath());
         if ( beanForDump != null ) {
            try {
               lookupClass(beanForDump);
               _className.setText(beanForDump);
            }
            catch ( ClassNotFoundException argh ) {
               // ignore
            }
            catch ( NoClassDefFoundError argh ) {
               // ignore
            }
         }
      }
   }

   void fillLookupFields() {
      List<FieldAccessor> fas = new ArrayList<FieldAccessor>(_accessors);
      Collections.sort(fas, new Comparator<FieldAccessor>() {

         public int compare( FieldAccessor o1, FieldAccessor o2 ) {
            return o1.getName().compareTo(o2.getName());
         }
      });

      String[] items = new String[fas.size()];
      for ( int i = 0, length = items.length; i < length; i++ ) {
         FieldAccessor fa = fas.get(i);
         items[i] = fa.getName();
         _lookupField.setData("" + i, fas.get(i));
      }
      _lookupField.setItems(items);
      _lookupField.setText(items[0]);
      _lookupText.setText("");

      lookupFieldSelected(null);
   }

   void initElementPositions( final IProgressMonitor mon ) {
      _indexes = new ArrayList<MyInfiniteGroupIndex>();

      discoverIndexes();
      if ( _indexes.size() > 0 ) {
         mon.beginTask("Analysing dump - using existing index for column " + _indexes.get(0).getFieldAccessor().getName(), IProgressMonitor.UNKNOWN);
         try {
            _elementPositions = _indexes.get(0).getAllPositions();
            _elementPositionsFullDump = _elementPositions;
            return;
         }
         finally {
            mon.done();
         }
      }

      // if we have no index, we must iterate all elements once to know their positions in the dump
      mon.beginTask("Analysing dump - iterating file", (int)(_dump.getDumpFile().length() / 1000));
      try {
         _elementPositions = new TLongArrayList(10000, 100000);
         _elementPositionsFullDump = _elementPositions;

         DumpIterator iterator = _dump.iterator();
         int lastWorked = 0;
         while ( iterator.hasNext() && !mon.isCanceled() ) {
            iterator.next();
            long pos = iterator.getPosition();
            _elementPositions.add(pos);
            if ( _elementPositions.size() % 1000 == 0 ) {
               mon.worked(((int)(pos / 1000)) - lastWorked);
               lastWorked = (int)(pos / 1000);
            }
         }
      }
      finally {
         mon.done();
      }
   }

   void loadDumpButtonSelected( Event e ) {
      try {
         if ( openDump() && _dump != null ) {
            initFieldAccessors();

            SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).initElementPositions(null);
            createTable();
            fillLookupFields();
            displayDumpStats();
            getShell().setText(SHELL_TITLE + " - " + _dump.getDumpFile().getAbsolutePath());
            _tabFolder.setSelection(_findItem);
            _beanMetaData = new BeanMetaData(_class, _dumpFile);
         }
      }
      catch ( Exception argh ) {
         _log.error("", argh);
         new MessageDialog(argh, _warningImages).open(false);
      }
   }

   void lookupButtonSelected( Event e ) {
      FieldAccessor fa = (FieldAccessor)_lookupField.getData("" + _lookupField.getSelectionIndex());
      if ( fa == null ) return;

      Object key = getLookupObject();
      if ( isFieldIndexSearchable(fa) ) {
         SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).lookupWithIndex(null, fa, key);
      } else {
         SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).lookupWithoutIndex(null, fa, key);
      }
   }

   void lookupFieldResized( Event e ) {
      Integer oldheight = (Integer)_lookupField.getData("oldheight");
      if ( oldheight == null || _lookupField.getSize().y != oldheight ) {
         _lookupField.setData("oldheight", _lookupField.getSize().y + 1);
         _lookupField.setSize(_lookupField.getSize().x, _lookupField.getSize().y + 1);
      }
   }

   void lookupFieldSelected( Event e ) {
      FieldAccessor fa = (FieldAccessor)_lookupField.getData("" + _lookupField.getSelectionIndex());
      if ( fa == null ) return;

      MyInfiniteGroupIndex index = null;
      for ( MyInfiniteGroupIndex i : _indexes ) {
         if ( i.getFieldAccessor().equals(fa) ) index = i;
      }

      if ( index == null ) {
         if ( isFieldIndexSearchable(fa) )
            _lookupFieldErrorDecoration.setDescriptionText("Loaded no index for field '" + fa.getName() + "' yet.\n" + //
               "Will have to create or load one for the first lookup, which will take some time.");
         else
            _lookupFieldErrorDecoration.setDescriptionText("Cannot use index for field '" + fa.getName() //
               + "' because I don't know how to instantiate items of type " + fa.getType() + ".\n" //
               + "I will have to search the whole dump, which will take some time.");
         _lookupFieldErrorDecoration.show();
      } else {
         _lookupFieldErrorDecoration.hide();
      }

      lookupTextModified(e);
   }

   void lookupTextModified( Event e ) {
      _lookupButton.setEnabled(getLookupObject() != null);
   }

   void lookupTextTraversed( Event e ) {
      if ( e.detail == SWT.TRAVERSE_RETURN ) {
         lookupButtonSelected(e);
      }
   }

   void lookupWithIndex( IProgressMonitor mon, FieldAccessor fa, Object key ) {
      MyInfiniteGroupIndex index = null;
      for ( MyInfiniteGroupIndex i : _indexes ) {
         if ( i.getFieldAccessor().equals(fa) ) index = i;
      }
      if ( index == null ) {
         mon.beginTask("Creating index for field '" + fa.getName() + "'", IProgressMonitor.UNKNOWN);
         try {
            index = new MyInfiniteGroupIndex(_dump, fa);
            _indexes.add(index);
         }
         finally {
            mon.done();
         }
      }
      long[] positions = null;
      if ( fa.getType().equals(int.class) || fa.getType().equals(Integer.class) ) {
         positions = index.getPositions(((Integer)key).intValue());
      } else if ( fa.getType().equals(long.class) || fa.getType().equals(Long.class) ) {
         positions = index.getPositions(((Long)key).longValue());
      } else if ( fa.getType().equals(String.class) ) {
         positions = index.getPositions(key);
      }
      if ( positions == null ) return;
      _elementPositions = new TLongArrayList(positions);
      SWTUtils.asyncExec(getShell().getDisplay()).on(_table.getClass(), _table).setItemCount(positions.length);
      SWTUtils.asyncExec(getShell().getDisplay()).on(_table.getClass(), _table).clearAll();
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).setLoadDumpButtonStatus(true);
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).lookupFieldSelected(null);
   }

   void lookupWithoutIndex( final IProgressMonitor mon, final FieldAccessor fa, final Object key ) {
      mon.beginTask("Searching dump", (int)(_dump.getDumpFile().length() / 1000));
      try {
         _elementPositions = new TLongArrayList(10000, 100000);
         DumpIterator iterator = _dump.iterator();
         int lastWorked = 0, n = 0;
         while ( iterator.hasNext() && !mon.isCanceled() ) {
            Object o = iterator.next();
            long pos = iterator.getPosition();
            try {
               if ( fa.get(o).toString().equals(key.toString()) ) _elementPositions.add(pos);
            }
            catch ( Exception argh ) {
               throw new RuntimeException(argh);
            }
            n++;
            if ( n % 1000 == 0 ) {
               mon.worked(((int)(pos / 1000)) - lastWorked);
               lastWorked = (int)(pos / 1000);
            }
         }
      }
      finally {
         mon.done();
      }
      SWTUtils.asyncExec(getShell().getDisplay()).on(_table.getClass(), _table).setItemCount(_elementPositions.size());
      SWTUtils.asyncExec(getShell().getDisplay()).on(_table.getClass(), _table).clearAll();
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).setLoadDumpButtonStatus(true);
   }

   void openClasspathDialog( Event e ) {
      new ClassPathDialog(_prefs).open(true);
      classNameModified(e);
   }

   void repairDump( File targetFile ) {
      if ( targetFile != null ) {
         Dump target = null;
         try {
            DumpUtils.closeSilently(_dump);
            _dump = new Dump(_class, _dumpFile);
            target = new Dump(_class, targetFile);
            DumpUtils.cleanup(_dump, target);
         }
         finally {
            DumpUtils.closeSilently(_dump);
            DumpUtils.closeSilently(target);
         }

         SWTUtils.asyncExec(getShell().getDisplay(), _fileName, "setText", targetFile.getAbsolutePath());
         SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).loadDumpButtonSelected(null);
      }
   }

   void repairDumpButtonSelected( Event e ) {
      final File[] selectedFile = new File[1];
      new MessageDialog("select target file for repaired dump", //
         "This feature tries to repair the dump you specified. All deleted elements will be pruned from the dump.\n\n" //
            + "Repairing corrupt elements only works if the element size in bytes is stable and not too many elements were deleted from the dump.\n" //
            + "Also some retained elements might contain broken data afterwards, if the binary representation of the element is still externalizable."
            + "I.e. there is no checksum mechanism in the dump.\n\n" //
            + "Please choose the target file for the repaired dump:", Images.SHELL_IMAGES, SWT.OK | SWT.CANCEL) {

         private Text _fileName;


         @Override
         public void createContent( Composite parent ) {
            _fileName = new Text(parent, SWT.SINGLE);
            _fileName.setFont(Fonts.CONSOLE_FONT);
            _fileName.addListener(SWT.Modify, new OnEvent(this, "fileNameModified"));
            _fileName.setText(GUI.this._fileName.getText() + ".repaired");
            GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL);
            _fileName.setLayoutData(gridData);
            BorderPainter.paintBordersFor(parent);
         }

         @Override
         public int getWidth() {
            return 800;
         };

         @Override
         public void ok() {
            selectedFile[0] = new File(_fileName.getText());
         };

         protected void fileNameModified( Event e ) {
            _ok.setEnabled(new File(_fileName.getText()).getParentFile().exists());
         }
      }.open(true);
      File dumpFile = selectedFile[0];

      if ( dumpFile != null ) {
         try {
            _class = lookupClass();
            _dumpFile = checkFileName();
            SWTUtils.runWithDelayedProgressBar(this, "repairing dump", 50).on(GUI.class, this).repairDump(dumpFile);
         }
         catch ( ClassNotFoundException ee ) {
            return;
         }
      }
   }

   void search( final IProgressMonitor mon, String searchText ) {
      // TODO create a Lucene index on first search and use it afterwards? Need to handle deletes and updates!

      final StringFilter stringFilter = new StringFilter();
      stringFilter.setFilterString(searchText.toLowerCase());

      mon.beginTask("Searching dump", (int)(_dump.getDumpFile().length() / 1000));
      try {
         _elementPositions = new TLongArrayList(10000, 100000);
         DumpIterator iterator = _dump.iterator();
         int lastWorked = 0, n = 0;
         while ( iterator.hasNext() && !mon.isCanceled() ) {
            Object o = iterator.next();
            long pos = iterator.getPosition();
            try {
               StringBuilder sb = new StringBuilder();
               for ( FieldAccessor fa : _accessors ) {
                  Object oo = fa.get(o);
                  if ( oo != null ) sb.append(" ").append(oo.toString());
               }
               if ( !stringFilter.filter(sb.toString()) ) _elementPositions.add(pos);
            }
            catch ( Exception argh ) {
               throw new RuntimeException(argh);
            }
            n++;
            if ( n % 1000 == 0 ) {
               mon.worked(((int)(pos / 1000)) - lastWorked);
               lastWorked = (int)(pos / 1000);
            }
         }
      }
      finally {
         mon.done();
      }
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).fillLookupFields();
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).createTable();
      SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).setLoadDumpButtonStatus(true);
   }

   void searchButtonSelected( Event e ) {
      try {
         // try to open dump, if not already opened
         if ( _dump == null ) {
            if ( !openDump() ) return;
            initFieldAccessors();
            _indexes = new ArrayList<MyInfiniteGroupIndex>();
         }
      }
      catch ( Exception argh ) {
         _log.error("", argh);
         new MessageDialog(argh, _warningImages).open(false);
         return;
      }
      SWTUtils.runWithProgressBar(this, true, true).on(GUI.class, this).search(null, _searchText.getText());
   }

   void searchDumpButtonSelected( Event e ) {
      _tabFolder.setSelection(_findItem);
      _searchText.setFocus();
   }

   void searchTextModified( Event e ) {
      try {
         _searchButton.setEnabled((_dump != null || loadDumpPreconditionsMet(lookupClass(), checkFileName())) && _searchText.getText().length() > 0);
      }
      catch ( ClassNotFoundException argh ) {
         if ( _searchButton != null ) // during createFindTabFolder _searchButton can be null
            _searchButton.setEnabled(false);
      }
      catch ( NoClassDefFoundError argh ) {
         if ( _searchButton != null ) // during createFindTabFolder _searchButton can be null
            _searchButton.setEnabled(false);
      }
   }

   void searchTextTraversed( Event e ) {
      if ( e.detail == SWT.TRAVERSE_RETURN ) {
         searchButtonSelected(e);
      }
   }

   void setLoadDumpButtonStatus( boolean enabled ) {
      if ( _loadDumpButton != null ) {
         _loadDumpButton.setEnabled(enabled);
      }
      if ( _searchDumpButton != null ) {
         _searchDumpButton.setEnabled(enabled);
      }
      if ( _repairDumpButton != null ) {
         _repairDumpButton.setEnabled(enabled);
      }
      if ( _showAllButton != null ) {
         _showAllButton.setEnabled(enabled && _elementPositions != _elementPositionsFullDump);
      }
   }

   void showAllButtonSelected( Event e ) {
      if ( _elementPositionsFullDump != null ) {
         _elementPositions = _elementPositionsFullDump;
         _table.setItemCount(_elementPositions.size());
         _table.clearAll();
      } else
         loadDumpButtonSelected(e);
   }

   void showFileDialog( Event e ) {
      FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
      dialog.setFilterNames(new String[] { "all file types", "dump files" });
      dialog.setFilterExtensions(new String[] { "*.*", "*.dmp" }); //Windows wild cards
      dialog.setFilterPath("."); //Windows path
      dialog.setFileName(_fileName.getText());

      String f = dialog.open();
      if ( f != null ) {
         _fileName.setText(f);
      }
      fileNameModified(e);
   }

   void sort( IProgressMonitor mon, final FieldAccessor fa, final boolean descending ) {
      try {
         mon.beginTask("sorting using column " + fa.getName(), _elementPositions.size() * 2);

         String suffix = ".sorted." + fa.getName() + (descending ? ".desc" : ".asc");
         File sortedDumpFile = new File(_dumpFile.getAbsolutePath() + suffix);
         sortedDumpFile.deleteOnExit();

         if ( !sortedDumpFile.exists() ) {
            // we have no reusable sort dump, do it the hard way 
            InfiniteSorter sorter = new InfiniteSorter();
            sorter.setObjectStreamProvider(new SingleTypeObjectStreamProvider(_class));
            sorter.setComparator(new Comparator() {

               public int compare( Object o1, Object o2 ) {
                  try {
                     Comparable f1 = (Comparable)fa.get(o1);
                     Comparable f2 = (Comparable)fa.get(o2);
                     if ( f1 == null )
                        return f2 == null ? 0 : (descending ? 1 : -1);
                     else if ( f2 == null ) return (descending ? -1 : 1);
                     return f1.compareTo(f2) * (descending ? -1 : 1);
                  }
                  catch ( Exception argh ) {
                     throw new RuntimeException("Failed to sort. " + argh.getMessage(), argh);
                  }
               }
            });
            for ( Object e : _dump ) {
               sorter.add(e);
               mon.worked(1);
               if ( mon.isCanceled() ) {
                  sortedDumpFile.delete();
                  return;
               }
            }
            Dump sortedDump = new Dump(_class, sortedDumpFile);
            for ( Object e : sorter ) {
               sortedDump.add(e);
               mon.worked(1);
               if ( mon.isCanceled() ) {
                  sortedDump.close();
                  sortedDumpFile.delete();
                  return;
               }
            }
            _dump.close();
            _dump = sortedDump;
         } else {
            _dump.close();
            _dump = new Dump(_class, sortedDumpFile);
         }

         SWTUtils.asyncExec(getShell().getDisplay()).on(GUI.class, this).endSort(descending);

      }
      catch ( Exception argh ) {
         _log.error("Failed to sort.", argh);
         new MessageDialog(argh, _warningImages).open(true);
      }
      finally {
         mon.done();
      }

   }

   void tableMenu( Event e ) {
      MenuManager mm = new MenuManager("table context menu");
      CopyTableContentAction copyTableContentAction = new CopyTableContentAction(_table, false);
      copyTableContentAction.setText("copy selected rows");
      mm.add(copyTableContentAction);
      TableItem[] selection = _table.getSelection();
      CopyTableCellAction copyFieldAction = new CopyTableCellAction(_table, selection) {

         @Override
         protected void setText( TableColumn col ) {
            setText(col != null ? "copy " + col.getText() : "copy cell");
         }
      };
      if ( copyFieldAction.isEnabled() ) mm.add(copyFieldAction);
      mm.add(new CopyColumnMenuManager("copy cell" + (selection.length > 1 ? "s" : ""), selection, _accessors));
      Menu menu = mm.createContextMenu(_table);
      menu.setLocation(getShell().getDisplay().getCursorLocation());
      menu.setVisible(true);
   }

   private File checkFileName() {
      String filename = _fileName.getText().trim();
      File dumpFile = new File(filename);
      return dumpFile;
   }

   private ClassLoader createClassLoader() {
      List<URL> urls = new ArrayList<URL>();
      for ( String path : _prefs.getClasspathElements() ) {
         File f = new File(path);
         if ( f.exists() ) {
            try {
               urls.add(f.toURI().toURL());
            }
            catch ( MalformedURLException argh ) {
               _log.error("", argh);
               new MessageDialog(argh, _warningImages).open(false);
            }
         }
      }

      return new URLClassLoader(urls.toArray(new URL[urls.size()]), getClass().getClassLoader());
   }

   private void createFindTabFolder( TabFolder tabFolder ) {
      _findItem = new TabItem(tabFolder, SWT.NONE);
      _findItem.setText("find");

      Composite parent = new Composite(tabFolder, SWT.NONE);
      parent.setLayout(new MigLayout("", "", "[]0[]"));
      BorderPainter.paintBordersFor(parent);

      String tooltip = "Searches for a value in a single field, using an index if possible.\n" + //
         "The index will be created if it doesn't exist yet.\n" + //
         "Only int, long and String fields are supported for index search.\n" + //
         "For other column types a simple search is used.";
      Label label = new Label(parent, SWT.NONE);
      label.setText("lookup value in field");
      label.setToolTipText(tooltip);
      _lookupField = new CCombo(parent, SWT.FLAT | SWT.READ_ONLY);
      _lookupField.setLayoutData("gapx 5, wmin 30");
      _lookupField.setToolTipText(tooltip);
      _lookupField.setVisibleItemCount(20);
      _lookupFieldErrorDecoration = new ControlDecoration(_lookupField, SWT.LEFT | SWT.UP);
      _lookupFieldErrorDecoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_WARNING).getImage());
      _lookupFieldErrorDecoration.setMarginWidth(1);
      _lookupFieldErrorDecoration.hide();
      OnEvent.addListener(_lookupField, SWT.Selection).on(GUI.class, this).lookupFieldSelected(null);
      OnEvent.addListener(_lookupField, SWT.Resize).on(GUI.class, this).lookupFieldResized(null);

      _lookupText = new Text(parent, SWT.SINGLE);
      _lookupText.setFont(Fonts.CONSOLE_FONT);
      _lookupText.setLayoutData("w 30:100%:600, growx, sg");
      OnEvent.addListener(_lookupText, SWT.Modify).on(GUI.class, this).lookupTextModified(null);
      OnEvent.addListener(_lookupText, SWT.Traverse).on(GUI.class, this).lookupTextTraversed(null);
      _lookupText.setToolTipText(tooltip);

      _lookupButton = createToolItem(parent, Images.SEARCH16, null, tooltip, "lookupButtonSelected");
      //      _lookupButton.getParent().setLayoutData("wrap");
      _lookupButton.setEnabled(false);

      _showAllButton = createToolItem(parent, Images.UNDO24, "show all", "Show all data in the current dump", "showAllButtonSelected");
      _showAllButton.getParent().setLayoutData("spany 2, wrap");
      new ToolItem(_showAllButton.getParent(), SWT.SEPARATOR, 0);

      tooltip = "Searches using a query in all fields.\n" + //
         "For this, the whole index is iterated.";
      label = new Label(parent, SWT.NONE);
      label.setText("search query using all fields");
      label.setLayoutData("spanx 2");
      label.setToolTipText(tooltip);

      _searchText = new Text(parent, SWT.SINGLE);
      _searchText.setFont(Fonts.CONSOLE_FONT);
      _searchText.setLayoutData("wmin 30, sg");
      _searchText.setToolTipText(tooltip);

      OnEvent.addListener(_searchText, SWT.Modify).on(GUI.class, this).searchTextModified(null);
      OnEvent.addListener(_searchText, SWT.Traverse).on(GUI.class, this).searchTextTraversed(null);

      _searchButton = createToolItem(parent, Images.SEARCH16, null, tooltip, "searchButtonSelected");
      _searchButton.setEnabled(false);

      _findItem.setControl(parent);
   }

   private void createOpenDumpTabFolder( TabFolder tabFolder ) {
      _openItem = new TabItem(tabFolder, SWT.NONE);
      _openItem.setText("open");

      Composite parent = new Composite(tabFolder, SWT.NONE);
      parent.setLayout(new MigLayout("fillx", "[][][][][][]push", "[]0[]"));
      BorderPainter.paintBordersFor(parent);

      Label label = new Label(parent, SWT.NONE);
      label.setText("Dump file path");
      _fileName = new CCombo(parent, SWT.FLAT);
      _fileName.setLayoutData("w 30:100%:600, gapx 5, growx");
      _fileName.setFont(Fonts.CONSOLE_FONT);
      _fileName.setVisibleItemCount(20);
      OnEvent.addListener(_fileName, SWT.KeyUp).on(GUI.class, this).fileNameKeyUp(null);
      _fileNameErrorDecoration = new ControlDecoration(_fileName, SWT.LEFT | SWT.UP);
      _fileNameErrorDecoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage());
      _fileNameErrorDecoration.setMarginWidth(1);
      OnEvent.addListener(_fileName, SWT.Modify).on(GUI.class, this).fileNameModified(null);
      OnEvent.addListener(_fileName, SWT.Selection).on(GUI.class, this).fileNameSelected(null);
      String[] dumpFileNames = _prefs.getDumpFileNames();
      if ( dumpFileNames.length > 0 ) {
         _fileName.setText(dumpFileNames[0]);
      }
      updateDumpFileNames();
      _selectDumpFile = createToolItem(parent, Images.FIND16, null, "Select dump file...", "showFileDialog");

      _loadDumpButton = createToolItem(parent, Images.PLAY24, "load dump", "Initialize dump from disk", "loadDumpButtonSelected");
      _loadDumpButton.getParent().setLayoutData("spany 2");
      new ToolItem(_loadDumpButton.getParent(), SWT.SEPARATOR, 0);

      _searchDumpButton = createToolItem(parent, Images.SEARCH24, "   search   ", "Search the dump", "searchDumpButtonSelected");
      _searchDumpButton.getParent().setLayoutData("spany 2");
      //new ToolItem(_searchDumpButton.getParent(), SWT.NONE, 0);

      _repairDumpButton = createToolItem(parent, Images.REPAIR24, "   repair   ", "Try to repair the dump...", "repairDumpButtonSelected");
      _repairDumpButton.getParent().setLayoutData("spany 2, wrap");

      label = new Label(parent, SWT.NONE);
      label.setText("Full qualified class name");
      _className = new CCombo(parent, SWT.FLAT);
      _className.setLayoutData("w 30:100%:600, gapx 5, growx");
      _className.setFont(Fonts.CONSOLE_FONT);
      _className.setVisibleItemCount(20);
      OnEvent.addListener(_className, SWT.KeyUp).on(GUI.class, this).classNameKeyUp(null);
      _classNameErrorDecoration = new ControlDecoration(_className, SWT.LEFT | SWT.UP);
      _classNameErrorDecoration.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(FieldDecorationRegistry.DEC_ERROR).getImage());
      _classNameErrorDecoration.setMarginWidth(1);
      OnEvent.addListener(_className, SWT.Modify).on(GUI.class, this).classNameModified(null);
      OnEvent.addListener(_className, SWT.Selection).on(GUI.class, this).classNameSelected(null);
      String[] classNames = _prefs.getClassNames();
      if ( classNames.length > 0 ) {
         _className.setText(classNames[0]);
      }
      updateClassNames();
      _openClasspathDialog = createToolItem(parent, Images.JAR_INTO16, null, "Edit classpath...", "openClasspathDialog");
      _openClasspathDialog.getParent().setLayoutData("wrap");

      _openItem.setControl(parent);
   }

   private ToolItem createToolItem( Composite parent, Image image, String text, String tooltipText, String eventHandler ) {
      ToolBar toolBar = new ToolBar(parent, SWT.FLAT);
      ToolItem item = new ToolItem(toolBar, SWT.PUSH);
      if ( image != null ) {
         item.setImage(image);
      }
      if ( text != null ) {
         item.setText(text);
      }
      if ( tooltipText != null ) {
         item.setToolTipText(tooltipText);
      }
      item.addListener(SWT.Selection, new OnEvent(this, eventHandler, true));
      return item;
   }

   private void discoverIndexes() {
      List<IndexMeta> indexMetas = DumpIndex.discoverIndexes(_dump);
      for ( IndexMeta meta : indexMetas ) {
         if ( meta.getIndexType().equals(InfiniteGroupIndex.class.getSimpleName()) ) {
            try {
               _indexes.add(new MyInfiniteGroupIndex(_dump, meta.getFieldAccessorName()));
            }
            catch ( NoSuchFieldException e ) {
               _log.warn("Failed to open Index.", e);
            }
         }
      }
   }

   private void displayDumpStats() {
      long dumpsize = _dump.getDumpFile().length();
      String ds = getHumanReadableBytes(dumpsize);
      String indexMessage = "";
      if ( _indexes.size() > 0 ) {
         indexMessage = " Found " + _indexes.size() + " indexes for this dump.";
      }
      setStatus("Dump has a size of " + ds + " containing " + NumberFormat.getNumberInstance().format(_elementPositions.size()) + " elements." + indexMessage);
   }

   private String getHumanReadableBytes( long byteNumber ) {
      String ds;
      if ( byteNumber / (1024 * 1024 * 1024.0) >= 0.5 ) {
         ds = NumberFormat.getNumberInstance().format(byteNumber / (1024 * 1024 * 1024)) + " GB";
      } else if ( byteNumber / (1024 * 1024.0) >= 0.5 ) {
         ds = NumberFormat.getNumberInstance().format(byteNumber / (1024 * 1024)) + " MB";
      } else if ( byteNumber / (1024.0) >= 0.5 ) {
         ds = NumberFormat.getNumberInstance().format(byteNumber / (1024)) + " kB";
      } else {
         ds = NumberFormat.getNumberInstance().format(byteNumber) + " Bytes";
      }
      return ds;
   }

   private Object getLookupObject() {
      FieldAccessor fa = (FieldAccessor)_lookupField.getData("" + _lookupField.getSelectionIndex());

      String text = _lookupText.getText();
      if ( text.length() == 0 ) {
         return null;
      }

      try {
         if ( fa.getType().equals(int.class) || fa.getType().equals(Integer.class) ) {
            return Integer.parseInt(text);
         } else if ( fa.getType().equals(long.class) || fa.getType().equals(Long.class) ) {
            return Long.parseLong(text);
         } else {
            return text;
         }
      }
      catch ( NumberFormatException argh ) {
         // ignore
      }

      return null;
   }

   private void initFieldAccessors() {
      Class c = _class;
      List<Field> fields = new ArrayList<Field>();
      while ( c != Object.class ) {
         if ( !c.getSimpleName().equals("Externalizer") ) { // compare using name instead of class to beat the copy&paste pattern...
            for ( Field f : c.getDeclaredFields() ) {
               if ( Modifier.isStatic(f.getModifiers()) ) {
                  continue;
               }
               if ( Modifier.isTransient(f.getModifiers()) ) {
                  continue;
               }
               f.setAccessible(true); // enable access to the method - ...hackity hack
               fields.add(f);
            }
         }
         c = c.getSuperclass();
      }

      // sort fields by the value of @externalize (ascending)
      Collections.sort(fields, new Comparator<Field>() {

         public int compare( Field o1, Field o2 ) {

            int val1 = Integer.MAX_VALUE;
            int val2 = Integer.MAX_VALUE;

            externalize annotation1 = o1.getAnnotation(externalize.class);
            externalize annotation2 = o2.getAnnotation(externalize.class);

            if ( annotation1 != null ) {
               val1 = annotation1.value();
            }
            if ( annotation2 != null ) {
               val2 = annotation2.value();
            }

            return val1 < val2 ? -1 : (val1 == val2 ? 0 : 1);
         }
      });

      _accessors = new ArrayList<FieldAccessor>(fields.size());
      for ( Field field : fields ) {
         _accessors.add(new FieldFieldAccessor(field));
      }
   }

   private boolean isFieldIndexSearchable( FieldAccessor fa ) {
      Class c = fa.getType();
      return c.equals(int.class) || c.equals(Integer.class) || c.equals(long.class) || c.equals(Long.class) || c.equals(String.class);
   }

   private boolean loadDumpPreconditionsMet( Class c, File f ) {
      return f != null && f.exists() && c != null;
   }

   private boolean openDump() throws Exception {
      _class = lookupClass();
      _dumpFile = checkFileName();
      if ( loadDumpPreconditionsMet(_class, _dumpFile) ) {
         _prefs.saveBeanMetaData(_beanMetaData);

         _prefs.addDumpFileName(_dumpFile.getAbsolutePath());
         updateDumpFileNames();

         _prefs.addClassName(_class.getName().replaceAll("\\$", "."));
         updateClassNames();

         if ( _dump != null ) _dump.close();

         _dump = new Dump(_class, _dumpFile, Dump.SHARED_MODE);

         _openedDumps.add(new DumpFilesInfo(_dumpFile));

         return true;
      }

      return false;
   }

   private void updateClassNames() {
      String[] classNames = _prefs.getClassNames();
      _className.setItems(classNames);
   }

   private void updateDumpFileNames() {
      String[] dumpFileNames = _prefs.getDumpFileNames();
      _fileName.setItems(dumpFileNames);
   }


   static class BeanMetaData {

      Class _class;
      File  _dumpFile;


      public BeanMetaData( Class c, File dumpFile ) {
         _class = c;
         _dumpFile = dumpFile;
      }
   }

   static class DumpFilesInfo {

      File   _dumpFile;
      File[] _existingFiles;


      public DumpFilesInfo( File dumpFile ) {
         _dumpFile = dumpFile;
         _existingFiles = _dumpFile.getAbsoluteFile().getParentFile().listFiles(new FilenameFilter() {

            public boolean accept( File dir, String name ) {
               return name.startsWith(_dumpFile.getName());
            }
         });
      }

      public void clean() {
         Set<File> existingFiles = new HashSet<File>(Arrays.asList(_existingFiles));
         File[] f = _dumpFile.getAbsoluteFile().getParentFile().listFiles(new FilenameFilter() {

            public boolean accept( File dir, String name ) {
               return name.startsWith(_dumpFile.getName());
            }
         });
         for ( File file : f ) {
            if ( !existingFiles.contains(file) ) {
               if ( !file.delete() ) {
                  System.err.println("Failed to cleanup created file " + file);
               }
            }
         }
      }

      @Override
      public boolean equals( Object obj ) {
         if ( this == obj ) {
            return true;
         }
         if ( obj == null ) {
            return false;
         }
         if ( getClass() != obj.getClass() ) {
            return false;
         }
         DumpFilesInfo other = (DumpFilesInfo)obj;
         if ( _dumpFile == null ) {
            if ( other._dumpFile != null ) {
               return false;
            }
         } else if ( !_dumpFile.equals(other._dumpFile) ) {
            return false;
         }
         return true;
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + ((_dumpFile == null) ? 0 : _dumpFile.hashCode());
         return result;
      }
   }

   static class MyInfiniteGroupIndex extends InfiniteGroupIndex {

      MyInfiniteGroupIndex( Dump dump, FieldAccessor fieldAccessor ) {
         super(dump, fieldAccessor, 100000);
      }

      MyInfiniteGroupIndex( Dump dump, String fieldName ) throws NoSuchFieldException {
         super(dump, fieldName, 100000);
      }

      @Override
      protected long[] getPositions( int key ) {
         return super.getPositions(key);
      }

      @Override
      protected long[] getPositions( long key ) {
         return super.getPositions(key);
      }

      @Override
      protected long[] getPositions( Object key ) {
         return super.getPositions(key);
      }
   }
}
