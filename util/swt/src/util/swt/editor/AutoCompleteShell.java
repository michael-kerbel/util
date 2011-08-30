package util.swt.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.miginfocom.swt.MigLayout;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;

import util.swt.ResourceManager;
import util.swt.event.OnEvent;


class AutoCompleteShell {

   static final Image     KEYWORD16  = ResourceManager.getImage(AutoCompleteShell.class, "keyword-16.png");
   static final Image     TEMPLATE16 = ResourceManager.getImage(AutoCompleteShell.class, "template-16.gif");
   static final Image     TEXT16     = ResourceManager.getImage(AutoCompleteShell.class, "text-16.png");

   private EditorHelper   _editorHelper;
   private StyledText     _editor;
   private Display        _display;
   private List<Template> _templatePropositions;
   private List<String>   _wordPropositions;
   private List<Keyword>  _keywordPropositions;
   private Table          _table;
   private TableViewer    _tableViewer;
   private int            _caretPos;
   private String         _word;

   Shell                  _shell;


   public AutoCompleteShell( EditorHelper editorHelper, int pos, String w, List<Template> templates, List<String> words, List<Keyword> keywords ) {
      _editorHelper = editorHelper;
      _caretPos = pos;
      _word = w;
      _keywordPropositions = keywords;
      _editor = _editorHelper._editor;
      _display = _editor.getDisplay();
      _templatePropositions = templates;
      _wordPropositions = words;
      _keywordPropositions = keywords;

   }

   public void keyDown( Event e ) {
      if ( e.character == SWT.ESC ) {
         _shell.dispose();
      }
      if ( e.keyCode == SWT.PAGE_DOWN ) {
         int selectionIndex = _table.getSelectionIndex();
         int itemCount = _table.getItemCount();
         int itemsPerPage = _table.getSize().y / _table.getItemHeight() - 1;

         int targetIndex = Math.min(itemCount - 1, selectionIndex + itemsPerPage);
         _table.setSelection(targetIndex);
      }
      if ( e.keyCode == SWT.PAGE_UP ) {
         int selectionIndex = _table.getSelectionIndex();
         int itemsPerPage = _table.getSize().y / _table.getItemHeight() - 1;

         int targetIndex = Math.max(0, selectionIndex - itemsPerPage);
         _table.setSelection(targetIndex);
      }
      if ( e.keyCode == SWT.ARROW_DOWN ) {
         int selectionIndex = _table.getSelectionIndex();
         int itemCount = _table.getItemCount();
         if ( selectionIndex + 1 < itemCount ) {
            _table.setSelection(selectionIndex + 1);
         } else {
            _table.setSelection(0);
         }
      }
      if ( e.keyCode == SWT.ARROW_UP ) {
         int selectionIndex = _table.getSelectionIndex();
         if ( selectionIndex > 0 ) {
            _table.setSelection(selectionIndex - 1);
         } else {
            _table.setSelection(_table.getItemCount() - 1);
         }
      }
      if ( e.keyCode == SWT.CR ) {
         expand();
      }
   }

   public void open() {
      _shell = new Shell(_editor.getShell(), SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS | SWT.RESIZE);
      _shell.setLayout(new MigLayout("ins 0"));
      OnEvent.addListener(_shell, SWT.Dispose).on(getClass(), this).shellDisposed(null);

      _tableViewer = new TableViewer(_shell, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
      _table = _tableViewer.getTable();
      _table.setHeaderVisible(false);
      _table.setLinesVisible(false);
      _table.setLayoutData("wmin 10, h 10:200:, grow, push");
      OnEvent.addListener(_table, SWT.MouseDoubleClick).on(getClass(), this).mouseDoubleClicked(null);

      _tableViewer.setContentProvider(new ArrayContentProvider());

      List input = new ArrayList();
      input.addAll(_templatePropositions);
      input.addAll(_wordPropositions);
      input.addAll(_keywordPropositions);
      Collections.sort(input, new Comparator() {

         public int compare( Object o1, Object o2 ) {
            int sortWeight1 = getSortWeight(o1);
            int sortWeight2 = getSortWeight(o2);
            if ( sortWeight1 != sortWeight2 ) return (sortWeight1 < sortWeight2 ? 1 : (sortWeight1 == sortWeight2 ? 0 : -1));
            return o1.toString().compareToIgnoreCase(o2.toString());
         }
      });
      _tableViewer.setInput(input);
      _tableViewer.setSelection(new StructuredSelection(input.get(0)));

      _tableViewer.setLabelProvider(new AutoCompleteLabelProvider());

      _shell.layout();
      Point loc = _editor.getLocationAtOffset(_editor.getCaretOffset());
      loc.y += _editor.getLineHeight();
      setBounds(_shell.computeSize(300, SWT.DEFAULT), _editor.toDisplay(loc));
      _shell.setVisible(true);
   }

   protected int getSortWeight( Object o ) {
      if ( o instanceof Template ) return 2;
      if ( o instanceof Keyword ) return 1;
      return 0;
   }

   void mouseDoubleClicked( Event e ) {
      expand();
   }

   void shellDisposed( Event e ) {
      _editorHelper._autoCompleteShell = null;
   }

   private void expand() {
      IStructuredSelection selection = (IStructuredSelection)_tableViewer.getSelection();
      Object element = selection.getFirstElement();
      if ( element instanceof String ) {
         _editor.replaceTextRange(_caretPos - _word.length(), _word.length(), (String)element);
         _editor.setCaretOffset(_caretPos + ((String)element).length() - _word.length());
      } else if ( element instanceof Template ) {
         _editorHelper.expandTemplate(_editor.getText(), _editor.getCaretOffset(), ((Template)element)._handle, _word);
      } else if ( element instanceof Keyword ) {
         _editorHelper.expandKeyword(_editor.getText(), _editor.getCaretOffset(), ((Keyword)element)._handle, _word);
      }
      _shell.dispose();
   }

   private Monitor getMonitor() {
      Point editorLocation = _editor.toDisplay(_editor.getLocation());
      for ( Monitor m : _display.getMonitors() ) {
         if ( m.getBounds().contains(editorLocation) ) {
            return m;
         }
      }
      return _display.getPrimaryMonitor();
   }

   private void setBounds( Point size, Point loc ) {
      Monitor m = getMonitor();
      Rectangle clientArea = m.getClientArea();
      Rectangle bounds = new Rectangle(Math.max(loc.x, clientArea.x), Math.max(loc.y, clientArea.y), size.x, size.y);
      if ( (!clientArea.contains(bounds.x, bounds.y + bounds.height) || // 
      !clientArea.contains(bounds.x + bounds.width, bounds.y + bounds.height)) ) {
         if ( clientArea.x > bounds.x ) {
            bounds.x += clientArea.x - bounds.x;
         } else if ( clientArea.x + clientArea.width < bounds.x + bounds.width ) {
            bounds.x -= bounds.x + bounds.width - clientArea.x - clientArea.width;
         }
         if ( clientArea.y > bounds.y ) {
            bounds.y += clientArea.y - bounds.y;
         } else if ( clientArea.y + clientArea.height < bounds.y + bounds.height ) {
            bounds.y -= bounds.y + bounds.height - clientArea.y - clientArea.height;
         }
      }
      _shell.setBounds(bounds);
   }


   private static class AutoCompleteLabelProvider extends StyledCellLabelProvider {

      @Override
      public void update( ViewerCell cell ) {
         Object element = cell.getElement();
         String label;
         if ( element instanceof Template ) {
            Template t = (Template)element;
            label = t._handle + " - " + t._name;
            StyleRange style = new StyleRange(t._handle.length(), label.length() - t._handle.length(), ResourceManager.getColor(SWT.COLOR_DARK_GRAY), null);
            cell.setStyleRanges(new StyleRange[] { style });
            cell.setImage(TEMPLATE16);
         } else if ( element instanceof Keyword ) {
            Keyword k = (Keyword)element;
            label = k._handle + " - " + k._description;
            StyleRange style = new StyleRange(k._handle.length(), label.length() - k._handle.length(), ResourceManager.getColor(SWT.COLOR_DARK_GRAY), null);
            cell.setStyleRanges(new StyleRange[] { style });
            cell.setImage(KEYWORD16);
         } else {
            label = element.toString();
            cell.setImage(TEXT16);
         }
         cell.setText(label);

         super.update(cell);
      }
   }
}
