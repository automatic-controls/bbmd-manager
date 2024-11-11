package aces.webctrl.bbmd.core;
import java.util.*;
import java.net.*;
import com.controlj.green.core.comm.connection.*;
import com.controlj.green.comm.bacnet.protocol.*;
import com.controlj.green.core.comm.bbmd.*;
import com.controlj.green.commbase.api.*;
import com.controlj.green.core.data.*;
public class Router implements AutoCloseable {
  public final static Comparator<Integer> UNSIGNED = new Comparator<Integer>(){
    @Override public int compare(Integer x, Integer y){
      return Integer.compareUnsigned(x, y);
    }
  };
  private final static boolean INCLUDE_THIRD_PARTY = false;
  public volatile static int pingTimeout = 3000;
  private volatile DatabaseLink dl;
  public volatile CoreHWDevice node;
  private volatile long dbid;
  public volatile TreeSet<Integer> bdt = null;
  public volatile String displayName;
  public volatile String referenceName;
  public volatile String modelName;
  public volatile String ipAddress;
  public volatile String subnetMask;
  public volatile String defaultGateway;
  public volatile String currentDriver = null;
  public volatile String latestDriver = null;
  public volatile int ipAddressBits;
  public volatile int subnetMaskBits;
  public volatile int defaultGatewayBits;
  public volatile boolean disabled;
  public volatile boolean autoConfigure;
  public volatile boolean autoBBMD;
  public volatile boolean valid;
  public volatile Router bbmd = null;
  public volatile Router bbmdNew = null;
  public volatile boolean reachable = false;
  public Router(DatabaseLink dl, CoreHWDevice node){
    this.dl = dl;
    this.node = node;
    readParams();
    if (valid){
      dbid = node.getDbid();
      dl.addResource(this);
    }
  }
  public void readParams(){
    referenceName = node.getReferenceName();
    ipAddress = node.getMacAddress();
    ipAddressBits = Utility.getAddressBits(ipAddress);
    disabled = node.getBooleanAttribute(CoreNodeConstants.DISABLED, false);
    modelName = node.getAttribute(CoreNodeConstants.MODEL_NAME);
    displayName = node.getDisplayName();
    if (displayName==null){
      displayName = referenceName;
    }
    valid = referenceName!=null && !referenceName.isBlank() && ipAddressBits!=0 && !disabled && (INCLUDE_THIRD_PARTY || modelName!=null);
    defaultGateway = node.getAttribute(CoreNodeConstants.DEFAULT_GATEWAY);
    subnetMask = node.getAttribute(CoreNodeConstants.SUBNET_MASK);
    autoConfigure = node.getBooleanAttribute(CoreNodeConstants.AUTO_IP_CONFIG, false);
    autoBBMD = node.getBooleanAttribute(CoreNodeConstants.BBMD_ROUTER, false);
    defaultGatewayBits = Utility.getAddressBits(defaultGateway);
    subnetMaskBits = Utility.getAddressBits(subnetMask);
    if (defaultGatewayBits==0){
      defaultGateway = "0.0.0.0";
    }
    if (subnetMaskBits==0){
      subnetMask = "0.0.0.0";
    }
    try{
      currentDriver = node.getDriver().getDefinitionName();
      latestDriver = node.getLatestDriverDefinition();
    }catch(Throwable t){
      Initializer.log(t);
    }
  }
  public void checkReach(){
    try{
      reachable = InetAddress.getByAddress(Utility.getIpArray(ipAddressBits)).isReachable(pingTimeout);
    }catch(Throwable t){}
  }
  /**
   * Disables BBMD autoconfigure
   */
  public void disableAutoConfig() throws Throwable {
    node.setBooleanAttribute(CoreNodeConstants.AUTO_IP_CONFIG, false);
    node.setBooleanAttribute(CoreNodeConstants.BBMD_ROUTER, false);
  }
  /**
   * Reads a broadcast distribution table (BDT) from this router.
   * @return {@code true} on success; {@code false} if there is no active {@link DatabaseLink}, or if this router is a third-party device.
   */
  public boolean readBDT() throws ConnectionException, CommException {
    if (dl==null){
      return false;
    }
    if (modelName==null || disabled){
      bdt = new TreeSet<Integer>(UNSIGNED);
      return false;
    }
    final BBMDHelper helper = dl.getBBMDHelper(node);
    final List<BroadcastDistributionEntry> list = helper.readBBMDTable(Utility.getBACnetAddress(ipAddressBits));
    bdt = new TreeSet<Integer>(UNSIGNED);
    int x;
    for (final BroadcastDistributionEntry bde: list){
      x = Utility.getAddressBits(bde.getAddress());
      if (x!=0){
        bdt.add(x);
      }
    }
    reachable = true;
    return true;
  }
  /**
   * Writes a broadcast distribution table (BDT) to this router.
   * @return {@code true} on success; {@code false} if there is no active {@link DatabaseLink}, or if this router has been set for automatic BBMD configuration, or if this router is a third-party device.
   */
  public boolean writeBDT(Set<Integer> bdt) throws ConnectionException, CommException {
    if (dl==null || autoConfigure || modelName==null || disabled){
      return false;
    }
    final ArrayList<BroadcastDistributionEntry> list = new ArrayList<BroadcastDistributionEntry>(bdt.size());
    for (Integer x: bdt){
      list.add(Utility.getBDE(x));
    }
    dl.getBBMDHelper(node).writeBBMDTableToAddress(Utility.getBACnetAddress(ipAddressBits), list);
    return true;
  }
  /**
   * Writes an empty broadcast distribution table (BDT) to this router.
   * @return {@code true} on success; {@code false} if there is no active {@link DatabaseLink}, or if this router has been set for automatic BBMD configuration, or if this router is a third-party device.
   */
  public boolean clearBDT() throws ConnectionException, CommException {
    return writeBDT(Collections.emptySet());
  }
  public boolean open(DatabaseLink dl) throws CoreNotFoundException {
    CoreNode n = dl.getNode(dbid);
    if (n instanceof CoreHWDevice){
      node = (CoreHWDevice)n;
      this.dl = dl;
      dl.addResource(this);
      return true;
    }else{
      return false;
    }
  }
  public boolean isOpen(){
    return dl!=null;
  }
  @Override public void close(){
    dl = null;
    node = null;
  }
  public void toJSON(StringBuilder sb, String indent){
    sb.append(indent).append("{\n");
    sb.append(indent).append("  \"dbid\": ").append(dbid).append(",\n");
    sb.append(indent).append("  \"referenceName\": \"").append(Utility.escapeJSON(referenceName)).append("\",\n");
    sb.append(indent).append("  \"displayName\": \"").append(Utility.escapeJSON(displayName)).append("\",\n");
    sb.append(indent).append("  \"modelName\": \"").append(Utility.escapeJSON(Utility.coalesce(modelName,"Unknown"))).append("\",\n");
    sb.append(indent).append("  \"ipAddress\": \"").append(ipAddress).append("\",\n");
    sb.append(indent).append("  \"subnetMask\": \"").append(subnetMask).append("\",\n");
    sb.append(indent).append("  \"defaultGateway\": \"").append(defaultGateway).append("\",\n");
    if (currentDriver!=null){
      sb.append(indent).append("  \"currentDriver\": \"").append(Utility.escapeJSON(currentDriver)).append("\",\n");
    }
    if (latestDriver!=null){
      sb.append(indent).append("  \"latestDriver\": \"").append(Utility.escapeJSON(latestDriver)).append("\",\n");
    }
    sb.append(indent).append("  \"reachable\": ").append(reachable).append(",\n");
    sb.append(indent).append("  \"autoConfigure\": ").append(autoConfigure).append(",\n");
    sb.append(indent).append("  \"autoBBMD\": ").append(autoBBMD).append(",\n");
    if (bdt==null || bdt.isEmpty()){
      sb.append(indent).append("  \"bdt\": []\n");
    }else{
      sb.append(indent).append("  \"bdt\": [");
      boolean first = true;
      for (Integer bde: bdt){
        if (first){
          first = false;
        }else{
          sb.append(',');
        }
        sb.append('\n').append(indent).append("    \"").append(Utility.getIPv4(bde)).append("\"");
      }
      sb.append('\n').append(indent).append("  ]\n");
    }
    sb.append(indent).append('}');
  }
}