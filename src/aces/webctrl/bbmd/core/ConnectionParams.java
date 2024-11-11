package aces.webctrl.bbmd.core;
import java.util.*;
public class ConnectionParams {
  public volatile long dbid;
  public volatile String ipAddress;
  public volatile String subnetMask;
  public volatile int ipAddressBits;
  public volatile int subnetMaskBits;
  public volatile boolean fdr;
  public volatile String primary;
  public volatile String secondary;
  public volatile Router primaryRouter = null;
  public volatile Router secondaryRouter = null;
  public void resolve(TreeMap<Integer,Router> routers){
    primaryRouter = null;
    secondaryRouter = null;
    final boolean a = primary==null || primary.isBlank();
    if (a){
      return;
    }
    final boolean b = secondary==null || secondary.isBlank();
    int x = b?1:2;
    for (Router r: routers.values()){
      if (r.referenceName.equals(primary)){
        primaryRouter = r;
        if (--x==0){
          return;
        }
      }else if (!b && r.referenceName.equals(secondary)){
        secondaryRouter = r;
        if (--x==0){
          return;
        }
      }
    }
  }
  public void toJSON(StringBuilder sb, String indent){
    sb.append(indent).append("\"connection\": {\n");
    sb.append(indent).append("  \"dbid\": ").append(dbid).append(",\n");
    sb.append(indent).append("  \"ipAddress\": \"").append(ipAddress).append("\",\n");
    sb.append(indent).append("  \"subnetMask\": \"").append(subnetMask).append("\",\n");
    sb.append(indent).append("  \"fdr\": ").append(fdr).append(",\n");
    if (primaryRouter==null){
      sb.append(indent).append("  \"primary\": \"\",\n");
    }else{
      sb.append(indent).append("  \"primary\": \"").append(Utility.escapeJSON(primaryRouter.displayName)).append("\",\n");
    }
    if (secondaryRouter==null){
      sb.append(indent).append("  \"secondary\": \"\"\n");
    }else{
      sb.append(indent).append("  \"secondary\": \"").append(Utility.escapeJSON(secondaryRouter.displayName)).append("\"\n");
    }
    sb.append(indent).append('}');
  }
}