package aces.webctrl.bbmd.core;
import java.util.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.channels.*;
import com.controlj.green.core.email.*;
public class SavedData {
  private volatile static Path file;
  public static volatile List<Set<String>> groups = null;
  public static volatile String emailSubject = "";
  public static volatile String[] emailRecipients = new String[]{};
  public static volatile int failsBeforeNotify = 5;
  public static void init(Path file){
    SavedData.file = file;
    loadData();
  }
  public static boolean sendEmail(String message){
    final String emailSubject = SavedData.emailSubject;
    final String[] emailRecipients = SavedData.emailRecipients;
    if (emailRecipients.length==0 || emailSubject.isBlank()){
      return true;
    }
    try{
      EmailParametersBuilder pb = EmailServiceFactory.createParametersBuilder();
      pb.withSubject(emailSubject);
      pb.withToRecipients(emailRecipients);
      pb.withMessageContents(message);
      pb.withMessageMimeType("text/plain");
      EmailServiceFactory.getService().sendEmail(pb.build());
      return true;
    }catch(Throwable t){
      Initializer.log(t);
      return false;
    }
  }
  private final static int check(int x, int min, int max, int def){
    return x>=min&&x<=max?x:def;
  }
  private final static long check(long x, long min, long max, long def){
    return x>=min&&x<=max?x:def;
  }
  public static void setGroups(ArrayList<Set<String>> groups){
    if (groups==null){
      SavedData.groups = Collections.emptyList();
    }
    // If two sets have non-empty intersection, then combine them
    Set<String> x, y;
    for (int i=0,j;i<groups.size();++i){
      x = groups.get(i);
      if (x.size()>1){
        for (j=i+1;j<groups.size();++j){
          y = groups.get(j);
          for (String s: x){
            if (y.contains(s)){
              x.addAll(y);
              groups.remove(j);
              --j;
              break;
            }
          }
        }
      }else{
        groups.remove(i);
        --i;
      }
    }
    SavedData.groups = groups;
  }
  public static boolean loadData(){
    if (file==null){
      return false;
    }
    try{
      if (Files.exists(file)){
        byte[] arr;
        synchronized(SavedData.class){
          arr = Files.readAllBytes(file);
        }
        final SerializationStream s = new SerializationStream(arr);
        Initializer.manageBBMD = s.readBoolean();
        Initializer.checkInterval = check(s.readLong(), 30000L, 3600000L, Initializer.checkInterval);
        Router.pingTimeout = check(s.readInt(), 250, 10000, Router.pingTimeout);
        Snapshot.RETRIES = check(s.readInt(), 1, 20, Snapshot.RETRIES);
        Snapshot.TIMEOUT = check(s.readLong(), 100L, 5000L, Snapshot.TIMEOUT);
        Snapshot.MAX_TIME = check(s.readLong(), 3000L, 300000L, Snapshot.MAX_TIME);
        Fixer.RETRIES = check(s.readInt(), 1, 20, Fixer.RETRIES);
        Fixer.TIMEOUT = check(s.readLong(), 100L, 5000L, Fixer.TIMEOUT);
        Fixer.MAX_TIME = check(s.readLong(), 3000L, 300000L, Fixer.MAX_TIME);
        failsBeforeNotify = check(s.readInt(), 0, 1024, failsBeforeNotify);
        emailSubject = s.readString();
        emailRecipients = new String[check(s.readInt(), 0, 4096, 0)];
        for (int i=0;i<emailRecipients.length;++i){
          emailRecipients[i] = s.readString();
        }
        final int len = check(s.readInt(), 0, 4096, 0);
        ArrayList<Set<String>> g = new ArrayList<Set<String>>(len);
        Set<String> set;
        for (int i=0,j,k;i<len;++i){
          j = check(s.readInt(), 0, 4096, 0);
          set = new HashSet<String>();
          for (k=0;k<j;++k){
            set.add(s.readString());
          }
          if (set.size()>1){
            g.add(set);
          }
        }
        setGroups(g);
        if (!s.end()){
          Initializer.log("Data file corrupted. - Too big");
        }
      }else{
        groups = new ArrayList<Set<String>>();
      }
      return true;
    }catch(Throwable t){
      Initializer.log("Error occurred while loading data.");
      Initializer.log(t);
      return false;
    }
  }
  public static boolean saveData(){
    if (file==null){
      return false;
    }
    try{
      final SerializationStream s = new SerializationStream(1024, true);
      s.write(Initializer.manageBBMD);
      s.write(Initializer.checkInterval);
      s.write(Router.pingTimeout);
      s.write(Snapshot.RETRIES);
      s.write(Snapshot.TIMEOUT);
      s.write(Snapshot.MAX_TIME);
      s.write(Fixer.RETRIES);
      s.write(Fixer.TIMEOUT);
      s.write(Fixer.MAX_TIME);
      s.write(failsBeforeNotify);
      s.write(emailSubject);
      final String[] arr = emailRecipients;
      s.write(arr.length);
      for (int i=0;i<arr.length;++i){
        s.write(arr[i]);
      }
      List<Set<String>> groups = SavedData.groups;
      if (groups==null){
        s.write(0);
      }else{
        s.write(groups.size());
        for (Set<String> set: groups){
          s.write(set.size());
          for (String str: set){
            s.write(str);
          }
        }
      }
      final ByteBuffer buf = s.getBuffer();
      synchronized(SavedData.class){
        try(
          FileChannel out = FileChannel.open(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        ){
          while (buf.hasRemaining()){
            out.write(buf);
          }
        }
      }
      return true;
    }catch(Throwable t){
      Initializer.log(t);
      return false;
    }
  }
}