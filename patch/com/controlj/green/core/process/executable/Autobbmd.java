package com.controlj.green.core.process.executable;
import com.controlj.green.core.process.*;
import com.controlj.green.core.data.*;
import com.controlj.green.common.*;
import java.util.*;
public class Autobbmd extends CoreBuiltinBase {
  private final static CJTrace trace = new CJTrace(Autobbmd.class);
  public final static CoreProcInfo info = new CoreProcInfo();
  private final static HashSet<String> helpSet = new HashSet<>();
  public static final CoreProcInfo getProcInfo() {
    return info;
  }
  private static String getHelp() {
    StringBuilder sb = new StringBuilder(512);
    sb.append(
      "autobbmd\r\n"+
      "  on ....... Mark the current node to have automatically configured BBMDs\r\n"+
      "  off ...... Mark the current node to have manually configured BBMDs\r\n"+
      "  show ..... Show whether the current node is set to manual or auto BBMD"
    );
    return sb.toString();
  }
  @Override public int builtinMain(){
    try{
      final String cmd = this.nextStringArg("show").toLowerCase();
      switch (cmd){
        case "show":{
          boolean autoConfigure;
          boolean autoBBMD;
          try(
            CoreDataSession cds = CoreDataSession.open(this.currentNode.getSession().getUserSession(), 0);
          ){
            final CoreHWDevice node = (CoreHWDevice)cds.getCorrespondingCoreNode(this.currentNode).findMyDevice();
            autoConfigure = node.getBooleanAttribute(CoreNodeConstants.AUTO_IP_CONFIG);
            autoBBMD = node.getBooleanAttribute(CoreNodeConstants.BBMD_ROUTER);
          }
          if (autoConfigure){
            this.stdout.println("This device has automatically configured BBMDs.");
            if (autoBBMD){
              this.stdout.println("This device is an active BBMD on its subnet.");
            }else{
              this.stdout.println("This device is not an active BBMD on its subnet.");
              this.stdout.println("Use the 'bbmd set' command to set this device as an active BBMD.");
            }
          }else{
            this.stdout.println("This device has manually configured BBMDs.");
          }
          break;
        }
        case "on":{
          boolean autoBBMD;
          try(
            CoreDataSession cds = CoreDataSession.open(this.currentNode.getSession().getUserSession(), 5);
          ){
            final CoreHWDevice node = (CoreHWDevice)cds.getCorrespondingCoreNode(this.currentNode).findMyDevice();
            node.setBooleanAttribute(CoreNodeConstants.AUTO_IP_CONFIG, true);
            cds.commit();
            autoBBMD = node.getBooleanAttribute(CoreNodeConstants.BBMD_ROUTER);
          }
          this.stdout.println("This device has automatically configured BBMDs.");
          if (autoBBMD){
            this.stdout.println("This device is an active BBMD on its subnet.");
          }else{
            this.stdout.println("This device is not an active BBMD on its subnet.");
            this.stdout.println("Use the 'bbmd set' command to set this device as an active BBMD.");
          }
          break;
        }
        case "off":{
          try(
            CoreDataSession cds = CoreDataSession.open(this.currentNode.getSession().getUserSession(), 5);
          ){
            final CoreHWDevice node = (CoreHWDevice)cds.getCorrespondingCoreNode(this.currentNode).findMyDevice();
            node.setBooleanAttribute(CoreNodeConstants.AUTO_IP_CONFIG, false);
            node.setBooleanAttribute(CoreNodeConstants.BBMD_ROUTER, false);
            cds.commit();
          }
          this.stdout.println("This device has manually configured BBMDs.");
          break;
        }
        default:{
          if (!helpSet.contains(cmd)) {
            this.stdout.println("Unknown command: " + cmd);
          }
          this.stdout.println();
          this.stdout.println("Available commands:");
          this.stdout.println(getHelp());
        }
      }
    }catch(Throwable t){
      if (trace.general){
        trace.error("Error in autobbmd command.", t);
      }
      this.stdout.println("An error occurred: " + t.getMessage());
      return 2;
    }
    return 0;
  }
  static {
    try{
      info.userType = CoreProcInfo.USERTYPE_NORMAL;
      info.helpType = CoreProcInfo.HELPTYPE_UNCOMMON;
      info.userPriv = 251;
      info.description = "BBMD related commands.";
      info.usage = getHelp();
      helpSet.add("help");
      helpSet.add("-help");
      helpSet.add("--help");
      helpSet.add("-h");
      helpSet.add("--h");
    }catch(Throwable t){
      trace.error("Failed to initialize.", t);
    }
  }
}