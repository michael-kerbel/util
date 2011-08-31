package util.swt.viewerbuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;


public class DefaultViewerColumnSelectable implements ViewerColumnSelectable {
	protected ViewerBuilder builder;
	protected ViewerColumn[] allColumns;

	public DefaultViewerColumnSelectable(ViewerBuilder builder, ViewerColumn[] allColumns) {
		this.builder = builder;
		this.allColumns = allColumns;
	}

	public DefaultViewerColumnSelectable(ViewerBuilder builder) {
		this(builder, null);
	}

	public void setAllColumns(ViewerColumn[] allColumns) {
		this.allColumns = allColumns;
	}

	public ViewerColumn[] getAllColumns() {
		return allColumns;
	}

	public ViewerColumn[] getCurrentColumns() {
		List<ViewerColumn> currentColumns = new ArrayList<ViewerColumn> ();
		for (ViewerColumn col : allColumns) {
			if (col.isShow()) {
				currentColumns.add(col);
			}
		}
		return currentColumns.toArray(new ViewerColumn[0]);
	}

	public void setColumns(ViewerColumn[] currentColumns) {
		saveColumns();
		for (ViewerColumn viewerColumn : allColumns) {
			int i;
			for (i = 0; i < currentColumns.length; i++) {
				if (viewerColumn.equals(currentColumns[i])) {
					break;
				}
			}
			viewerColumn.setShow(i < currentColumns.length);
		}
		updateColumns();
	}

	public void saveColumns() {
		Map<String, Integer> mapOrders = new HashMap<String, Integer> ();
		int []orders = new int[0];
		if (builder instanceof TableBuilder) {
			Table table = ((TableBuilder) builder).getTableViewer().getTable();
			orders = table.getColumnOrder();
			for (int i = 0; i < orders.length; i++) {
				String id1 = "", id2 = "";
				TableColumn tCol = table.getColumn(i);
				id1 = (String) tCol.getData(ViewerBuilder.COLUMN_ID_KEY);
				id2 = (String) table.getColumn(orders[i]).getData(ViewerBuilder.COLUMN_ID_KEY);
				for (ViewerColumn col : allColumns) {
					if (id1.equals(col.getGetterName())) {
						col.setWidth(tCol.getWidth());
						mapOrders.put(id2, col.getOrder());
						break;
					}
				}
			}
			TableColumn sortColumn = table.getSortColumn();
			if (sortColumn != null) {
				table.setData(ViewerBuilder.SORT_COLUMN_ID_KEY, sortColumn.getData(ViewerBuilder.COLUMN_ID_KEY));
				table.setData(ViewerBuilder.SORT_DESC, (table.getSortDirection() == SWT.DOWN));
			}
		} else if (builder instanceof TreeBuilder) {
			Tree tree = ((TreeBuilder) builder).getTreeViewer().getTree();
			orders = tree.getColumnOrder();
			for (int i = 0; i < orders.length; i++) {
				String id1 = "", id2 = "";
				TreeColumn tCol = tree.getColumn(i);
				id1 = (String) tCol.getData(ViewerBuilder.COLUMN_ID_KEY);
				id2 = (String) tree.getColumn(orders[i]).getData(ViewerBuilder.COLUMN_ID_KEY);
				for (ViewerColumn col : allColumns) {
					if (id1.equals(col.getGetterName())) {
						col.setWidth(tCol.getWidth());
						mapOrders.put(id2, col.getOrder());
						break;
					}
				}
			}
			TreeColumn sortColumn = tree.getSortColumn();
			if (sortColumn != null) {
				tree.setData(ViewerBuilder.SORT_COLUMN_ID_KEY, sortColumn.getData(ViewerBuilder.COLUMN_ID_KEY));
				tree.setData(ViewerBuilder.SORT_DESC, (tree.getSortDirection() == SWT.DOWN));
			}
		}
		for (ViewerColumn col : allColumns) {
			if (mapOrders.containsKey(col.getGetterName())) {
				col.setOrder(mapOrders.get(col.getGetterName()));
			}
		}
	}

	private void updateColumns() {
		ViewerColumn []currentColumns = getCurrentColumns();
		List<ViewerColumn> orderedColumns = new ArrayList<ViewerColumn> ();
		
		for (ViewerColumn col : currentColumns) {
			int i = 0;
			for (i = 0; i < orderedColumns.size(); i++) {
				if (col.getOrder() < orderedColumns.get(i).getOrder()) {
					break;
				}
			}
			orderedColumns.add(i, col);
		}
		
		if (builder instanceof TableBuilder) {
			TableViewer tableViewer = ((TableBuilder) builder).getTableViewer();
			tableViewer.getTable().setRedraw(false);
			builder.reset();
			for (ViewerColumn col : orderedColumns) {
				builder.addColumn(col);
			}

			String sortColumnId = (String) tableViewer.getTable().getData(ViewerBuilder.SORT_COLUMN_ID_KEY);
			Boolean sortDesc = (Boolean) tableViewer.getTable().getData(ViewerBuilder.SORT_DESC);
			if (sortColumnId != null) {
				int i = 0;
				for (i = 0; i < tableViewer.getTable().getColumnCount(); i++) {
					TableColumn col = tableViewer.getTable().getColumn(i);
					if (sortColumnId.equals(col.getData(ViewerBuilder.COLUMN_ID_KEY))) {
						BuilderSorter sorter = (BuilderSorter) tableViewer.getSorter();
						sorter.setSortColumn(tableViewer, i, sortDesc);
						tableViewer.getTable().setSortColumn(col);
						tableViewer.getTable().setSortDirection(sortDesc ? SWT.DOWN : SWT.UP);
						break;
					}
				}
			}

			tableViewer.getTable().setRedraw(true);
			tableViewer.refresh();
		} else if (builder instanceof TreeBuilder) {
			TreeViewer treeViewer = ((TreeBuilder) builder).getTreeViewer();
			treeViewer.getTree().setRedraw(false);
			builder.reset();
			for (ViewerColumn col : orderedColumns) {
				builder.addColumn(col);
			}

			String sortColumnId = (String) treeViewer.getTree().getData(ViewerBuilder.SORT_COLUMN_ID_KEY);
			Boolean sortDesc = (Boolean) treeViewer.getTree().getData(ViewerBuilder.SORT_DESC);
			if (sortColumnId != null) {
				int i = 0;
				for (i = 0; i < treeViewer.getTree().getColumnCount(); i++) {
					TreeColumn col = treeViewer.getTree().getColumn(i);
					if (sortColumnId.equals(col.getData(ViewerBuilder.COLUMN_ID_KEY))) {
						BuilderSorter sorter = (BuilderSorter) treeViewer.getSorter();
						sorter.setSortColumn(treeViewer, i, sortDesc);
						treeViewer.getTree().setSortColumn(col);
						treeViewer.getTree().setSortDirection(sortDesc ? SWT.DOWN : SWT.UP);
						break;
					}
				}
			}

			treeViewer.getTree().setRedraw(true);
			treeViewer.refresh();
		} else {
			throw new IllegalStateException("unknown ViewerBuilder: " + builder);
		}
	}
}