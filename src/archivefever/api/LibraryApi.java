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

    /** Text collected from a page of a book. @param page null when the reader left it out */
    public record Passage(Integer page,String text) {}

    private final ApiClient client;

    public LibraryApi(ApiClient client) {this.client=client;}

    public Scan createScan() throws IOException {return scan(client.post("/api/scans"));}

    public Scan scan(String id) throws IOException {return scan(client.get("/api/scans/"+id));}

    public Scan uploadPhoto(String id,String filename,byte[] photo) throws IOException {
        return scan(client.postFile("/api/scans/"+id+"/image","file",filename,photo));
    }

    public Scan cancelScan(String id) throws IOException {return scan(client.post("/api/scans/"+id+"/cancel"));}

    public ShelfBook confirmScan(String id) throws IOException {
        return shelfBook(client.post("/api/scans/"+id+"/confirm"));
    }

    public List<ShelfBook> library() throws IOException {
        List<ShelfBook> books=new ArrayList<>();
        for(Object entry:Json.list(client.get("/api/library/books")))books.add(shelfBook(entry));
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

    /** @param path a cover or spine address from one of the answers above */
    public BufferedImage image(String path) throws IOException {
        BufferedImage image=ImageIO.read(new ByteArrayInputStream(client.getBytes(path)));
        if(image==null)throw new IOException("Not an image: "+path);
        return image;
    }

    private static Scan scan(Object json) {
        Object book=Json.field(json,"book");
        return new Scan(Json.string(json,"id"),Json.string(json,"status"),Json.string(json,"reason"),
                book==null?null:new BookInfo(Json.string(book,"isbn13"),Json.string(book,"title"),
                        Json.string(book,"author"),Json.string(book,"publisher"),Json.string(book,"link"),
                        Json.string(book,"coverUrl"),Json.string(book,"spineUrl")));
    }

    private static Passage passage(Object json) {
        return new Passage(Json.field(json,"page") instanceof Number page?page.intValue():null,Json.string(json,"text"));
    }

    private static ShelfBook shelfBook(Object json) {
        return new ShelfBook(Json.string(json,"isbn13"),Json.string(json,"title"),Json.string(json,"author"),
                Json.string(json,"link"),Json.string(json,"spineUrl"),Json.integer(json,"spineWidth"));
    }
}
