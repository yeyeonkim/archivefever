package archivefever.host;

import archivefever.api.LibraryApi;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Desktop stand-in for the phone: a photo is picked from disk instead of taken. */
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

    @Override public void openUrl(String url) {
        try {
            if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));
        } catch(IOException | RuntimeException e) {
            // No browser available; the link simply does nothing.
        }
    }
}
