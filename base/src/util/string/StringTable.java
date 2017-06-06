package util.string;

import static util.string.StringFormattingTool.centerTab;
import static util.string.StringFormattingTool.leftTab;
import static util.string.StringFormattingTool.replicate;
import static util.string.StringFormattingTool.rightTab;


/** 
 * <code>StringTable</code> is a class utilizing {@link StringFormattingTool} to create tables with Strings assuming proportional fonts.
 * Example:
 * <pre>
 *  id   | name | value
 * ------+------+------
 *     1 | Joe  | abcd 
 *     2 | Hans | bc   
 *     3 | Gabi | cdef 
 *     4 | Bill | m$   
 * </pre> 
 * */
public class StringTable {

   public static void main( String[] args ) {
      StringTable table = new StringTable(new Col("id", Alignment.Right, 5), new Col("name", Alignment.Left, 4), new Col("value", Alignment.Left, 5));
      table.addRow("1", "Joe", "abcd");
      table.addRow("2", "Hans", "bc");
      table.addRow("3", "Gabi", "cdef");
      table.addRow("4", "Bill", "m$");
      System.err.println(table);
   }


   private final Col[]         _cols;
   private final StringBuilder _s = new StringBuilder();


   public StringTable( Col... cols ) {
      _cols = cols;
      for ( Col col : cols ) {
         if ( _s.length() > 0 ) {
            _s.append(" | ");
         }
         _s.append(centerTab(col._header, col._width));
      }
      _s.append('\n');

      addHr();
   }

   public void addHr() {
      for ( int i = 0, length = _cols.length; i < length; i++ ) {
         if ( i > 0 ) {
            _s.append("-+-");
         }
         _s.append(replicate('-', _cols[i]._width));
      }
      _s.append('\n');
   }

   public void addRow( String... rowCells ) {
      if ( rowCells == null ) {
         throw new RuntimeException("rowCells Parameter may not be null.");
      }
      if ( rowCells.length > _cols.length ) {
         throw new RuntimeException("more rowCells than columns: " + rowCells.length + " > " + _cols.length);
      }
      for ( int i = 0, length = rowCells.length; i < length; i++ ) {
         if ( i > 0 ) {
            _s.append(" | ");
         }
         _s.append(_cols[i].format(rowCells[i]));
      }
      _s.append('\n');
   }

   @Override
   public String toString() {
      return _s.toString();
   }


   public enum Alignment {
      Left, Right, Center;
   }

   public static class Col {

      private final String    _header;
      private final Alignment _alignment;
      private final int       _width;


      public Col( String header, Alignment alignment, int width ) {
         _header = header;
         _alignment = alignment;
         _width = width;
      }

      public String format( String s ) {
         switch ( _alignment ) {
         case Center:
            return centerTab(s, _width);
         case Left:
            return leftTab(s, _width);
         case Right:
            return rightTab(s, _width);
         }
         throw new RuntimeException();
      }
   }
}
