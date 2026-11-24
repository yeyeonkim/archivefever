package archivefever.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader for the server's answers, so the client needs no library.
 * Objects become Map, arrays List, numbers Double; strings, booleans and null map directly.
 */
public final class Json {
    private final String text;
    private int at;

    private Json(String text) {this.text=text;}

    public static Object parse(String text) {
        Json reader=new Json(text);
        Object value=reader.value();
        reader.skipSpace();
        if(reader.at!=text.length())throw reader.error("unexpected text after the value");
        return value;
    }

    /** Field of an object as text, or null when absent or JSON null. */
    public static String string(Object object,String field) {
        Object value=field(object,field);
        return value==null?null:value.toString();
    }

    public static int integer(Object object,String field) {
        Object value=field(object,field);
        if(!(value instanceof Number number))throw new IllegalArgumentException("Not a number: "+field);
        return number.intValue();
    }

    /** Nested object, or null when absent or JSON null. */
    public static Object field(Object object,String field) {
        if(!(object instanceof Map<?,?> map))throw new IllegalArgumentException("Not a JSON object");
        return map.get(field);
    }

    public static List<?> list(Object value) {
        if(!(value instanceof List<?> list))throw new IllegalArgumentException("Not a JSON array");
        return list;
    }

    /** The text as a JSON string literal, quotes included. */
    public static String quote(String text) {
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            switch(c) {
                case '"'->out.append("\\\"");
                case '\\'->out.append("\\\\");
                case '\n'->out.append("\\n");
                case '\r'->out.append("\\r");
                case '\t'->out.append("\\t");
                default->{if(c<0x20)out.append(String.format("\\u%04x",(int)c));else out.append(c);}
            }
        }
        return out.append('"').toString();
    }

    private Object value() {
        skipSpace();
        if(at>=text.length())throw error("unexpected end");
        char c=text.charAt(at);
        if(c=='{')return object();
        if(c=='[')return array();
        if(c=='"')return string();
        if(text.startsWith("true",at)){at+=4;return Boolean.TRUE;}
        if(text.startsWith("false",at)){at+=5;return Boolean.FALSE;}
        if(text.startsWith("null",at)){at+=4;return null;}
        return number();
    }

    private Map<String,Object> object() {
        Map<String,Object> result=new LinkedHashMap<>();
        at++;skipSpace();
        if(peek('}')){at++;return result;}
        while(true) {
            skipSpace();
            if(!peek('"'))throw error("expected a field name");
            String name=string();
            skipSpace();expect(':');
            result.put(name,value());
            skipSpace();
            if(peek(',')){at++;continue;}
            expect('}');return result;
        }
    }

    private List<Object> array() {
        List<Object> result=new ArrayList<>();
        at++;skipSpace();
        if(peek(']')){at++;return result;}
        while(true) {
            result.add(value());
            skipSpace();
            if(peek(',')){at++;continue;}
            expect(']');return result;
        }
    }

    private String string() {
        StringBuilder out=new StringBuilder();
        at++;
        while(true) {
            if(at>=text.length())throw error("unterminated string");
            char c=text.charAt(at++);
            if(c=='"')return out.toString();
            if(c!='\\'){out.append(c);continue;}
            if(at>=text.length())throw error("unterminated escape");
            char escaped=text.charAt(at++);
            switch(escaped) {
                case 'n'->out.append('\n');
                case 'r'->out.append('\r');
                case 't'->out.append('\t');
                case 'b'->out.append('\b');
                case 'f'->out.append('\f');
                case 'u'->{
                    if(at+4>text.length())throw error("short unicode escape");
                    out.append((char)Integer.parseInt(text.substring(at,at+4),16));at+=4;
                }
                default->out.append(escaped);
            }
        }
    }

    private Double number() {
        int start=at;
        while(at<text.length() && "+-0123456789.eE".indexOf(text.charAt(at))>=0)at++;
        if(start==at)throw error("unexpected character");
        try {return Double.valueOf(text.substring(start,at));}
        catch(NumberFormatException e) {throw error("bad number");}
    }

    private void skipSpace() {while(at<text.length() && Character.isWhitespace(text.charAt(at)))at++;}
    private boolean peek(char c) {return at<text.length() && text.charAt(at)==c;}
    private void expect(char c) {if(!peek(c))throw error("expected '"+c+"'");at++;}
    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid JSON at "+at+": "+message);
    }
}
