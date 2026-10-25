
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
    public boolean equals(Object obj) {
        // Deux objets identiques sont forcément égaux
        if (this == obj) {
            return true;
        }

        // On vérifie que l'objet existe et qu'il est de type PageId
        if (!(obj instanceof PageId)) {
            return false;
        }

        PageId autre = (PageId) obj;

        // Deux pages sont égales si elles ont le même numéro
        return this.pageNumber == autre.pageNumber;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(pageNumber);
    }

    @Override
    public String toString() {
        return "PageId(" + pageNumber + ")";
    }
}
