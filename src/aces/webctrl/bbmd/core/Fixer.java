package aces.webctrl.bbmd.core;
import java.util.*;
import com.controlj.green.core.data.*;
import com.controlj.green.core.download.api.*;
import com.controlj.green.core.download.impl.*;
public class Fixer {
  public volatile static int RETRIES = 3;
  public volatile static long TIMEOUT = 300L;
  public volatile static long MAX_TIME = 15000L;
  public volatile Snapshot snip;
  public volatile long startTime;
  public volatile long endTime;
  private volatile boolean complete = true;
  public Fixer(Snapshot snip, List<Set<String>> groups) throws InterruptedException {
    this(snip,groups,RETRIES,TIMEOUT,MAX_TIME);
  }
  public Fixer(Snapshot snip, List<Set<String>> groups, int retries, long timeout, long maxTime) throws InterruptedException {
    this.snip = snip;
    if (snip==null || snip.cp==null || snip.routers==null || snip.center==null){
      complete = false;
      return;
    }
    final Collection<Router> rs = snip.routers.values();
    final List<Set<Integer>> exceptions = new ArrayList<Set<Integer>>(groups==null?1:groups.size());
    if (groups!=null){
      HashMap<String,Router> refnameMap = new HashMap<String,Router>();
      for (Router r: rs){
        refnameMap.put(r.referenceName, r);
      }
      for (Set<String> set: groups){
        Set<Integer> setNew = new TreeSet<Integer>(Router.UNSIGNED);
        Router r;
        for (String x: set){
          if ((r=refnameMap.get(x))!=null){
            setNew.add(r.bbmdNew.ipAddressBits);
          }
        }
        if (setNew.size()>1){
          exceptions.add(setNew);
        }
      }
      groups = null;
    }
    final TreeMap<Integer,Set<Integer>> changes = new TreeMap<Integer,Set<Integer>>(Router.UNSIGNED);
    boolean disableAuto = false;
    {
      Set<Integer> bbmds = new TreeSet<Integer>();
      for (Router r: rs){
        bbmds.add(r.bbmdNew.ipAddressBits);
      }
      for (Router r: rs){
        if (r.autoBBMD || r.autoConfigure){
          disableAuto = true;
          Initializer.log("Disabling BBMD auto-configure: "+r.displayName);
        }
      }
      for (Router r: rs){
        if (r.reachable){
          if (r!=r.bbmdNew){
            if (r.bdt==null || !r.bdt.isEmpty()){
              changes.put(r.ipAddressBits, Collections.emptySet());
              Initializer.log("Clearing BDT: "+r.displayName);
            }
          }else{
            TreeSet<Integer> bdt = new TreeSet<Integer>(Router.UNSIGNED);
            if (r==snip.center){
              bdt.addAll(bbmds);
            }else{
              bdt.add(r.ipAddressBits);
              bdt.add(snip.center.ipAddressBits);
              for (Set<Integer> set: exceptions){
                if (set.contains(r.ipAddressBits)){
                  bdt.addAll(set);
                }
              }
            }
            if (r.bdt==null || !bdt.equals(r.bdt)){
              changes.put(r.ipAddressBits, bdt);
              Initializer.log("Writing BDT: "+r.displayName);
            }
          }
        }
      }
    }
    startTime = System.currentTimeMillis();
    if (changes.isEmpty() && !disableAuto){
      endTime = startTime;
      return;
    }
    try{
      final long lim = startTime+maxTime;
      int i;
      DatabaseLink d = null;
      if (disableAuto){
        for (i=1;i<=retries;++i){
          try{
            d = new DatabaseLink(false);
            break;
          }catch(Throwable t){
            if (retries>1 && System.currentTimeMillis()>lim){
              retries = 1;
            }
            if (i>=retries){
              Initializer.log("Failed to establish DatabaseLink.");
              Initializer.log(t);
            }else{
              Thread.sleep(timeout);
            }
          }
        }
        if (d==null){
          complete = false;
          return;
        }
        ArrayList<Router> ls = new ArrayList<Router>();
        try{
          for (Router r: rs){
            try{
              r.open(d);
            }catch(Throwable t){}
            if (r.isOpen() && (r.autoBBMD || r.autoConfigure)){
              for (i=1;i<=retries;++i){
                try{
                  r.disableAutoConfig();
                  ls.add(r);
                  break;
                }catch(Throwable t){
                  if (retries>1 && System.currentTimeMillis()>lim){
                    retries = 1;
                  }
                  if (i>=retries){
                    Initializer.log("Failed to disable BBMD auto-configure: "+r.displayName);
                    Initializer.log(t);
                  }else{
                    Thread.sleep(timeout);
                  }
                }
              }
            }
          }
        }finally{
          try{
            d.close();
          }catch(Throwable t){
            Initializer.log(t);
          }
        }
        if (!ls.isEmpty()){
          Thread.sleep(5000L);
          for (i=1;i<=retries;++i){
            try{
              d = new DatabaseLink(false);
              try{
                CoreNode node = d.getNode("/trees/network");
                if (node!=null){
                  Downloader downloader = new Downloader(d.cds);
                  try{
                    downloader.getResumeAction(TaskSet.BBMD).performActionSingly(node, Downloader.TASKS_ALL);
                  }finally{
                    downloader.close();
                  }
                }
              }finally{
                d.close();
              }
              break;
            }catch(Throwable t){
              if (retries>1 && System.currentTimeMillis()>lim){
                retries = 1;
              }
              if (i>=retries){
                Initializer.log("Failed to initiate BBMD download.");
                Initializer.log(t);
              }else{
                Thread.sleep(timeout);
              }
            }
          }
          Initializer.checkIntervalOverride = 20000L;
        }
      }
      if (changes.isEmpty()){
        return;
      }
      d = null;
      if (disableAuto){
        Thread.sleep(2000L);
      }
      for (i=1;i<=retries;++i){
        try{
          d = new DatabaseLink(false);
          break;
        }catch(Throwable t){
          if (retries>1 && System.currentTimeMillis()>lim){
            retries = 1;
          }
          if (i>=retries){
            Initializer.log("Failed to establish DatabaseLink.");
            Initializer.log(t);
          }else{
            Thread.sleep(timeout);
          }
        }
      }
      if (d==null){
        complete = false;
        return;
      }
      try{
        for (Router r: rs){
          try{
            r.open(d);
          }catch(Throwable t){}
        }
        Router r;
        for (Map.Entry<Integer,Set<Integer>> x: changes.entrySet()){
          r = snip.routers.get(x.getKey());
          if (r!=null && r.isOpen()){
            for (i=1;i<=retries;++i){
              try{
                r.writeBDT(x.getValue());
                break;
              }catch(Throwable t){
                if (retries>1 && System.currentTimeMillis()>lim){
                  retries = 1;
                }
                if (i>=retries){
                  Initializer.log("Failed to write BDT: "+r.displayName);
                  Initializer.log(t);
                }else{
                  Thread.sleep(timeout);
                }
              }
            }
          }
        }
      }finally{
        try{
          d.close();
        }catch(Throwable t){
          Initializer.log(t);
        }
      }
    }finally{
      endTime = System.currentTimeMillis();
    }
  }
  public boolean isComplete(){
    return complete;
  }
}