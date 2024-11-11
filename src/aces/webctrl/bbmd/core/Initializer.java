package aces.webctrl.bbmd.core;
import javax.servlet.*;
import java.nio.file.*;
import com.controlj.green.addonsupport.*;
import com.controlj.green.core.main.*;
public class Initializer implements ServletContextListener {
  /** Contains basic information about this addon */
  public volatile static AddOnInfo info = null;
  /** The name of this addon */
  private volatile static String name;
  /** Prefix used for constructing relative URL paths */
  private volatile static String prefix;
  /** Path to the private directory for this addon */
  private volatile static Path root;
  /** Logger for this addon */
  private volatile static FileLogger logger;
  /** Primary processing thread */
  private volatile Thread mainThread = null;
  /** Whether the primary thread is active */
  private volatile boolean running = false;
  /** Whether to stop the primary thread */
  private volatile boolean stop = false;
  /** Specifies how often to run the BBMD checker */
  public static volatile long checkInterval = 60000L;
  /** Overrides checkInterval in some specific cases */
  public static volatile long checkIntervalOverride = -1;
  /** Whether to manage BBMDs */
  public static volatile boolean manageBBMD = false;
  /** Used to control BBMD management timeout intervals */
  public final static Object waitObj = new Object();
  /** Used to manually trigger BBMD management */
  private static volatile boolean goNow = false;
  /** Whether the last Fixer completed successfully */
  public static volatile boolean lastStatus = true;
  /**
   * Entry point of this add-on.
   */
  @Override public void contextInitialized(ServletContextEvent sce){
    info = AddOnInfo.getAddOnInfo();
    name = info.getName();
    prefix = '/'+name+'/';
    root = info.getPrivateDir().toPath();
    logger = info.getDateStampLogger();
    SavedData.init(root.resolve("params.dat"));
    mainThread = new Thread(){
      public void run(){
        long nextSave = 0;
        long a,b;
        int attempts = 0;
        while (!stop){
          try{
            b = System.currentTimeMillis();
            a = b+(nextSave==0?(Core.getSystemUptime()<300000L?60000L:5000L):(checkIntervalOverride>0?checkIntervalOverride:Initializer.checkInterval));
            if (checkIntervalOverride>0){
              checkIntervalOverride = -1;
            }
            do {
              synchronized(waitObj){
                if (!goNow && !stop){
                  waitObj.wait(Math.min(a-b+10L,60000L));
                }
              }
              if (goNow || stop){
                break;
              }
              b = System.currentTimeMillis();
            } while (b<a);
            if (stop){
              break;
            }
            if ((a=System.currentTimeMillis())>nextSave){
              nextSave = a+600000L;
              SavedData.saveData();
            }
            if (goNow){
              attempts = -1;
            }
            if (manageBBMD){
              final Snapshot s = new Snapshot(5000L);
              lastStatus = new Fixer(s, SavedData.groups).isComplete();
              if (s.requireNotify()){
                if (attempts!=-1){
                  if (attempts>=SavedData.failsBeforeNotify){
                    if (SavedData.sendEmail("The BBMD management add-on has detected a configuration problem. Please log into WebCTRL and review all warning messages.")){
                      attempts = -1;
                    }
                  }else{
                    ++attempts;
                  }
                }
              }else{
                attempts = 0;
              }
            }else{
              attempts = 0;
              if (goNow){
                lastStatus = new Snapshot(5000L).isComplete();
              }
            }
          }catch(InterruptedException e){}catch(Throwable t){
            Initializer.log(t);
          }
          goNow = false;
        }
        running = false;
      }
    };
    running = true;
    mainThread.start();
  }
  /**
   * Releases resources.
   */
  @Override public void contextDestroyed(ServletContextEvent sce){
    stop = true;
    SavedData.saveData();
    trigger();
    if (running){
      try{
        mainThread.interrupt();
        mainThread.join();
      }catch(InterruptedException e){}
    }
  }
  /**
   * Trigger BBMD management to start immediately.
   */
  public static void trigger(){
    synchronized (waitObj){
      goNow = true;
      waitObj.notifyAll();
    }
  }
  /**
   * @return the name of this application.
   */
  public static String getName(){
    return name;
  }
  /**
   * @return the prefix used for constructing relative URL paths.
   */
  public static String getPrefix(){
    return prefix;
  }
  /**
   * @return the private directory for this addon.
   */
  public static Path getRoot(){
    return root;
  }
  /**
   * Logs a message.
   */
  public synchronized static void log(String str){
    logger.println(str);
  }
  /**
   * Logs an error.
   */
  public synchronized static void log(Throwable t){
    logger.println(t);
  }
}