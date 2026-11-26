package archivefever.api;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** The server's book registration and library calls. Blocking: call it off the event thread. */
public final class LibraryApi {
    /** @param link the book's YES24 page; YES24 requires showing it wherever the book appears */
    public record BookInfo(String isbn13,String title,String author,String publisher,String link,
                           String coverUrl,String spineUrl) {}

    /**
     * One attempt to register a book from a barcode photo.
     * @param status PENDING until the photo arrives, then RESOLVED (book is set), NO_BARCODE,
     *               NOT_FOUND, FAILED, CANCELLED or EXPIRED; CONFIRMED once added to the library
     */
    public record Scan(String id,String status,String reason,BookInfo book) {}

    /** @param spineWidth width in pixels of the spine at the shelf's 172 pixel spine height */
    public record ShelfBook(String isbn13,String title,String author,String link,String spineUrl,int spineWidth) {}

    /**
     * Text collected from a page of a book.
     * @param page null when the reader left it out
     * @param marks the reader's own marks on its sentences
     */
    public record Passage(long id,Integer page,String text,List<PassageMark> marks) {}

    /**
     * How the reader has marked one sentence of a passage.
     * @param sentence which sentence of the passage, counting from 0
     * @param note the memo; empty for none
     */
    public record PassageMark(int sentence,boolean underlined,boolean highlighted,String note) {}

    /** A book a search found. @param onShelf whether the reader has it already */
    public record FoundBook(String isbn13,String title,String author,String publisher,String link,boolean onShelf) {}

    /**
     * Someone signed in.
     * @param id also names their folder of reading notes
     * @param handle the name friends find them by, without the "@"
     */
    public record User(String id,String nickname,String handle) {}

    private final ApiClient client;

    public LibraryApi(ApiClient client) {this.client=client;}

    /**
     * Signs in by name alone, which the server allows while it has no Kakao key. Kakao
     * sign-in needs a browser and is done by the web page, not from here.
     */
    public User signInByName(String name) throws IOException {
        Object user=client.post("/api/auth/dev","{\"name\":"+Json.quote(name)+"}");
        return new User(Json.string(user,"id"),Json.string(user,"nickname"),Json.string(user,"handle"));
    }

    public void signOut() throws IOException {client.postForNothing("/api/auth/logout");}

    public Scan createScan() throws IOException {return scan(client.post("/api/scans"));}

    public Scan scan(String id) throws IOException {return scan(client.get("/api/scans/"+id));}

    public Scan uploadPhoto(String id,String filename,byte[] photo) throws IOException {
        return scan(client.postFile("/api/scans/"+id+"/image","file",filename,photo));
    }

    public Scan cancelScan(String id) throws IOException {return scan(client.post("/api/scans/"+id+"/cancel"));}

    public ShelfBook confirmScan(String id) throws IOException {
        return shelfBook(client.post("/api/scans/"+id+"/confirm"));
    }

    public List<ShelfBook> library() throws IOException {return shelf("/api/library/books");}

    /** The books at a shelf address: the user's own, a friend's, or those the two have in common. */
    public List<ShelfBook> shelf(String path) throws IOException {
        List<ShelfBook> books=new ArrayList<>();
        for(Object entry:Json.list(client.get(path)))books.add(shelfBook(entry));
        return books;
    }

    /** The passages collected from a shelf book, in reading order (by page). */
    public List<Passage> passages(String isbn13) throws IOException {
        List<Passage> passages=new ArrayList<>();
        for(Object entry:Json.list(client.get("/api/library/books/"+isbn13+"/passages")))passages.add(passage(entry));
        return passages;
    }

    public Passage addPassage(String isbn13,Integer page,String text) throws IOException {
        return passage(client.post("/api/library/books/"+isbn13+"/passages",
                "{\"page\":"+page+",\"text\":"+Json.quote(text)+"}"));
    }

    /** Rewrites a passage of the reader's own; a null page takes its page away. */
    public Passage updatePassage(String isbn13,long id,Integer page,String text) throws IOException {
        return passage(client.put("/api/library/books/"+isbn13+"/passages/"+id,
                "{\"page\":"+page+",\"text\":"+Json.quote(text)+"}"));
    }

    /** Deletes a passage of the reader's own, with the marks on it. */
    public void deletePassage(String isbn13,long id) throws IOException {
        client.deleteForNothing("/api/library/books/"+isbn13+"/passages/"+id);
    }

    /** Sets how the reader marks a sentence of a passage of their own from now on. */
    public void markPassage(String isbn13,long id,int sentence,boolean underlined,boolean highlighted,String note)
            throws IOException {
        client.postForNothing("/api/library/books/"+isbn13+"/passages/"+id+"/marks","{\"sentence\":"+sentence
                +",\"underlined\":"+underlined+",\"highlighted\":"+highlighted+",\"note\":"+Json.quote(note)+"}");
    }

    /** Books the catalogue finds for some words of a title or an author, best match first. */
    public List<FoundBook> searchBooks(String words) throws IOException {
        List<FoundBook> found=new ArrayList<>();
        for(Object entry:Json.list(client.get("/api/books?query="+java.net.URLEncoder.encode(words,java.nio.charset.StandardCharsets.UTF_8))))
            found.add(new FoundBook(Json.string(entry,"isbn13"),Json.string(entry,"title"),Json.string(entry,"author"),
                    Json.string(entry,"publisher"),Json.string(entry,"link"),Boolean.TRUE.equals(Json.field(entry,"onShelf"))));
        return found;
    }

    /** One book with its cover, looked up for the reader to confirm before adding it. */
    public BookInfo book(String isbn13) throws IOException {return bookInfo(client.get("/api/books/"+isbn13));}

    /** Puts a book found by searching on the reader's shelf. */
    public ShelfBook addBook(String isbn13) throws IOException {
        return shelfBook(client.post("/api/library/books","{\"isbn13\":"+Json.quote(isbn13)+"}"));
    }

    /** Takes a book off the reader's shelf, with what they collected from it. */
    public void removeBook(String isbn13) throws IOException {
        client.deleteForNothing("/api/library/books/"+isbn13);
    }

    /** @param path a cover or spine address from one of the answers above */
    public BufferedImage image(String path) throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(client.getBytes(path)));
        if(image==null)throw new IOException("Not an image: "+path);
        return image;
    }

    private static Scan scan(Object json) {
        Object book=Json.field(json,"book");
        return new Scan(Json.string(json,"id"),Json.string(json,"status"),Json.string(json,"reason"),
                book==null?null:bookInfo(book));
    }

    private static BookInfo bookInfo(Object book) {
        return new BookInfo(Json.string(book,"isbn13"),Json.string(book,"title"),
                Json.string(book,"author"),Json.string(book,"publisher"),Json.string(book,"link"),
                Json.string(book,"coverUrl"),Json.string(book,"spineUrl"));
    }

    private static Passage passage(Object json) {
        List<PassageMark> marks=new ArrayList<>();
        if(Json.field(json,"marks") instanceof List<?> listed)for(Object mark:listed)
            marks.add(new PassageMark(Json.integer(mark,"sentence"),Boolean.TRUE.equals(Json.field(mark,"underlined")),
                    Boolean.TRUE.equals(Json.field(mark,"highlighted")),
                    Json.string(mark,"note")==null?"":Json.string(mark,"note")));
        return new Passage(Json.field(json,"id") instanceof Number id?id.longValue():0,
                Json.field(json,"page") instanceof Number page?page.intValue():null,Json.string(json,"text"),marks);
    }

    private static ShelfBook shelfBook(Object json) {
        return new ShelfBook(Json.string(json,"isbn13"),Json.string(json,"title"),Json.string(json,"author"),
                Json.string(json,"link"),Json.string(json,"spineUrl"),Json.integer(json,"spineWidth"));
    }
}
