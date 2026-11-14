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

        add(action("read.","책 읽기 선택",105,433,() -> {
            if(selectedBook!=null) this.onRead.accept(selectedBook);
        }));
        add(action("scan.","책 스캔 선택",218,433,()->{
            if(selectedBook!=null)this.onScan.accept(selectedBook);
        }));

    }

    public void setBook(BookSpine book) {
        selectedBook=Objects.requireNonNull(book);
    }

    private static JButton action(String text,String accessibleName,int x,int y,Runnable onDismiss) {
        JButton button=new JButton("<html><u>"+text+"</u></html>");
        button.setBounds(x,y,90,38);
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
