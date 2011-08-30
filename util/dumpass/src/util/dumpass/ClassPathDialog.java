package util.dumpass;

import net.miginfocom.swt.MigLayout;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import util.swt.BorderPainter;
import util.swt.DialogShell;
import util.swt.ResourceManager;
import util.swt.event.OnEvent;


public class ClassPathDialog extends DialogShell {

   private final GuiPrefs _prefs;
   private TableViewer    _classPathElements;

   public ClassPathDialog( GuiPrefs prefs ) {
      super(SWT.CANCEL);
      _prefs = prefs;
   }

   @Override
   public void cancel() {}

   @Override
   public void createContent( Composite parent ) {
      _cancel.setToolTipText("Close this dialog");

      Composite c = new Composite(parent, SWT.NONE);
      c.setLayoutData(new GridData(GridData.FILL_BOTH));
      c.setLayout(new MigLayout("fill", "", ""));

      _classPathElements = new TableViewer(c, SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
      Table table = _classPathElements.getTable();
      table.setBackground(ResourceManager.getColor(SWT.COLOR_WHITE));
      table.setData(BorderPainter.KEY_DRAW_BORDER, Boolean.FALSE);
      table.setLayoutData("wmin 0, growx, growy");
      _classPathElements.setContentProvider(new ArrayContentProvider());
      _classPathElements.setInput(_prefs.getClasspathElements());

      ToolBar addRemoveToolbar = new ToolBar(c, SWT.FLAT | SWT.VERTICAL);
      ToolItem addDir = new ToolItem(addRemoveToolbar, SWT.PUSH);
      addDir.setImage(Images.ADD_DIR16);
      addDir.setToolTipText("Add a classpath directory...");
      addDir.addListener(SWT.Selection, new OnEvent(this, "addDir", false));

      ToolItem addJar = new ToolItem(addRemoveToolbar, SWT.PUSH);
      addJar.setImage(Images.ADD_JAR16);
      addJar.setToolTipText("Add a jar...");
      addJar.addListener(SWT.Selection, new OnEvent(this, "addJar", false));

      ToolItem remove = new ToolItem(addRemoveToolbar, SWT.PUSH);
      remove.setImage(Images.MINUS16);
      remove.setToolTipText("Remove selected entry");
      remove.addListener(SWT.Selection, new OnEvent(this, "remove", false));
   }

   @Override
   public String getDescriptionText() {
      return "You can add class directories or jars to the class path here, to find your bean class easily. "
         + "Old locations are remembered and stay in the class path. You can remove them with the appropriate button.";
   }

   @Override
   public Image[] getImages() {
      return Images.SHELL_IMAGES;
   }

   @Override
   public String getTitle() {
      return "Edit class path";
   }

   @Override
   public int getWidth() {
      return 500;
   }

   @Override
   public void ok() {}

   void addDir() {
      DirectoryDialog dialog = new DirectoryDialog(_shell, SWT.NONE);
      dialog.setText("Select a classpath directory");
      String path = dialog.open();
      if ( path != null ) {
         _prefs.addClasspathElement(path);
         _classPathElements.add(path);
      }
   }

   void addJar() {
      FileDialog dialog = new FileDialog(_shell, SWT.OPEN | SWT.MULTI);
      dialog.setText("Select a jar");
      dialog.setFilterExtensions(new String[] { "*.jar" });
      dialog.open();
      String filterPath = dialog.getFilterPath();
      for ( String path : dialog.getFileNames() ) {
         _prefs.addClasspathElement(filterPath + "/" + path);
         _classPathElements.add(filterPath + "/" + path);
      }
   }

   void remove() {
      IStructuredSelection selection = (IStructuredSelection)_classPathElements.getSelection();
      if ( selection.isEmpty() ) return;

      String path = (String)selection.getFirstElement();
      _prefs.removeClasspathElement(path);
      _classPathElements.remove(path);
   }

}
