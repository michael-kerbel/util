
package util.swt.viewerbuilder;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import util.swt.DialogShell;
import util.swt.ResourceManager;
import util.swt.SWTUtils;


public class ViewerColumnSelector extends DialogShell implements Listener {

   private static final Image[]   IMAGES          = new Image[] { ResourceManager.getImage(SWT.ICON_QUESTION) };

   private static final Color     COLOR_SELECTION = ResourceManager.getColor(0xff, 0xff, 0xa0);

   private ViewerColumnSelectable _selectable;
   private ViewerColumn[]         _allColumns;
   private Button[]               _buttons;
   private String                 _title          = "Tabellenspalten anpassen";
   private String                 _description    = "Hier können die Spalten der Tabelle angepasst werden. " + //
                                                     "Nur Spalten, die hier mit einem Häckchen versehen sind, werden angezeigt. " + //
                                                     "Der Tabellen-Filter berücksichtigt jeweils nur die angezeigten Spalten.";
   private ViewerColumn[]         _columns;

   private Rectangle[]            _positions;

   private Button                 _selectedButton;
   private Display                _display;


   public ViewerColumnSelector( ViewerColumnSelectable selectable ) {
      _selectable = selectable;
      _allColumns = _selectable.getAllColumns();
   }

   public ViewerColumnSelector( ViewerColumnSelectable selectable, String title, String description ) {
      _selectable = selectable;
      _title = title;
      _description = description;
      _allColumns = _selectable.getAllColumns();
   }

   @Override
   public void cancel() {}

   @Override
   public void createContent( Composite parent ) {
      ViewerColumn[] columns = _selectable.getCurrentColumns();
      _buttons = new Button[_allColumns.length];
      for ( int i = 0, length = _allColumns.length; i < length; i++ ) {
         ViewerColumn col = _allColumns[i];
         _buttons[i] = new Button(parent, SWT.CHECK | SWT.FLAT);
         _buttons[i].setData(col);
         _buttons[i].setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.FILL_HORIZONTAL));
         boolean checked = false;
         for ( ViewerColumn element : columns ) {
            if ( element.equals(col) ) {
               checked = true;
            }
         }
         _buttons[i].setSelection(checked);
         _buttons[i].setText(col.getCaption());
      }
      //      _buttons[0].setEnabled(false);

      _display = parent.getDisplay();
      _display.addFilter(SWT.MouseMove, this);
      parent.addListener(SWT.Dispose, this);
      parent.addListener(SWT.Resize, this);
      parent.addListener(SWT.Move, this);
      _display.timerExec(100, new Runnable() {

         public void run() {
            if ( !_shell.isDisposed() ) {
               markButton();
               _display.timerExec(100, this);
            }
         }
      });
   }

   @Override
   public String getDescriptionText() {
      return _description;
   }

   @Override
   public Image[] getImages() {
      return IMAGES;
   }

   @Override
   public String getTitle() {
      return _title;
   }

   @Override
   public int getWidth() {
      return 300;
   }

   public void handleEvent( Event e ) {
      if ( e.type == SWT.Dispose ) {
         _display.removeFilter(SWT.MouseMove, this);
      }
      else if ( e.type == SWT.Resize | e.type == SWT.Move ) {
         _positions = null;
      }
      else if ( e.type == SWT.MouseMove ) {
         markButton();
      }
   }

   @Override
   public void ok() {
      int number = 0;
      for ( Button b : _buttons ) {
         if ( b.getSelection() ) {
            number++;
         }
      }

      _columns = new ViewerColumn[number];
      int i = 0;
      for ( Button b : _buttons ) {
         if ( b.getSelection() ) {
            _columns[i++] = (ViewerColumn)b.getData();
         }
      }

   }

   @Override
   public void okPostDispose() {
      _selectable.setColumns(_columns);
   }

   private void initPositions() {
      _positions = new Rectangle[_allColumns.length];
      for ( int i = 0, length = _buttons.length; i < length; i++ ) {
         _positions[i] = SWTUtils.toDisplay(_buttons[i].getParent(), _buttons[i].getBounds());
      }
   }

   private void markButton() {
      if ( _positions == null ) {
         initPositions();
      }
      Point cursor = _display.getCursorLocation();
      boolean buttonFound = false;
      for ( int i = 0, length = _positions.length; i < length; i++ ) {
         if ( _positions[i].contains(cursor) ) {
            setSelectedButton(_buttons[i]);
            buttonFound = true;
         }
      }
      if ( !buttonFound ) {
         setSelectedButton(null);
      }
   }

   private void setSelectedButton( Button button ) {
      if ( _selectedButton == button ) {
         return;
      }
      if ( _selectedButton != null ) {
         _selectedButton.setBackground(null);
      }
      if ( button != null ) {
         button.setBackground(COLOR_SELECTION);
      }
      _selectedButton = button;
   }


}



