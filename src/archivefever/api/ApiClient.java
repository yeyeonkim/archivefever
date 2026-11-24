package archivefever.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP access to the Archive Fever server. Blocking: call it off the event thread.
 * Uses HttpURLConnection because that is what the browser JVM maps onto fetch.
 */
public final class ApiClient {
    private static final String SESSION_COOKIE="AF_SESSION";
    private final String base;
    /**
     * The session cookie the server set at sign-in, sent back with every request. Only on
     * the desktop: in the browser JVM the browser keeps the cookie and adds it itself, and
     * does not show it to the page.
     */
    private volatile String session;

    /** @param base server address without a path, e.g. http://localhost:8080 */
    public ApiClient(String base) {
        this.base=base.endsWith("/")?base.substring(0,base.length()-1):base;
    }

    public Object get(String path) throws IOException {
        return Json.parse(text(read(open(path,"GET"))));
    }

    /** POST without a body; the server's actions take everything from the path. */
    public Object post(String path) throws IOException {
        HttpURLConnection connection=open(path,"POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        connection.getOutputStream().close();
        return Json.parse(text(read(connection)));
    }

    public Object post(String path,String json) throws IOException {
        byte[] body=json.getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection=open(path,"POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","application/json");
        connection.setFixedLengthStreamingMode(body.length);
        try(OutputStream out=connection.getOutputStream()) {out.write(body);}
        return Json.parse(text(read(connection)));
    }

    /** POST whose answer has no content, such as signing out. */
    public void postForNothing(String path) throws IOException {
        HttpURLConnection connection=open(path,"POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        connection.getOutputStream().close();
        read(connection);
    }

    public Object postFile(String path,String field,String filename,byte[] content) throws IOException {
        String boundary="----archivefever"+Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field+"\"; filename=\""
                +filename.replace("\"","")+"\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpURLConnection connection=open(path,"POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);
        connection.setFixedLengthStreamingMode(body.size());
        try(OutputStream out=connection.getOutputStream()) {body.writeTo(out);}
        return Json.parse(text(read(connection)));
    }

    public byte[] getBytes(String path) throws IOException {
        return read(open(path,"GET"));
    }

    private HttpURLConnection open(String path,String method) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(base+path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5_000);
        // Uploading a photo includes the server's barcode reading and book lookup.
        connection.setReadTimeout(30_000);
        connection.setRequestProperty("Accept","application/json, image/png");
        String cookie=session;
        if(cookie!=null)connection.setRequestProperty("Cookie",SESSION_COOKIE+"="+cookie);
        return connection;
    }

    private byte[] read(HttpURLConnection connection) throws IOException {
        try {
            int status=connection.getResponseCode();
            rememberSession(connection);
            if(status<200 || status>=300) {
                InputStream error=connection.getErrorStream();
                if(error!=null)try(error){error.readAllBytes();}
                throw new IOException("HTTP "+status);
            }
            try(InputStream in=connection.getInputStream()) {return in.readAllBytes();}
        } finally {connection.disconnect();}
    }

    /** Signing in sets the session cookie and signing out clears it. */
    private void rememberSession(HttpURLConnection connection) {
        java.util.List<String> cookies=connection.getHeaderFields().get("Set-Cookie");
        if(cookies==null)return;
        for(String cookie:cookies) {
            if(!cookie.startsWith(SESSION_COOKIE+"="))continue;
            int end=cookie.indexOf(';');
            String value=cookie.substring(SESSION_COOKIE.length()+1,end<0?cookie.length():end);
            session=value.isEmpty()?null:value;
        }
    }

    private static String text(byte[] bytes) {return new String(bytes,StandardCharsets.UTF_8);}
}
