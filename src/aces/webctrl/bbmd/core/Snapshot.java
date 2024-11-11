package aces.webctrl.bbmd.core;
import java.util.*;
public class Snapshot {
  public volatile static int RETRIES = 3;
  public volatile static long TIMEOUT = 300L;
  public volatile static long MAX_TIME = 45000L;
  private volatile static Snapshot lastCapture = null;
  public volatile long startTime;
  public volatile long endTime;
  public volatile TreeMap<Integer,Router> routers = null;
  public volatile TreeMap<Integer,Router> disabledRouters = null;
  public volatile ArrayList<String> duplicateAddresses = null;
  public volatile ConnectionParams cp = null;
  public volatile Router center = null;
  private volatile boolean complete = true;
  /**
   * @param cacheTimeout If 0, then do not use cache. If <0, then always use cache. If >0, then it specifies cache expiry.
   */
  public Snapshot(long cacheTimeout) throws InterruptedException {
    this(RETRIES,TIMEOUT,MAX_TIME,cacheTimeout);
  }
  private void copy(Snapshot snap){
    this.startTime = snap.startTime;
    this.endTime = snap.endTime;
    this.routers = snap.routers;
    this.disabledRouters = snap.disabledRouters;
    this.duplicateAddresses = snap.duplicateAddresses;
    this.cp = snap.cp;
    this.center = snap.center;
    this.complete = snap.complete;
  }
  public Snapshot(int retries, long timeout, long maxTime, long cacheTimeout) throws InterruptedException {
    Snapshot last = lastCapture;
    if (last!=null && cacheTimeout<0){
      copy(last);
    }
    synchronized (Snapshot.class){
      if (lastCapture!=null && (cacheTimeout<0 || cacheTimeout!=0 && System.currentTimeMillis()<lastCapture.endTime+cacheTimeout)){
        copy(lastCapture);
        return;
      }
      try{
        Collection<Router> rs = null;
        startTime = System.currentTimeMillis();
        disabledRouters = new TreeMap<Integer,Router>(Router.UNSIGNED);
        duplicateAddresses = new ArrayList<String>(8);
        final long lim = startTime+maxTime;
        int i;
        DatabaseLink d = null;
        for (i=1;i<=retries;++i){
          try{
            d = new DatabaseLink(true);
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
          for (i=1;i<=retries;++i){
            try{
              cp = d.getConnectionDetails();
              break;
            }catch(Throwable t){
              if (retries>1 && System.currentTimeMillis()>lim){
                retries = 1;
              }
              if (i>=retries){
                Initializer.log("Failed to get connection details.");
                Initializer.log(t);
              }else{
                Thread.sleep(timeout);
              }
            }
          }
          if (cp==null){
            complete = false;
          }
          for (i=1;i<=retries;++i){
            try{
              disabledRouters.clear();
              duplicateAddresses.clear();
              routers = d.getRouters(disabledRouters, duplicateAddresses);
              break;
            }catch(Throwable t){
              if (retries>1 && System.currentTimeMillis()>lim){
                retries = 1;
              }
              if (i>=retries){
                Initializer.log("Failed to get router list.");
                Initializer.log(t);
              }else{
                Thread.sleep(timeout);
              }
            }
          }
          if (routers==null){
            complete = false;
            return;
          }
        }finally{
          try{
            d.close();
          }catch(Throwable t){
            Initializer.log(t);
          }
        }
        rs = routers.values();
        for (Router r: rs){
          r.checkReach();
        }
        d = null;
        for (i=1;i<=retries;++i){
          try{
            d = new DatabaseLink(true);
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
            if (r.reachable){
              try{
                r.open(d);
              }catch(Throwable t){}
              if (r.isOpen()){
                for (i=1;i<=retries;++i){
                  try{
                    r.readBDT();
                    break;
                  }catch(Throwable t){
                    r.bdt = null;
                    if (retries>1 && System.currentTimeMillis()>lim){
                      retries = 1;
                    }
                    if (i>=retries){
                      Initializer.log("Failed to read BDT: "+r.displayName);
                      Initializer.log(t);
                    }else{
                      Thread.sleep(timeout);
                    }
                  }
                }
              }
              if (r.bdt==null){
                complete = false;
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
        if (cp!=null && routers!=null){
          cp.resolve(routers);
          if (cp.fdr && cp.primaryRouter==null){
            complete = false;
          }
          {
            Collection<Router> tail;
            int max=0;
            for (Router r: rs){
              if (Integer.compareUnsigned(r.ipAddressBits,max)>0){
                max = r.ipAddressBits|(~r.subnetMaskBits);
                tail = routers.tailMap(r.ipAddressBits).values();
                for (Router s: tail){
                  if (Integer.compareUnsigned(s.ipAddressBits,max)>0){
                    break;
                  }
                  if (s.bdt!=null && !s.bdt.isEmpty()){
                    r.bbmd = s;
                    break;
                  }
                }
                if (r.bbmd!=null){
                  for (Router s: tail){
                    if (Integer.compareUnsigned(s.ipAddressBits,max)>0){
                      break;
                    }
                    s.bbmd = r.bbmd;
                  }
                }
              }
            }
          }
          for (Router r: rs){
            if (Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, cp.ipAddressBits)){
              center = r;
              if (r.reachable){
                break;
              }
            }
          }
          if (center==null){
            if (cp.fdr && cp.primaryRouter!=null){
              center = cp.primaryRouter;
              for (Router r: rs){
                if (Utility.subnetContains(center.ipAddressBits, center.subnetMaskBits, r.ipAddressBits)){
                  r.bbmdNew = center;
                }
              }
            }
          }else if (center.bbmd!=null){
            center = center.bbmd;
          }
          {
            Collection<Router> tail;
            int max=0;
            for (Router r: rs){
              if (r.bbmdNew==null){
                if (r.bbmd==null){
                  max = r.ipAddressBits|(~r.subnetMaskBits);
                  tail = routers.tailMap(r.ipAddressBits).values();
                  for (Router s: tail){
                    if (Integer.compareUnsigned(s.ipAddressBits,max)>0){
                      break;
                    }
                    if (s.reachable){
                      r.bbmdNew = s;
                      break;
                    }
                  }
                  if (r.bbmdNew==null){
                    r.bbmdNew = r;
                  }
                  for (Router s: tail){
                    if (Integer.compareUnsigned(s.ipAddressBits,max)>0){
                      break;
                    }
                    s.bbmdNew = r.bbmdNew;
                  }
                }else{
                  r.bbmdNew = r.bbmd;
                }
              }
            }
          }
        }
      }finally{
        lastCapture = this;
        endTime = System.currentTimeMillis();
      }
    }
  }
  public boolean isComplete(){
    return complete;
  }
  public ArrayList<String> getWarnings(){
    final ArrayList<String> list = new ArrayList<String>();
    if (cp==null || routers==null){
      list.add("Failed to gather required information.");
      return list;
    }
    if (routers.isEmpty()){
      list.add("Could not detect any valid BACnet/IP routers.");
      return list;
    }
    for (String s: duplicateAddresses){
      list.add("Duplicate IP address: "+s);
    }
    for (Router s: disabledRouters.values()){
      list.add("Marked out-of-service: "+s.displayName);
    }
    Router x;
    if (cp.ipAddressBits==0){
      list.add("Invalid IP Address: BACnet/IP Connection");
    }else if ((x=routers.get(cp.ipAddressBits))!=null){
      list.add("Server and Router share an IP address: "+x.displayName);
    }
    if (cp.subnetMaskBits==0 || !Utility.isValidSubnetMask(cp.subnetMaskBits)){
      list.add("Invalid Subnet Mask: BACnet/IP Connection");
    }
    final Collection<Router> rs = routers.values();
    boolean b;
    for (Router r: rs){
      b = true;
      if (r.ipAddressBits==0){
        list.add("Invalid IP Address: "+r.displayName);
        b = false;
      }else if (!r.reachable){
        list.add("Ping failed: "+r.displayName);
      }
      if (r.reachable && r.bdt==null){
        list.add("Failed to read BDT: "+r.displayName);
      }
      if (r.subnetMaskBits==0 || !Utility.isValidSubnetMask(r.subnetMaskBits)){
        list.add("Invalid Subnet Mask: "+r.displayName);
        b = false;
      }
      if (r.defaultGatewayBits==0){
        list.add("Invalid Default Gateway: "+r.displayName);
        b = false;
      }
      if (b && !Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, r.defaultGatewayBits)){
        list.add("Default Gateway Unreachable: "+r.displayName);
      }
      if ((x=routers.get(r.defaultGatewayBits))!=null){
        list.add("'"+x.displayName+"' is the default gateway for '"+r.displayName+"'");
      }
    }
    for (Router r: rs){
      if (!Utility.disjointOrEqualSubnets(r.ipAddressBits, r.subnetMaskBits, cp.ipAddressBits, cp.subnetMaskBits)){
        list.add("WebCTRL server and '"+r.displayName+"' have distinct overlapping subnets.");
      }
      for (Router s: routers.tailMap(r.ipAddressBits, false).values()){
        b = Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, s.ipAddressBits);
        if (b && r.subnetMaskBits==s.subnetMaskBits){
          if (r.defaultGatewayBits!=s.defaultGatewayBits){
            list.add("'"+r.displayName+"' and '"+s.displayName+"' have different default gateways, but belong to the same subnet.");
          }
        }else if (b || Utility.subnetContains(s.ipAddressBits, s.subnetMaskBits, r.ipAddressBits)){
          list.add("'"+r.displayName+"' and '"+s.displayName+"' have distinct overlapping subnets.");
        }
      }
    }
    for (Router r: rs){
      if (r.bdt!=null){
        if (r.bdt.size()>1){
          if (!r.bdt.contains(r.ipAddressBits)){
            list.add("BDT does not contain reflexive entry: "+r.displayName);
          }
          if (r!=r.bbmd){
            list.add("Extra BBMD: "+r.displayName);
          }
        }
        for (Integer bde: r.bdt){
          if (r.ipAddressBits!=(int)bde){
            if ((x=routers.get(bde))==null){
              if (disabledRouters.get(bde)==null){
                list.add("'"+r.displayName+"' has unknown BDE: "+Utility.getIPv4(bde));
              }
            }else if (x.reachable && x.bdt!=null && !x.bdt.contains(r.ipAddressBits)){
              list.add("'"+r.displayName+"' fails BDT symmetry: "+Utility.getIPv4(bde));
            }
          }
        }
      }
    }
    x = null;
    for (Router r: rs){
      if (Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, cp.ipAddressBits)){
        x = r;
        break;
      }
    }
    if (x==null){
      if (!cp.fdr){
        list.add("FDR required, but not enabled.");
      }else if (cp.primaryRouter==null){
        list.add("Could not resolve primary FDR router.");
      }
    }else if (cp.fdr){
      list.add("FDR enabled, but is not required: "+x.displayName);
    }
    if (center!=null){
      final boolean useBDT = center.bdt!=null && !center.bdt.isEmpty();
      for (Router r: rs){
        if (r.reachable && r!=center && !Utility.subnetContains(center.ipAddressBits, center.subnetMaskBits, r.ipAddressBits) && !(useBDT && r.bbmd!=null && center.bdt.contains(r.bbmd.ipAddressBits))){
          list.add("Cannot communicate to server: "+r.displayName);
        }
      }
    }
    if (Initializer.manageBBMD){
      for (Router r: rs){
        if (r.autoBBMD || r.autoConfigure){
          list.add("BBMD auto-configure is enabled: "+r.displayName);
        }
      }
    }else{
      for (Router r: rs){
        if (!r.autoConfigure){
          list.add("BBMD auto-configure is disabled: "+r.displayName);
        }
      }
    }
    return list;
  }
  public boolean requireNotify(){
    if (cp==null || routers==null || routers.isEmpty()){
      return false;
    }
    if (!duplicateAddresses.isEmpty() || cp.ipAddressBits==0 || routers.get(cp.ipAddressBits)!=null || cp.subnetMaskBits==0 || !Utility.isValidSubnetMask(cp.subnetMaskBits)){
      return true;
    }
    final Collection<Router> rs = routers.values();
    boolean b;
    for (Router r: rs){
      if (r.ipAddressBits==0 || r.subnetMaskBits==0 || r.autoBBMD || r.autoConfigure || !Utility.isValidSubnetMask(r.subnetMaskBits) || r.defaultGatewayBits==0 || !Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, r.defaultGatewayBits) || routers.get(r.defaultGatewayBits)!=null || !Utility.disjointOrEqualSubnets(r.ipAddressBits, r.subnetMaskBits, cp.ipAddressBits, cp.subnetMaskBits)){
        return true;
      }
      for (Router s: routers.tailMap(r.ipAddressBits, false).values()){
        b = Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, s.ipAddressBits);
        if (b && r.subnetMaskBits==s.subnetMaskBits){
          if (r.defaultGatewayBits!=s.defaultGatewayBits){
            return true;
          }
        }else if (b || Utility.subnetContains(s.ipAddressBits, s.subnetMaskBits, r.ipAddressBits)){
          return true;
        }
      }
    }
    Router x = null;
    for (Router r: rs){
      if (Utility.subnetContains(r.ipAddressBits, r.subnetMaskBits, cp.ipAddressBits)){
        x = r;
        break;
      }
    }
    if (x==null){
      if (!cp.fdr || cp.primaryRouter==null){
        return true;
      }
    }else if (cp.fdr){
      return true;
    }
    if (center!=null){
      final boolean useBDT = center.bdt!=null && !center.bdt.isEmpty();
      for (Router r: rs){
        if (r.reachable && r!=center && !Utility.subnetContains(center.ipAddressBits, center.subnetMaskBits, r.ipAddressBits) && !(useBDT && r.bbmd!=null && center.bdt.contains(r.bbmd.ipAddressBits))){
          return true;
        }
      }
    }
    return false;
  }
  public void toJSON(StringBuilder sb, String indent, boolean includeWarnings){
    final String nextIndent = indent+"    ";
    sb.append(indent).append("{\n");
    sb.append(indent).append("  \"complete\": ").append(complete).append(",\n");
    final ArrayList<String> warnings = getWarnings();
    if (warnings.isEmpty()){
      sb.append(indent).append("  \"warnings\": [],\n");
    }else{
      sb.append(indent).append("  \"warnings\": [\n");
      boolean first = true;
      for (String s: warnings){
        if (first){
          first = false;
        }else{
          sb.append(",\n");
        }
        sb.append(nextIndent).append('"').append(Utility.escapeJSON(s)).append("\"");
      }
      sb.append('\n').append(indent).append("  ],\n");
    }
    if (cp==null){
      sb.append(indent).append("  \"connection\": null");
    }else{
      cp.toJSON(sb, indent+"  ");
    }
    sb.append(",\n");
    if (routers==null || routers.isEmpty()){
      sb.append(indent).append("  \"routers\": null");
    }else{
      sb.append(indent).append("  \"routers\": [\n");
      boolean first = true;
      for (Router r: routers.values()){
        if (first){
          first = false;
        }else{
          sb.append(",\n");
        }
        r.toJSON(sb, nextIndent);
      }
      sb.append('\n').append(indent).append("  ]");
    }
    sb.append(",\n");
    if (disabledRouters==null || disabledRouters.isEmpty()){
      sb.append(indent).append("  \"disabledRouters\": null\n");
    }else{
      sb.append(indent).append("  \"disabledRouters\": [\n");
      boolean first = true;
      for (Router r: disabledRouters.values()){
        if (first){
          first = false;
        }else{
          sb.append(",\n");
        }
        r.toJSON(sb, nextIndent);
      }
      sb.append('\n').append(indent).append("  ]\n");
    }
    sb.append(indent).append('}');
  }
}