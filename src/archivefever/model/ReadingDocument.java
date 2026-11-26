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
    /**
     * Where the marks of a document are kept when that is not this device's own file: on the
     * server, for the reader's record of a book and for a record shared in a book swap.
     */
    public interface MarkStore {
        /**
         * Keeps a sentence as it is marked from now on: underlined or not, highlighted or
         * not, and its memo (empty for none). When this fails, nothing has changed.
         */
        void save(int sentence,Mark mark) throws IOException;
        /** Why nothing more can be marked, in words for the reader; null while it can. */
        default String closed() {return null;}
    }
    private final String text;
    private final List<Sentence> sentences;
    /** This device's own file of marks for the book; not read when the marks were handed in, not written when there is a store. */
    private final Path file;
    private final MarkStore store;
    private Properties data=new Properties();
    private String loadWarning="";

    /** A document whose marks are kept in a file on this device. */
    public ReadingDocument(String bookId,String text) {this(bookId,text,null,null);}

    /**
     * A document whose marks are kept elsewhere.
     * @param marks the marks as they are kept there now, by sentence; null to read this
     *              device's own file for the book instead
     * @param store told of every change, which counts only once the store has taken it;
     *              null to keep changes in this device's own file
     */
    public ReadingDocument(String bookId,String text,Map<Integer,Mark> marks,MarkStore store) {
        this.text=text;this.store=store;
        sentences=split(text);
        file=localFile(bookId);
        if(marks!=null) {
            marks.forEach((sentence,mark)->{
                if(sentence<0 || sentence>=sentences.size())return;
                if(mark.underlined())data.setProperty(sentence+".underline","true");
                if(mark.highlighted())data.setProperty(sentence+".highlight","true");
                if(!mark.note().isBlank()) {
                    data.setProperty(sentence+".memo","true");data.setProperty(sentence+".note",mark.note().strip());
                }
            });
            data.setProperty("text",text);
        } else if(Files.exists(file))try(InputStream in=Files.newInputStream(file)) {
            data.load(in);
            if(!text.equals(data.getProperty("text")))data=carriedOver(data);
        } catch(IOException | IllegalArgumentException e){
            loadWarning="저장된 메모를 읽지 못했습니다.";data=new Properties();
        }
    }
    /** This device's own file of marks for a book. */
    private static Path localFile(String bookId) {
        try {
            String name=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(bookId.getBytes(StandardCharsets.UTF_8)));
            return Path.of(System.getProperty("archivefever.notes.dir",
                    Path.of(System.getProperty("user.home"),".archivefever","notes").toString()),name+".properties");
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    /** Whether this device has a file of marks for the book, from when marks were kept on the device. */
    public static boolean hasLocalMarks(String bookId) {return Files.exists(localFile(bookId));}
    /** Removes this device's file of marks for the book, once what it held is kept elsewhere. */
    public static void forgetLocalMarks(String bookId) throws IOException {Files.deleteIfExists(localFile(bookId));}
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
    /** Why nothing can be marked on this document any more, in words for the reader; null while it can. */
    public String closed(){return store==null?null:store.closed();}
    public Mark mark(int index) {check(index);return mark(data,index);}
    private static Mark mark(Properties marks,int index) {
        String key=index+".";
        return new Mark(Boolean.parseBoolean(marks.getProperty(key+"underline")),
                Boolean.parseBoolean(marks.getProperty(key+"highlight")),
                Boolean.parseBoolean(marks.getProperty(key+"memo")),marks.getProperty(key+"note",""));
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
        Properties next=new Properties();next.putAll(data);next.setProperty("text",text);
        next.setProperty(index+"."+field,value);
        if(field.equals("note"))next.setProperty(index+".memo",Boolean.toString(!value.isBlank()));
        if(store!=null) {
            // Opening the memo box marks nothing yet; any other change counts once the store has it.
            if(!field.equals("memo"))store.save(index,mark(next,index));
            data=next;
            return;
        }
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
