package aces.webctrl.bbmd.core;
import java.util.*;
import com.controlj.green.core.comm.connection.*;
import com.controlj.green.comm.bacnet.service.*;
import com.controlj.green.core.comm.bbmd.*;
import com.controlj.green.commbase.api.*;
import com.controlj.green.core.data.*;
/**
 * Utility class meant to facilitate access to WebCTRL's internal operator API.
 */
public class DatabaseLink implements AutoCloseable {
  /** Controls the connection to the underlying database. */
  public volatile CoreDataSession cds;
  /** Used to cache CoreNodes. */
  private volatile HashMap<String,CoreNode> nodeMap = new HashMap<String,CoreNode>();
  /** Specifies whether modifications can be made to the underlying database. */
  private volatile boolean readOnly;
  /** Specifies whether to automatically commit changes. */
  private volatile boolean autoCommit;
  /** Objects to automatically close when this {@code DatabaseLink} is closed. */
  private final ArrayList<AutoCloseable> res = new ArrayList<AutoCloseable>();
  /**
   * Opens a new CoreDataSession.
   * @param readOnly specifies whether to expect any modifications to the underlying operator database.
   */
  public DatabaseLink(boolean readOnly) throws CoreDatabaseException {
    this.readOnly = readOnly;
    this.autoCommit = !readOnly;
    cds = CoreDataSession.open(readOnly?0:1);
  }
  /**
   * Any added resource will be closed when this {@code DatabaseLink} is closed.
   */
  public void addResource(AutoCloseable ac){
    res.add(ac);
  }
  /**
   * @param autoCommit specifies whether to automatically commit changes.
   */
  public void setAutoCommit(boolean autoCommit){
    this.autoCommit = autoCommit;
  }
  /**
   * @return whether to automatically commit changes.
   */
  public boolean isAutoCommit(){
    return autoCommit;
  }
  /**
   * @return whether the underlying database connection is read-only.
   */
  public boolean isReadOnly(){
    return readOnly;
  }
  /**
   * @return a utility to assist with BBMD management on the specified device.
   */
  public BBMDHelper getBBMDHelper(CoreNode device) throws ConnectionException {
    return new BBMDHelper(((BACnetClientRequestProcessor)ConnectionManager.connectTo(device).getConnection().getClientService().getRequestProcessor()).getProtocol());
  }
  /**
   * @return a mapping of BBMD capable routers sorted by IP address.
   */
  public TreeMap<Integer,Router> getRouters(Map<Integer,Router> disabledRouters, List<String> duplicateAddresses) throws CoreIntegrityException {
    final TreeMap<Integer,Router> map = new TreeMap<Integer,Router>(Router.UNSIGNED);
    final String proto = MediaType.BACNET_IP.toString();
    Router r,s;
    CoreHWDevice n;
    for (CoreNode a: getNode("/trees/network").getChildrenByType((short)105)){
      if (!"discovered".equals(a.getReferenceName())){
        for (CoreNode b: a.getChildrenByType((short)202)){
          if (proto.equals(b.getAttribute(CoreNodeConstants.MEDIA_TYPE))){
            for (CoreNode c: b.getChildrenByCategory(NodeType.CAT_BACNET_HW_DEVICE)){
              if (c instanceof CoreHWDevice){
                n = (CoreHWDevice)c;
                if (n.isCapableOfBBMD()){
                  r = new Router(this, n);
                  if (r.valid){
                    if ((s=map.put(r.ipAddressBits, r))!=null && duplicateAddresses!=null){
                      duplicateAddresses.add(s.displayName);
                    }
                  }else if (r.disabled && disabledRouters!=null){
                    disabledRouters.put(r.ipAddressBits, r);
                  }
                }
              }
            }
          }
        }
      }
    }
    return map;
  }
  /**
   * Deletes all valid refnames from the given set, leaving only invalid refnames remaining.
   */
  public void verifyRefnames(Set<String> names){
    final String proto = MediaType.BACNET_IP.toString();
    CoreHWDevice n;
    for (CoreNode a: getNode("/trees/network").getChildrenByType((short)105)){
      if (!"discovered".equals(a.getReferenceName())){
        for (CoreNode b: a.getChildrenByType((short)202)){
          if (proto.equals(b.getAttribute(CoreNodeConstants.MEDIA_TYPE))){
            for (CoreNode c: b.getChildrenByCategory(NodeType.CAT_BACNET_HW_DEVICE)){
              if (c instanceof CoreHWDevice){
                n = (CoreHWDevice)c;
                if (n.isCapableOfBBMD()){
                  names.remove(n.getReferenceName());
                }
              }
            }
          }
        }
      }
    }
  }
  /**
   * @return an object containing BACnet/IP connection details for the server, or {@code null} if no such connection exists.
   */
  public ConnectionParams getConnectionDetails() throws CoreIntegrityException {
    ConnectionParams cp = new ConnectionParams();
    int x;
    for (CoreNode a: getNode("/trees/config/connections").getChildrenByType((short)4)){
      x = 0;
      for (CoreNode b: a.getChildren()){
        switch (b.getReferenceName()){
          case "ip_address":{
            cp.ipAddress = b.getValueString();
            ++x;
            break;
          }
          case "subnetmask":{
            cp.subnetMask = b.getValueString();
            ++x;
            break;
          }
          case "foreign_device":{
            cp.fdr = "force".equals(b.getValueString());
            ++x;
            break;
          }
          case "register_with_device":{
            cp.primary = b.getValueString();
            ++x;
            break;
          }
          case "register_with_alt_device":{
            cp.secondary = b.getValueString();
            ++x;
            break;
          }
        }
        if (x==5){
          cp.ipAddressBits = Utility.getAddressBits(cp.ipAddress);
          cp.subnetMaskBits = Utility.getAddressBits(cp.subnetMask);
          if (cp.ipAddressBits==0){
            cp.ipAddress = "0.0.0.0";
          }
          if (cp.subnetMaskBits==0){
            cp.subnetMask = "0.0.0.0";
          }
          cp.dbid = a.getDbid();
          return cp;
        }
      }
    }
    return null;
  }
  /**
   * @return the CoreNode corresponding to the given absolute path.
   */
  public CoreNode getNode(String path) throws CoreIntegrityException {
    CoreNode n = nodeMap.get(path);
    if (n==null){
      n = cds.getExpectedNode(path);
      nodeMap.put(path,n);
    }
    return n;
  }
  /**
   * @return the CoreNode corresponding to the given DBID.
   */
  public CoreNode getNode(long dbid) throws CoreNotFoundException {
    return cds.getNode(dbid);
  }
  /**
   * Commits changes to the underlying database.
   */
  public void commit(){
    cds.commit();
  }
  /**
   * Closes the CoreDataSession associated with this Object.
   */
  @Override public void close(){
    try{
      for (AutoCloseable ac: res){
        ac.close();
      }
    }catch(Exception e){
      Initializer.log(e);
    }
    if (autoCommit){
      commit();
    }
    cds.close();
    res.clear();
  }
}