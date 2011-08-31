package util.swt.event;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import org.eclipse.swt.SWT;


public enum SWTEvent {

   Activate(SWT.Activate), //
   Arm(SWT.Arm), //
   Close(SWT.Close), //
   Collapse(SWT.Collapse), //
   Deactivate(SWT.Deactivate), //
   DefaultSelection(SWT.DefaultSelection), //
   Deiconify(SWT.Deiconify), //
   Dispose(SWT.Dispose), //
   DragDetect(SWT.DragDetect), //
   EraseItem(SWT.EraseItem), //
   Expand(SWT.Expand), //
   FocusIn(SWT.FocusIn), //
   FocusOut(SWT.FocusOut), //
   HardKeyDown(SWT.HardKeyDown), //
   HardKeyUp(SWT.HardKeyUp), //
   Help(SWT.Help), //
   Hide(SWT.Hide), //
   Iconify(SWT.Iconify), //
   KeyDown(SWT.KeyDown), //
   KeyUp(SWT.KeyUp), //
   MeasureItem(SWT.MeasureItem), //
   MenuDetect(SWT.MenuDetect), //
   Modify(SWT.Modify), //
   MouseDoubleClick(SWT.MouseDoubleClick), //
   MouseDown(SWT.MouseDown), //
   MouseEnter(SWT.MouseEnter), //
   MouseExit(SWT.MouseExit), //
   MouseHover(SWT.MouseHover), //
   MouseMove(SWT.MouseMove), //
   MouseUp(SWT.MouseUp), //
   MouseWheel(SWT.MouseWheel), //
   Move(SWT.Move), //
   Paint(SWT.Paint), //
   PaintItem(SWT.PaintItem), //
   Resize(SWT.Resize), //
   Selection(SWT.Selection), //
   SetData(SWT.SetData), //
   Show(SWT.Show), //
   Traverse(SWT.Traverse), //
   Verify(SWT.Verify);

   private static TIntObjectMap<SWTEvent> _typeEvents = new TIntObjectHashMap<SWTEvent>();

   static {
      for ( SWTEvent e : values() ) {
         _typeEvents.put(e._type, e);
      }
   }


   public static SWTEvent forType( int type ) {
      return _typeEvents.get(type);
   }


   private final int _type;


   private SWTEvent( int type ) {
      _type = type;
   }

   public int getType() {
      return _type;
   }
}
