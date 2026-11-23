package archivefever.host;

import archivefever.api.LibraryApi;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Desktop stand-in for the phone: a barcode photo is picked from disk instead of taken,
 * and a passage is typed in instead of read off a page photo.
 */
public final class DesktopHost implements HostBridge {
    private final LibraryApi api;

    public DesktopHost(LibraryApi api) {this.api=api;}

    @Override public void startScan(String scanId) {
        SwingUtilities.invokeLater(()->{
            JFileChooser chooser=new JFileChooser();
            chooser.setDialogTitle("바코드 사진 선택");
            chooser.setFileFilter(new FileNameExtensionFilter("사진 (jpg, png)","jpg","jpeg","png"));
            File photo=chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION?chooser.getSelectedFile():null;
            Thread worker=new Thread(()->send(scanId,photo),"desktop-scan-upload");
            worker.setDaemon(true);worker.start();
        });
    }

    private void send(String scanId,File photo) {
        try {
            if(photo==null)api.cancelScan(scanId);
            else api.uploadPhoto(scanId,photo.getName(),Files.readAllBytes(photo.toPath()));
        } catch(IOException e) {
            // The UI is polling the scan; cancelling tells it to stop waiting for a photo.
            try {api.cancelScan(scanId);} catch(IOException ignored) {}
        }
    }

    @Override public void savePassage(String isbn13,Consumer<PassageResult> done) {
        JTextArea text=new JTextArea(6,30);
        text.setLineWrap(true);text.setWrapStyleWord(true);
        JTextField page=new JTextField(5);
        JPanel pageRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        pageRow.add(new JLabel("쪽수 (비워 둘 수 있음)  "));pageRow.add(page);
        JPanel form=new JPanel(new BorderLayout(0,8));
        form.add(new JScrollPane(text),BorderLayout.CENTER);form.add(pageRow,BorderLayout.SOUTH);
        if(JOptionPane.showConfirmDialog(null,form,"수집할 문장 입력",JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) {done.accept(PassageResult.CANCELLED);return;}
        String passage=text.getText().strip();
        if(passage.isEmpty()) {done.accept(PassageResult.NOTHING_PICKED);return;}
        Integer number;
        try {number=page.getText().isBlank()?null:Integer.valueOf(page.getText().strip());}
        catch(NumberFormatException e) {done.accept(PassageResult.FAILED);return;}
        Thread worker=new Thread(()->{
            PassageResult result;
            try {api.addPassage(isbn13,number,passage);result=PassageResult.SAVED;}
            catch(IOException | RuntimeException e) {result=PassageResult.FAILED;}
            PassageResult outcome=result;
            SwingUtilities.invokeLater(()->done.accept(outcome));
        },"desktop-passage-save");
        worker.setDaemon(true);worker.start();
    }

    @Override public void openUrl(String url) {
        try {
            if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));
        } catch(IOException | RuntimeException e) {
            // No browser available; the link simply does nothing.
        }
    }
}
