package archivefever.ui.components;

import archivefever.ui.navigation.Destination;
import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public final class BookmarkNavigation extends JPanel {
    private final Map<Destination,BookmarkButton> bookmarks=new EnumMap<>(Destination.class);
    private Destination currentDestination;
    private final Map<Destination,Rectangle> restBounds=new EnumMap<>(Destination.class);

    public BookmarkNavigation(Consumer<Destination> navigate) {
        setOpaque(false);setLayout(null);
        // Add back-to-front using the same stacking order as the Figma layers.
        addBookmark(new BookmarkButton(Destination.FRIEND,"friend",154,836,246,407,
                221.848,394.262,-3.59,246,863,Color.WHITE,new String[]{"friend"},false,navigate));
        addBookmark(new BookmarkButton(Destination.ADD_BOOK,"add-book",58,786,92,274,
                73.803,269.929,-3.82,96,866,Color.WHITE,new String[]{"add","book"},true,navigate));
        addBookmark(new BookmarkButton(Destination.ARCHIVE,"archive",115,815,149,266,
                149,266,180,161,836,Color.BLACK,new String[]{"My","Book","Archive"},false,navigate));
        addBookmark(new BookmarkButton(Destination.BOOK_SWAP,"book-swap",230,822,192,284,
                163.198,267.704,-173.69,334,867,Color.BLACK,new String[]{"Book","swap"},false,navigate));
        addBookmark(new BookmarkButton(Destination.MENU,"menu",-165,795,309,364,
                234.685,312.807,-164.76,23,856,Color.BLACK,new String[]{"menu"},false,navigate));
    }

    private void addBookmark(BookmarkButton button) {
        bookmarks.put(button.destination(),button);
        restBounds.put(button.destination(),new Rectangle(button.getBounds()));
        add(button,0);
    }

    public void setDestination(Destination destination) {
        if(currentDestination==destination) return;
        currentDestination=destination;
        bookmarks.forEach((bookmarkDestination,button) -> {
            Rectangle bounds=new Rectangle(restBounds.get(bookmarkDestination));
            if(destination==Destination.MENU && bookmarkDestination==Destination.MENU)bounds.y-=49;
            if(destination==Destination.ADD_BOOK && bookmarkDestination==Destination.ADD_BOOK)bounds.y-=69;
            if((destination==Destination.MENU || destination==Destination.ADD_BOOK)
                    && bookmarkDestination==Destination.ARCHIVE)bounds.y+=18;
            button.setBounds(bounds);
            if(bookmarkDestination!=destination) button.resetLift();
        });
    }

    public void setSentenceCaptureLayout(boolean active){
        Rectangle bounds=new Rectangle(restBounds.get(Destination.ARCHIVE));
        if(active)bounds.y-=20;
        else if(currentDestination==Destination.MENU || currentDestination==Destination.ADD_BOOK)bounds.y+=18;
        bookmarks.get(Destination.ARCHIVE).setBounds(bounds);
    }

    @Override public boolean contains(int x,int y) {
        for(Component button:getComponents())
            if(button.contains(x-button.getX(),y-button.getY())) return true;
        return false;
    }
}
