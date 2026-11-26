package archivefever.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collected passages laid out as the one text of a reading page, and the way from the
 * sentences of that text back to the passages. Marks are kept by passage and by sentence
 * within the passage; the page numbers its sentences through the whole text.
 */
public final class PassagePage {
    /**
     * @param page the page of the book it was collected from; null when the reader left it out
     * @param heading a line of its own above the passage, such as its page; null for none
     */
    public record Passage(long id,Integer page,String heading,String text) {}

    private final String text;
    private final List<Passage> passages;
    /** For each sentence of the text: which passage it is of (an index, or -1 for a heading), and which sentence of that passage. */
    private final int[] passageOf,placeIn;
    private final Map<String,Integer> sentenceAt=new HashMap<>();

    /** @param passages in the order they are to be read */
    public PassagePage(List<Passage> passages) {
        this.passages=List.copyOf(passages);
        StringBuilder page=new StringBuilder();
        int[] starts=new int[passages.size()],ends=new int[passages.size()];
        for(int i=0;i<passages.size();i++) {
            Passage passage=passages.get(i);
            if(page.length()>0)page.append("\n\n");
            if(passage.heading()!=null)page.append(passage.heading()).append('\n');
            starts[i]=page.length();
            page.append(passage.text());
            ends[i]=page.length();
        }
        text=page.toString();
        List<ReadingDocument.Sentence> sentences=ReadingDocument.sentencesOf(text);
        passageOf=new int[sentences.size()];placeIn=new int[sentences.size()];
        for(int i=0;i<sentences.size();i++) {
            passageOf[i]=-1;
            int start=sentences.get(i).start();
            for(int p=0;p<passages.size();p++) {
                if(start<starts[p] || start>=ends[p])continue;
                passageOf[i]=p;
                placeIn[i]=i>0 && passageOf[i-1]==p?placeIn[i-1]+1:0;
                sentenceAt.put(passages.get(p).id()+"/"+placeIn[i],i);
            }
        }
    }

    public String text() {return text;}

    public boolean isEmpty() {return passages.isEmpty();}

    /** The passage a sentence of the text is of; null for a heading line. */
    public Passage passageOf(int sentence) {
        return sentence<0 || sentence>=passageOf.length || passageOf[sentence]<0?null:passages.get(passageOf[sentence]);
    }

    /** Which sentence of its passage a sentence of the text is, counting from 0. */
    public int placeIn(int sentence) {return placeIn[sentence];}

    /** The sentence of the text that is the given sentence of a passage; null when the passage has no such sentence (any more). */
    public Integer sentenceAt(long passageId,int place) {return sentenceAt.get(passageId+"/"+place);}
}
