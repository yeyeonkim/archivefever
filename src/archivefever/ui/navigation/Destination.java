package archivefever.ui.navigation;

public enum Destination {
    MENU("menu"), ADD_BOOK("add book"), ARCHIVE("My Book Archive"),
    FRIEND("friend"), BOOK_SWAP("Book swap");

    private final String label;
    Destination(String label) { this.label = label; }
    public String label() { return label; }
}
