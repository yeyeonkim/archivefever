package archivefever.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.text.BreakIterator;
import java.util.*;

/** Sentence-level annotations, kept separately from screen coordinates. */
public final class ReadingDocument {
    public record Sentence(int start,int end) {}
    public record Mark(boolean underlined,boolean highlighted,boolean memo,String note) {}
    /** Where the memos of a record shared with someone else are kept: with them, not only on this device. */
    public interface NoteStore {
        /** Saves the memo of a sentence, or takes it away when the note is blank. */
        void save(int sentence,String note) throws IOException;
        /** Why no more memos can be written, in words for the reader; null while they can. */
        default String closed() {return null;}
    }
    private final String text;
    private final List<Sentence> sentences;
    private final Path file;
    private final NoteStore noteStore;
    private Properties data=new Properties();
    private String loadWarning="";

    public ReadingDocument(String bookId,String text) {this(bookId,text,null,null);}

    /**
     * A document whose memos live in a note store. Underlines and highlights stay on this
     * device, as for any other document.
     * @param notes the memos as the store has them now, by sentence; they replace whatever
     *              this device remembers. Null for a document without a store.
     * @param noteStore told of every memo saved or removed, before this device records it;
     *                  when it fails, the memo counts as not saved
     */
    public ReadingDocument(String bookId,String text,Map<Integer,String> notes,NoteStore noteStore) {
        this.text=text;this.noteStore=noteStore;
        sentences=split(text);
        try {
            String name=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bookId.getBytes(StandardCharsets.UTF_8)));
            file=Path.of(System.getProperty("archivefever.notes.dir",
                    Path.of(System.getProperty("user.home"),".archivefever","notes").toString()),name+".properties");
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        if(Files.exists(file))try(InputStream in=Files.newInputStream(file)) {
            data.load(in);
            if(!text.equals(data.getProperty("text")))data=carriedOver(data);
        } catch(IOException | IllegalArgumentException e){
            loadWarning="저장된 메모를 읽지 못했습니다.";data=new Properties();
        }
        if(notes!=null) {
            for(String key:List.copyOf(data.stringPropertyNames()))
                if(key.endsWith(".memo") || key.endsWith(".note"))data.remove(key);
            notes.forEach((sentence,note)->{
                if(sentence<0 || sentence>=sentences.size() || note.isBlank())return;
                data.setProperty(sentence+".memo","true");data.setProperty(sentence+".note",note.strip());
            });
        }
    }
    /** Where the sentences of a text are, as a document of that text numbers them. */
    public static List<Sentence> sentencesOf(String text) {return split(text);}
    private static List<Sentence> split(String text) {
        List<Sentence> ranges=new ArrayList<>();
        BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.KOREAN);
        // A line break always ends a sentence: collected passages, and the page notes above
        // them, are lines of their own even when they do not end in a full stop.
        for(int lineStart=0;lineStart<text.length();) {
            int lineEnd=text.indexOf('\n',lineStart);
            if(lineEnd<0)lineEnd=text.length();
            iterator.setText(text.substring(lineStart,lineEnd));
            int start=iterator.first();
            for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next())
                ranges.add(new Sentence(lineStart+start,lineStart+end));
            lineStart=lineEnd+1;
        }
        return List.copyOf(ranges);
    }
    /**
     * The text has changed since the marks were saved, as it does when another passage is
     * collected from the book. Each mark follows its sentence to where that sentence is now;
     * marks of sentences that are gone are dropped.
     */
    private Properties carriedOver(Properties saved) {
        Properties next=new Properties();
        String savedText=saved.getProperty("text");
        if(savedText==null)return next;
        // The same sentence can occur twice: the n-th occurrence then stays the n-th.
        Map<String,Deque<Integer>> places=new HashMap<>();
        for(int i=0;i<sentences.size();i++)
            places.computeIfAbsent(words(text,sentences.get(i)),key->new ArrayDeque<>()).add(i);
        List<Sentence> before=split(savedText);
        for(int i=0;i<before.size();i++) {
            Deque<Integer> same=places.get(words(savedText,before.get(i)));
            if(same==null || same.isEmpty())continue;
            int now=same.poll();
            for(String field:new String[]{"underline","highlight","memo","note"}) {
                String value=saved.getProperty(i+"."+field);
                if(value!=null)next.setProperty(now+"."+field,value);
            }
        }
        next.setProperty("text",text);
        return next;
    }
    private static String words(String text,Sentence sentence) {
        return text.substring(sentence.start(),sentence.end()).strip();
    }
    public String text(){return text;}
    public List<Sentence> sentences(){return sentences;}
    public String loadWarning(){return loadWarning;}
    /** Why memos can no longer be written or changed on this document, in words for the reader; null while they can. */
    public String memosClosed(){return noteStore==null?null:noteStore.closed();}
    public Mark mark(int index) {
        check(index);String key=index+".";
        return new Mark(Boolean.parseBoolean(data.getProperty(key+"underline")),
                Boolean.parseBoolean(data.getProperty(key+"highlight")),
                Boolean.parseBoolean(data.getProperty(key+"memo")),data.getProperty(key+"note",""));
    }
    public void underline(int index)throws IOException {update(index,"underline",Boolean.toString(!mark(index).underlined()));}
    public void highlight(int index)throws IOException {update(index,"highlight",Boolean.toString(!mark(index).highlighted()));}
    public void enableMemo(int index)throws IOException {update(index,"memo","true");}
    public void saveMemo(int index,String note)throws IOException {update(index,"note",note.strip());}
    public void deleteMemo(int index)throws IOException {saveMemo(index,"");}
    private void check(int index){if(index<0 || index>=sentences.size())throw new IndexOutOfBoundsException(index);}
    private void update(int index,String field,String value)throws IOException {
        check(index);
        if(value.equals(data.getProperty(index+"."+field)) && text.equals(data.getProperty("text"))
                && (!field.equals("note") || mark(index).memo()==!value.isBlank()))return;
        if(field.equals("note") && noteStore!=null)noteStore.save(index,value);
        Properties next=new Properties();next.putAll(data);next.setProperty("text",text);
        next.setProperty(index+"."+field,value);
        if(field.equals("note"))next.setProperty(index+".memo",Boolean.toString(!value.isBlank()));
        Files.createDirectories(file.getParent());
        Path temporary=Files.createTempFile(file.getParent(),"notes-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temporary)){next.store(out,"Archive Fever reading annotations");}
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            data=next;
        } finally{Files.deleteIfExists(temporary);}
    }
}
