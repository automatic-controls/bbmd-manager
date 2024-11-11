package aces.webctrl.bbmd.core;
import com.controlj.green.core.comm.bbmd.*;
import com.controlj.green.comm.bacnet.protocol.*;
import java.util.regex.*;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.time.format.*;
public class Utility {
  public final static DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
  public final static Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]\\d|\\d)$");
  private final static Pattern LINE_ENDING = Pattern.compile("\\r?+\\n");
  private final static Pattern SUBST_FORMATTER = Pattern.compile("\\$(\\d)");
  public static boolean disjointOrEqualSubnets(int ipAddress1, int subnetMask1, int ipAddress2, int subnetMask2){
    final boolean a = subnetContains(ipAddress1, subnetMask1, ipAddress2);
    return (a && subnetMask1==subnetMask2) || (!a && !subnetContains(ipAddress2, subnetMask2, ipAddress1));
  }
  public static boolean subnetContains(int ipAddress, int subnetMask, int testAddress){
    return (ipAddress&subnetMask)==(testAddress&subnetMask);
  }
  public static boolean isValidSubnetMask(int subnetMask){
    boolean ones = false;
    for (int i=0;i<32;++i){
      if ((subnetMask&1)==0){
        if (ones){
          return false;
        }
      }else{
        ones = true;
      }
      subnetMask>>=1;
    }
    return true;
  }
  public static BACnetAddress getBACnetAddress(String ipv4){
    if (ipv4==null){
      return null;
    }
    final Matcher m = IPV4_PATTERN.matcher(ipv4);
    if (m.matches()){
      byte[] mac = new byte[6];
      mac[0] = (byte)Integer.parseInt(m.group(1));
      mac[1] = (byte)Integer.parseInt(m.group(2));
      mac[2] = (byte)Integer.parseInt(m.group(3));
      mac[3] = (byte)Integer.parseInt(m.group(4));
      mac[4] = (byte)(47808 >> 8);
      mac[5] = (byte)(47808 & 0xFF);
      return new BACnetAddress(0, mac, BBMDHelper.bbmdMask);
    }else{
      return null;
    }
  }
  public static byte[] getIpArray(int addressBits){
    final byte[] mac = new byte[4];
    mac[0] = (byte)((addressBits>>24)&0xFF);
    mac[1] = (byte)((addressBits>>16)&0xFF);
    mac[2] = (byte)((addressBits>>8)&0xFF);
    mac[3] = (byte)(addressBits&0xFF);
    return mac;
  }
  public static BACnetAddress getBACnetAddress(int addressBits){
    byte[] mac = new byte[6];
    mac[0] = (byte)((addressBits>>24)&0xFF);
    mac[1] = (byte)((addressBits>>16)&0xFF);
    mac[2] = (byte)((addressBits>>8)&0xFF);
    mac[3] = (byte)(addressBits&0xFF);
    mac[4] = (byte)(47808>>8);
    mac[5] = (byte)(47808&0xFF);
    return new BACnetAddress(0, mac, BBMDHelper.bbmdMask);
  }
  public static BroadcastDistributionEntry getBDE(int addressBits){
    byte[] mac = new byte[6];
    mac[0] = (byte)((addressBits>>24)&0xFF);
    mac[1] = (byte)((addressBits>>16)&0xFF);
    mac[2] = (byte)((addressBits>>8)&0xFF);
    mac[3] = (byte)(addressBits&0xFF);
    mac[4] = (byte)(47808>>8);
    mac[5] = (byte)(47808&0xFF);
    return new BroadcastDistributionEntry("", mac, BBMDHelper.bbmdMask);
  }
  public static String getIPv4(byte[] mac){
    if (mac==null || mac.length<4){
      return null;
    }
    final StringBuilder sb = new StringBuilder(16);
    sb.append(mac[0]&0xFF);
    sb.append('.');
    sb.append(mac[1]&0xFF);
    sb.append('.');
    sb.append(mac[2]&0xFF);
    sb.append('.');
    sb.append(mac[3]&0xFF);
    return sb.toString();
  }
  public static int getAddressBits(byte[] mac){
    if (mac==null || mac.length<4){
      return 0;
    }
    return ((mac[0]&0xFF)<<24)|((mac[1]&0xFF)<<16)|((mac[2]&0xFF)<<8)|(mac[3]&0xFF);
  }
  public static int getAddressBits(String ipv4){
    if (ipv4==null){
      return 0;
    }
    final Matcher m = IPV4_PATTERN.matcher(ipv4);
    if (m.matches()){
      final int g1 = Integer.parseInt(m.group(1));
      final int g2 = Integer.parseInt(m.group(2));
      final int g3 = Integer.parseInt(m.group(3));
      final int g4 = Integer.parseInt(m.group(4));
      return (g1<<24)|(g2<<16)|(g3<<8)|g4;
    }else{
      return 0;
    }
  }
  public static String getIPv4(int bits){
    final StringBuilder sb = new StringBuilder(16);
    sb.append((bits>>24)&0xFF);
    sb.append('.');
    sb.append((bits>>16)&0xFF);
    sb.append('.');
    sb.append((bits>>8)&0xFF);
    sb.append('.');
    sb.append(bits&0xFF);
    return sb.toString();
  }
  /**
   * @return the first non-null argument.
   */
  public static String coalesce(final String... args){
    for (int i=0;i<args.length;++i){
      if (args[i]!=null){
        return args[i];
      }
    }
    return null;
  }
  /**
   * @param epochMilli the number of milliseconds from 1970-01-01T00:00:00Z.
   * @return a formatted datetime {@code String} representing the specified instant in time.
   */
  public static String format(long epochMilli){
    return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMilli));
  }
  /**
   * Replaces occurrences of {@code $n} in the input {@code String} with the nth indexed argument.
   * For example, {@code format("Hello $0!", "Beautiful")=="Hello Beautiful!"}.
   */
  public static String format(final String s, final Object... args){
    final String[] args_ = new String[args.length];
    for (int i=0;i<args.length;++i){
      args_[i] = args[i]==null?"":Matcher.quoteReplacement(args[i].toString());
    }
    return SUBST_FORMATTER.matcher(s).replaceAll(new java.util.function.Function<MatchResult,String>(){
      public String apply(MatchResult m){
        int i = Integer.parseInt(m.group(1));
        return i<args.length?args_[i]:"";
      }
    });
  }
  /**
   * Writes all bytes from the specified resource to the output file.
   */
  public static void extractResource(String name, Path out) throws Throwable {
    try(
      InputStream s = Utility.class.getClassLoader().getResourceAsStream(name);
      OutputStream t = Files.newOutputStream(out);
    ){
      int read;
      byte[] buffer = new byte[8192];
      while ((read = s.read(buffer, 0, 8192)) >= 0) {
        t.write(buffer, 0, read);
      }
    }
  }
  /**
   * Loads all bytes from the given resource and convert to a {@code UTF-8} string.
   * @return the {@code UTF-8} string representing the given resource.
   */
  public static String loadResourceAsString(String name) throws Throwable {
    byte[] arr;
    try(
      InputStream s = Utility.class.getClassLoader().getResourceAsStream(name);
    ){
      arr = s.readAllBytes();
    }
    return LINE_ENDING.matcher(new String(arr, java.nio.charset.StandardCharsets.UTF_8)).replaceAll(System.lineSeparator());
  }
  /**
   * Loads all bytes from the given resource and convert to a {@code UTF-8} string.
   * @return the {@code UTF-8} string representing the given resource.
   */
  public static String loadResourceAsString(ClassLoader cl, String name) throws Throwable {
    byte[] arr;
    try(
      InputStream s = cl.getResourceAsStream(name);
    ){
      arr = s.readAllBytes();
    }
    return LINE_ENDING.matcher(new String(arr, java.nio.charset.StandardCharsets.UTF_8)).replaceAll(System.lineSeparator());
  }
  /**
   * Escapes a {@code String} for usage in CSV document cells.
   * @param str is the {@code String} to escape.
   * @return the escaped {@code String}.
   */
  public static String escapeCSV(String str){
    if (str.indexOf(',')==-1 && str.indexOf('"')==-1 && str.indexOf('\n')==-1 && str.indexOf('\r')==-1){
      return str;
    }else{
      return '"'+str.replace("\"","\"\"")+'"';
    }
  }
  /**
   * Escapes a {@code String} for usage in HTML attribute values.
   * @param str is the {@code String} to escape.
   * @return the escaped {@code String}.
   */
  public static String escapeHTML(String str){
    if (str==null){
      return "";
    }
    int len = str.length();
    StringBuilder sb = new StringBuilder(len+16);
    char c;
    int j;
    for (int i=0;i<len;++i){
      c = str.charAt(i);
      j = c;
      if (j>=32 && j<127){
        switch (c){
          case '&':{
            sb.append("&amp;");
            break;
          }
          case '"':{
            sb.append("&quot;");
            break;
          }
          case '\'':{
            sb.append("&apos;");
            break;
          }
          case '<':{
            sb.append("&lt;");
            break;
          }
          case '>':{
            sb.append("&gt;");
            break;
          }
          default:{
            sb.append(c);
          }
        }
      }else if (j<1114111 && (j<=55296 || j>57343)){
        sb.append("&#").append(Integer.toString(j)).append(";");
      }
    }
    return sb.toString();
  }
  /**
   * Intended to escape strings for use in Javascript.
   * Escapes backslashes, single quotes, and double quotes.
   * Replaces new-line characters with the corresponding escape sequences.
   */
  public static String escapeJS(String str){
    if (str==null){
      return "";
    }
    int len = str.length();
    StringBuilder sb = new StringBuilder(len+16);
    char c;
    for (int i=0;i<len;++i){
      c = str.charAt(i);
      switch (c){
        case '\\': case '\'': case '"': {
          sb.append('\\').append(c);
          break;
        }
        case '\n': {
          sb.append("\\n");
          break;
        }
        case '\t': {
          sb.append("\\t");
          break;
        }
        case '\r': {
          sb.append("\\r");
          break;
        }
        case '\b': {
          sb.append("\\b");
          break;
        }
        case '\f': {
          sb.append("\\f");
          break;
        }
        default: {
          sb.append(c);
        }
      }
    }
    return sb.toString();
  }
  /**
   * Encodes a JSON string.
   */
  public static String escapeJSON(String s){
    if (s==null){ return "NULL"; }
    int len = s.length();
    StringBuilder sb = new StringBuilder(len+16);
    char c;
    String hex;
    int hl;
    for (int i=0;i<len;++i){
      c = s.charAt(i);
      switch (c){
        case '\\': case '/': case '"': {
          sb.append('\\').append(c);
          break;
        }
        case '\n': {
          sb.append("\\n");
          break;
        }
        case '\t': {
          sb.append("\\t");
          break;
        }
        case '\r': {
          sb.append("\\r");
          break;
        }
        case '\b': {
          sb.append("\\b");
          break;
        }
        case '\f': {
          sb.append("\\f");
          break;
        }
        default: {
          if (c>31 && c<127){
            sb.append(c);
          }else{
            //JDK17: hex = HexFormat.of().toHexDigits(c);
            hex = Integer.toHexString((int)c);
            hl = hex.length();
            if (hl<=4){
              sb.append("\\u");
              for (;hl<4;hl++){
                sb.append('0');
              }
              sb.append(hex);
            }
          }
        }
      }
    }
    return sb.toString();
  }
}