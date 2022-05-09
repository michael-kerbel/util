package util.dumpass;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.custom.TableCursor;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import util.dump.reflection.FieldAccessor;
import util.swt.BooleanDialog;
import util.swt.ResourceManager;
import util.swt.event.OnEvent;


public class Editor {

   public static boolean ALLOW_EDITING     = false;
   public static boolean CONFIRM_DELETES   = true;

   final Table           _table;
   TableCursor           _cursor;
   ControlEditor         _editor;
   final GUI             _gui;
   int                   _lastCursorColumn = 0;

   public Editor( GUI gui, Table table ) {
      _gui = gui;
      _table = table;

      _cursor = new TableCursor(table, SWT.NONE);
      _cursor.setForeground(ResourceManager.getColor(SWT.COLOR_LIST_SELECTION_TEXT));
      _cursor.setBackground(ResourceManager.getColor(SWT.COLOR_LIST_SELECTION));

      _editor = new ControlEditor(_cursor);
      _editor.grabHorizontal = true;
      _editor.grabVertical = true;

      _cursor.addSelectionListener(new SelectionAdapter() {

         // when the user hits "ENTER" in the TableCursor, pop up a text editor so that 
         // they can change the text of the cell
         @Override
         public void widgetDefaultSelected( SelectionEvent e ) {
            edit();
         }

         // when the TableEditor is over a cell, select the corresponding row in 
         // the table
         @Override
         public void widgetSelected( SelectionEvent e ) {
            _table.setSelection(new TableItem[] { _cursor.getRow() });
         }
      });
      // Hide the TableCursor when the user hits the "CTRL" or "SHIFT" key.
      // This alows the user to select multiple items in the table.
      _cursor.addKeyListener(new KeyAdapter() {

         @Override
         public void keyPressed( KeyEvent e ) {
            if ( e.keyCode == SWT.CTRL || e.keyCode == SWT.SHIFT || (e.stateMask & SWT.CONTROL) != 0 || (e.stateMask & SWT.SHIFT) != 0 ) {
               hideCursor();
            }
            else if ( e.keyCode == SWT.DEL ) {
               if ( CONFIRM_DELETES ) {
                  BooleanDialog dialog = new BooleanDialog("Really delete?", "Do you really want to delete the selected rows from the dump?",
                     new Image[] { ResourceManager.getImage(SWT.ICON_QUESTION) });
                  if ( !dialog.getUserAnswer() ) {
                     return;
                  }
               }
               for ( TableItem row : _table.getSelection() ) {
                  int i = _table.indexOf(row);
                  _gui.getDumpElement(i);
                  _gui._dump.deleteLast();
               }
               _table.clearAll();
               hideCursor();
               _gui.setStatus("Deleted rows from dump.");
            }
         }
      });
      // When the user double clicks in the TableCursor, pop up a text editor so that 
      // they can change the text of the cell
      _cursor.addMouseListener(new MouseAdapter() {

         @Override
         public void mouseDoubleClick( MouseEvent e ) {
            edit();
         }
      });

      _cursor.addListener(SWT.MenuDetect, new OnEvent(_gui, "tableMenu"));

      // Show the TableCursor when the user releases the "SHIFT" or "CTRL" key.
      // This signals the end of the multiple selection task.
      _table.addKeyListener(new KeyAdapter() {

         @Override
         public void keyReleased( KeyEvent e ) {
            if ( e.keyCode == SWT.CONTROL && (e.stateMask & SWT.SHIFT) != 0 ) {
               return;
            }
            if ( e.keyCode == SWT.SHIFT && (e.stateMask & SWT.CONTROL) != 0 ) {
               return;
            }
            if ( e.keyCode != SWT.CONTROL && (e.stateMask & SWT.CONTROL) != 0 ) {
               return;
            }
            if ( e.keyCode != SWT.SHIFT && (e.stateMask & SWT.SHIFT) != 0 ) {
               return;
            }

            showCursor();
         }
      });

      _table.addListener(SWT.Selection, new Listener() {

         public void handleEvent( Event event ) {
            if ( _table.getSelectionCount() > 1 ) {
               hideCursor();
            }
            else {
               showCursor();
            }
         }
      });
   }

   private void edit() {
      final Text text = new Text(_cursor, SWT.NONE);
      //            text.setBackground(ResourceManager.getColor(SWT.COLOR_INFO_BACKGROUND));
      text.addListener(SWT.Paint, new Listener() {

         public void handleEvent( Event e ) {
            Point s = text.getSize();
            e.gc.setAlpha(100);
            e.gc.setForeground(ResourceManager.getColor(SWT.COLOR_DARK_GREEN));
            e.gc.drawRectangle(0, 0, s.x - 1, s.y - 1);
         }

      });
      TableItem row = _cursor.getRow();
      int column = _cursor.getColumn();
      text.setText(row.getText(column));
      text.addKeyListener(new KeyAdapter() {

         @Override
         public void keyPressed( KeyEvent e ) {
            // close the text editor and copy the data over 
            // when the user hits "ENTER"
            if ( e.character == SWT.CR ) {
               TableItem row = _cursor.getRow();
               int i = _table.indexOf(row);
               Object dumpElement = _gui.getDumpElement(i);
               int column = _cursor.getColumn();
               FieldAccessor fa = (FieldAccessor)_table.getColumn(column).getData();
               String t = text.getText();
               try {
                  if ( fa.getType() == int.class || fa.getType() == Integer.class ) {
                     int d = Integer.parseInt(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == long.class || fa.getType() == Long.class ) {
                     long d = Long.parseLong(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == byte.class || fa.getType() == Byte.class ) {
                     byte d = Byte.parseByte(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == boolean.class || fa.getType() == Boolean.class ) {
                     boolean d = Boolean.parseBoolean(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == double.class || fa.getType() == Double.class ) {
                     double d = Double.parseDouble(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == float.class || fa.getType() == Float.class ) {
                     float d = Float.parseFloat(t);
                     fa.set(dumpElement, d);
                  }
                  else if ( fa.getType() == String.class ) {
                     fa.set(dumpElement, t);
                  }
                  else {
                     throw new RuntimeException("Dumpbrowser doesn't support parsing this cell type: " + fa.getType());
                  }
                  row.setText(column, t);
                  long dumpSize = _gui._dump.getDumpSize();
                  _gui._dump.updateLast(dumpElement);
                  if ( _gui._dump.getDumpSize() != dumpSize ) {
                     _gui._elementPositions.set(i, dumpSize);
                  }
                  _table.clear(i);
                  _gui.setStatus("Updated row in dump.");
               }
               catch ( Exception argh ) {
                  _gui.setStatus("Failed to set cell value to '" + t + "': " + argh);
               }
               text.dispose();
            }
            // close the text editor when the user hits "ESC"
            if ( e.character == SWT.ESC ) {
               text.dispose();
            }
         }
      });
      // close the text editor when the user tabs away
      text.addFocusListener(new FocusAdapter() {

         @Override
         public void focusLost( FocusEvent e ) {
            text.dispose();
         }
      });
      _editor.setEditor(text);
      text.setFocus();
   }

   private void hideCursor() {
      _lastCursorColumn = _cursor.getColumn();
      _cursor.setVisible(false);
   }

   private void showCursor() {
      if ( !ALLOW_EDITING || _table.getSelectionCount() > 1 ) {
         return;
      }
      TableItem[] selection = _table.getSelection();
      TableItem row = (selection.length == 0) ? _table.getItem(_table.getTopIndex()) : selection[0];
      _table.showItem(row);
      _cursor.setSelection(row, _lastCursorColumn);
      _cursor.setVisible(true);
      _cursor.setFocus();
   }
}
