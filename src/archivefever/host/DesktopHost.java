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

    @Override public void editPassage(String isbn13,long id,Integer page,String text,Consumer<PassageResult> done) {
        JTextArea words=new JTextArea(text,6,30);
        words.setLineWrap(true);words.setWrapStyleWord(true);
        JTextField number=new JTextField(page==null?"":page.toString(),5);
        JPanel pageRow=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));
        pageRow.add(new JLabel("쪽수 (비워 둘 수 있음)  "));pageRow.add(number);
        JPanel form=new JPanel(new BorderLayout(0,8));
        form.add(new JScrollPane(words),BorderLayout.CENTER);form.add(pageRow,BorderLayout.SOUTH);
        String[] choices={"저장","삭제","취소"};
        int chosen=JOptionPane.showOptionDialog(null,form,"수집한 문장 고치기",JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,null,choices,choices[0]);
        if(chosen==1 && JOptionPane.showConfirmDialog(null,"이 문장과 여기에 남긴 밑줄·메모를 지울까요?","문장 삭제",
                JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)chosen=2;
        if(chosen!=0 && chosen!=1) {done.accept(PassageResult.CANCELLED);return;}
        boolean delete=chosen==1;
        Integer newPage;
        try {newPage=number.getText().isBlank()?null:Integer.valueOf(number.getText().strip());}
        catch(NumberFormatException e) {done.accept(PassageResult.FAILED);return;}
        String corrected=words.getText().strip();
        if(!delete && corrected.isEmpty()) {done.accept(PassageResult.NOTHING_PICKED);return;}
        Thread worker=new Thread(()->{
            PassageResult result;
            try {
                if(delete)api.deletePassage(isbn13,id);else api.updatePassage(isbn13,id,newPage,corrected);
                result=delete?PassageResult.DELETED:PassageResult.SAVED;
            } catch(IOException | RuntimeException e) {result=PassageResult.FAILED;}
            PassageResult outcome=result;
            SwingUtilities.invokeLater(()->done.accept(outcome));
        },"desktop-passage-edit");
        worker.setDaemon(true);worker.start();
    }

    @Override public void signIn(Consumer<String> done) {
        String typed=JOptionPane.showInputDialog(null,
                "로그인할 이름 (영문 소문자·숫자 2~20자)",
                "Archive Fever 로그인",JOptionPane.PLAIN_MESSAGE);
        if(typed==null || typed.isBlank())return;
        Thread worker=new Thread(()->{
            try {
                LibraryApi.User user=api.signInByName(typed.strip());
                SwingUtilities.invokeLater(()->{
                    // Marks made while reading are kept apart for each person on this computer.
                    System.setProperty("archivefever.notes.dir",java.nio.file.Path.of(System.getProperty("user.home"),
                            ".archivefever","notes",user.id()).toString());
                    // Shown in the profile, for telling friends who to look for.
                    System.setProperty("archivefever.handle",user.handle()==null?"":user.handle());
                    done.accept(user.nickname());
                });
            } catch(IOException | RuntimeException e) {
                // The desktop can only sign in by name; a server set up for Kakao refuses that.
                SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,
                        "로그인하지 못했습니다. 이름을 확인하거나, "
                        +"서버가 카카오 로그인만 받는지 확인해 주세요.\n("+e.getMessage()+")",
                        "Archive Fever",JOptionPane.WARNING_MESSAGE));
            }
        },"desktop-sign-in");
        worker.setDaemon(true);worker.start();
    }

    @Override public void signOut() {
        Thread worker=new Thread(()->{
            // Offline, the session simply runs out on the server by itself.
            try {api.signOut();} catch(IOException | RuntimeException ignored) {}
        },"desktop-sign-out");
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
