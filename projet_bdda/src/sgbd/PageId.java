package sgbd;

public class PageId implements IPageId {
	 private int pageNumber;

	    public PageId(int pageNumber) {
	        this.pageNumber = pageNumber;
	    }

	    public int getPageNumber() {
	        return pageNumber;
	    }

	    @Override
	    public String toString() {
	        return "PageId(" + pageNumber + ")";
	    }
}
