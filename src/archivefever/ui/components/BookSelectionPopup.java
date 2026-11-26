package archivefever.ui.components;

import archivefever.model.BookSpine;
import archivefever.ui.theme.AppFonts;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.Consumer;

/** Modal book action chooser shown above the archive screen. */
public final class BookSelectionPopup extends PaperPopup {
    private static final Rectangle PAPER_BOUNDS=new Rectangle(51,360,311,141);
    private final Consumer<BookSpine> onRead;
    private final Consumer<BookSpine> onScan;
    private BookSpine selectedBook;

    public BookSelectionPopup(Consumer<BookSpine> onRead,Runnable onDismiss){
        this(onRead,book->onDismiss.run(),onDismiss);
    }

    public BookSelectionPopup(Consumer<BookSpine> onRead,Consumer<BookSpine> onScan,Runnable onDismiss) {
        this(onRead,onScan,null,onDismiss);
    }

    /** @param onRemove takes the book off the shelf, once the reader has said they mean it; null for no such choice */
    public BookSelectionPopup(Consumer<BookSpine> onRead,Consumer<BookSpine> onScan,Consumer<BookSpine> onRemove,
                              Runnable onDismiss) {
        super(PAPER_BOUNDS,onDismiss);
        this.onRead=Objects.requireNonNull(onRead);
        this.onScan=Objects.requireNonNull(onScan);
        setOpaque(false);
        setLayout(null);
        setFocusable(true);

        JLabel title=new JLabel("what’s next?",SwingConstants.CENTER);
        title.setFont(AppFonts.typewriter(24));
        title.setBounds(51,389,311,42);
        add(title);

        add(action("read.","책 읽기 선택",105,433,90,() -> {
            if(selectedBook!=null) this.onRead.accept(selectedBook);
        }));
        add(action("scan.","책 스캔 선택",218,433,90,()->{
            if(selectedBook!=null)this.onScan.accept(selectedBook);
        }));
        // Under the two things to do with a book, and smaller: the one that undoes having it.
        if(onRemove!=null) {
            JButton remove=action("remove from shelf","책 빼기 선택",116,468,180,26,()->{
                if(selectedBook!=null)onRemove.accept(selectedBook);
            });
            remove.setFont(AppFonts.typewriter(15));remove.setForeground(new Color(0x5A5048));
            add(remove);
        }

    }

    public void setBook(BookSpine book) {
        selectedBook=Objects.requireNonNull(book);
    }

    private static JButton action(String text,String accessibleName,int x,int y,int width,Runnable onDismiss) {
        return action(text,accessibleName,x,y,width,38,onDismiss);
    }

    private static JButton action(String text,String accessibleName,int x,int y,int width,int height,Runnable onDismiss) {
        JButton button=new JButton("<html><u>"+text+"</u></html>");
        button.setBounds(x,y,width,height);
        button.setFont(AppFonts.typewriter(24));
        button.setForeground(Color.BLACK);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.getAccessibleContext().setAccessibleName(accessibleName);
        button.addActionListener(e -> onDismiss.run());
        return button;
    }

}
