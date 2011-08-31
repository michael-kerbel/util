   
package util.swt.viewerbuilder;

class FacadedCollectionTableColumnMeta extends CollectionTableColumnMeta {

	GetterFacade facade;
	
	public FacadedCollectionTableColumnMeta(GetterFacade facade) {
		this.facade = facade;
	}

	public Object invoke(Object obj) {
		if (obj instanceof GetterFacade) {
			return super.invoke(obj);
		}
		facade.setValueObject(obj);
		return super.invoke(facade);
	}
	
	public String toString(Object obj) {
		if (obj instanceof GetterFacade) {
			return super.toString(obj);
		}
		facade.setValueObject(obj);
		return super.toString(facade);
	}

	public Comparable toComparable(Object obj) {
		if (obj instanceof GetterFacade) {
			return super.toComparable(obj);
		}
		facade.setValueObject(obj);
		return super.toComparable(facade);
	}

}



