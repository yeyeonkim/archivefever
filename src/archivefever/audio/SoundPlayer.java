package archivefever.audio;

import archivefever.ui.theme.AppTheme;
import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Plays a bundled sound off the EDT, releasing its audio line when finished. */
public final class SoundPlayer implements AutoCloseable {
    private final String resource;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"archivefever-ui-sound");thread.setDaemon(true);return thread;
    });
    private final AtomicReference<Clip> current=new AtomicReference<>();
    private final AtomicBoolean closed=new AtomicBoolean();

    public SoundPlayer(String resource){this.resource=resource;}
    public void play(){
        if(closed.get())return;
        worker.execute(()->{
            Clip previous=current.getAndSet(null);
            if(previous!=null){previous.stop();previous.close();}
            Clip clip=null;
            try(AudioInputStream audio=openAudioStream(resource)){
                clip=AudioSystem.getClip();clip.open(audio);
                if(closed.get()){clip.close();return;}
                Clip playing=clip;
                clip.addLineListener(event->{
                    if(event.getType()==LineEvent.Type.STOP && playing.getLongFramePosition()>=playing.getFrameLength()){
                        current.compareAndSet(playing,null);playing.close();
                    }
                });
                current.set(clip);clip.start();
            }catch(Exception e){
                if(clip!=null){current.compareAndSet(clip,null);clip.close();}
                System.getLogger(SoundPlayer.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not play UI sound: "+resource,e);
            }
        });
    }
    /** Convert the supplied 24-bit WAV to widely supported signed 16-bit PCM. */
    public static AudioInputStream openAudioStream(String resource)throws IOException,UnsupportedAudioFileException{
        AudioInputStream source=AudioSystem.getAudioInputStream(new BufferedInputStream(AppTheme.resource(resource)));
        AudioFormat format=source.getFormat();
        AudioFormat pcm=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,format.getSampleRate(),16,
                format.getChannels(),format.getChannels()*2,format.getSampleRate(),false);
        if(!AudioSystem.isConversionSupported(pcm,format)){
            source.close();throw new IOException("Unsupported sound format: "+format);
        }
        try{return AudioSystem.getAudioInputStream(pcm,source);}
        catch(RuntimeException e){source.close();throw e;}
    }
    @Override public void close(){
        closed.set(true);worker.shutdownNow();
        Clip clip=current.getAndSet(null);if(clip!=null){clip.stop();clip.close();}
    }
}
