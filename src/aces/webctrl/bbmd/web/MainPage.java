package aces.webctrl.bbmd.web;
import aces.webctrl.bbmd.core.*;
import java.util.*;
import java.util.regex.*;
import javax.servlet.http.*;
public class MainPage extends ServletBase {
  private final static Pattern splitter = Pattern.compile(";");
  private static int lim(int x, int min, int max){
    if (x<min){
      x = min;
    }else if (x>max){
      x = max;
    }
    return x;
  }
  private static long lim(long x, long min, long max){
    if (x<min){
      x = min;
    }else if (x>max){
      x = max;
    }
    return x;
  }
  @Override public void exec(final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    final String type = req.getParameter("type");
    if (type==null){
      res.setContentType("text/html");
      res.getWriter().print(getHTML(req));
    }else{
      switch(type){
        case "trigger":{
          Initializer.trigger();
          break;
        }
        case "save":{
          final String manage_ = req.getParameter("manage");
          final String checkInterval_ = req.getParameter("checkInterval");
          final String pingTimeout_ = req.getParameter("pingTimeout");
          final String readRetries_ = req.getParameter("readRetries");
          final String readTimeout_ = req.getParameter("readTimeout");
          final String readMaxTime_ = req.getParameter("readMaxTime");
          final String writeRetries_ = req.getParameter("writeRetries");
          final String writeTimeout_ = req.getParameter("writeTimeout");
          final String writeMaxTime_ = req.getParameter("writeMaxTime");
          final String emailSubject = req.getParameter("emailSubject");
          final String failsBeforeNotify_ = req.getParameter("failsBeforeNotify");
          final String emailRecipients_ = req.getParameter("emailRecipients");
          final String groups_ = req.getParameter("groups");
          if (manage_==null||checkInterval_==null||pingTimeout_==null||readRetries_==null||readTimeout_==null||readMaxTime_==null||writeRetries_==null||writeTimeout_==null||writeMaxTime_==null||emailSubject==null||failsBeforeNotify_==null||emailRecipients_==null||groups_==null){
            res.sendError(400, "Required parameters are missing.");
            break;
          }
          boolean manage;
          long checkInterval;
          int pingTimeout;
          int readRetries;
          long readTimeout;
          long readMaxTime;
          int writeRetries;
          long writeTimeout;
          long writeMaxTime;
          int failsBeforeNotify;
          String[] emailRecipients;
          ArrayList<Set<String>> groups;
          try{
            manage = Boolean.parseBoolean(manage_);
            checkInterval = lim(Long.parseLong(checkInterval_), 30000L, 3600000L);
            pingTimeout = lim(Integer.parseInt(pingTimeout_), 250, 10000);
            readRetries = lim(Integer.parseInt(readRetries_), 1, 20);
            readTimeout = lim(Long.parseLong(readTimeout_), 100L, 5000L);
            readMaxTime = lim(Long.parseLong(readMaxTime_), 3000L, 300000L);
            writeRetries = lim(Integer.parseInt(writeRetries_), 1, 20);
            writeTimeout = lim(Long.parseLong(writeTimeout_), 100L, 5000L);
            writeMaxTime = lim(Long.parseLong(writeMaxTime_), 3000L, 300000L);
            failsBeforeNotify = lim(Integer.parseInt(failsBeforeNotify_), 0, 1024);
            emailRecipients = splitter.split(emailRecipients_);
            groups = new ArrayList<Set<String>>();
            final String[] tokens = splitter.split(groups_);
            Set<String> set;
            for (int i=0,j,k;i<tokens.length;){
              if (tokens[i].isBlank()){
                ++i;
              }else{
                set = new HashSet<String>();
                j = Integer.parseInt(tokens[i]);
                ++i;
                for (k=0;k<j;++k,++i){
                  if (!tokens[i].isBlank()){
                    set.add(tokens[i]);
                  }
                }
                if (set.size()>1){
                  groups.add(set);
                }
              }
            }
          }catch(Throwable t){
            Initializer.log(t);
            res.sendError(400, "Could not parse all parameters.");
            break;
          }
          boolean trigger = !Initializer.manageBBMD && manage;
          Initializer.manageBBMD = manage;
          Initializer.checkInterval = checkInterval;
          Router.pingTimeout = pingTimeout;
          Snapshot.RETRIES = readRetries;
          Snapshot.TIMEOUT = readTimeout;
          Snapshot.MAX_TIME = readMaxTime;
          Fixer.RETRIES = writeRetries;
          Fixer.TIMEOUT = writeTimeout;
          Fixer.MAX_TIME = writeMaxTime;
          SavedData.emailSubject = emailSubject;
          SavedData.failsBeforeNotify = failsBeforeNotify;
          SavedData.emailRecipients = emailRecipients;
          SavedData.setGroups(groups);
          if (trigger){
            Initializer.trigger();
          }
        }
        case "refresh":{
          final Snapshot snap = new Snapshot(-1);
          final StringBuilder sb = new StringBuilder(4096);
          sb.append("{");
          ArrayList<String> warnings = snap.getWarnings();
          if (warnings.isEmpty()){
            sb.append("\"warnings\":[],");
          }else{
            sb.append("\"warnings\":[");
            boolean first = true;
            for (String s: warnings){
              if (first){
                first = false;
              }else{
                sb.append(",");
              }
              sb.append('"').append(Utility.escapeJSON(s)).append("\"");
            }
            sb.append("],");
          }
          warnings = null;
          sb.append("\"cacheTime\":\"").append(Utility.format(snap.endTime)).append("\",");
          sb.append("\"centerRouter\":\"").append(snap.center==null?"Unknown":Utility.escapeJSON(snap.center.displayName)).append("\",");
          sb.append("\"fdr\":\"").append(snap.cp==null?"Unknown":(snap.cp.fdr?"Active":"Inactive")).append("\",");
          sb.append("\"bbmdStatus\":\"").append(Initializer.lastStatus?"Success":"Failure").append('"');
          if (req.getParameter("readOnly")==null){
            sb.append(',');
            sb.append("\"manage\":").append(Initializer.manageBBMD).append(',');
            sb.append("\"checkInterval\":").append(Initializer.checkInterval).append(',');
            sb.append("\"pingTimeout\":").append(Router.pingTimeout).append(',');
            sb.append("\"readRetries\":").append(Snapshot.RETRIES).append(',');
            sb.append("\"readTimeout\":").append(Snapshot.TIMEOUT).append(',');
            sb.append("\"readMaxTime\":").append(Snapshot.MAX_TIME).append(',');
            sb.append("\"writeRetries\":").append(Fixer.RETRIES).append(',');
            sb.append("\"writeTimeout\":").append(Fixer.TIMEOUT).append(',');
            sb.append("\"writeMaxTime\":").append(Fixer.MAX_TIME).append(',');
            sb.append("\"emailSubject\":\"").append(Utility.escapeJSON(SavedData.emailSubject)).append("\",");
            sb.append("\"failsBeforeNotify\":").append(SavedData.failsBeforeNotify).append(',');
            sb.append("\"emailRecipients\":\"");
            String[] arr = SavedData.emailRecipients;
            for (int i=0;i<arr.length;++i){
              if (i>0){
                sb.append(';');
              }
              sb.append(Utility.escapeJSON(arr[i]));
            }
            sb.append("\",");
            final List<Set<String>> groups = SavedData.groups;
            final Set<String> invalid = new HashSet<String>();
            for (Set<String> s: groups){
              invalid.addAll(s);
            }
            try(
              DatabaseLink d = new DatabaseLink(true);
            ){
              d.verifyRefnames(invalid);
            }catch(Throwable t){
              Initializer.log(t);
            }
            sb.append("\"groups\":[");
            boolean first = true, first2;
            for (Set<String> set: groups){
              if (first){
                first = false;
              }else{
                sb.append(',');
              }
              sb.append('[');
              first2 = true;
              for (String s: set){
                if (first2){
                  first2 = false;
                }else{
                  sb.append(',');
                }
                sb.append("{\"refname\":\"").append(Utility.escapeJSON(s)).append("\",");
                sb.append("\"valid\":").append(!invalid.contains(s)).append('}');
              }
              sb.append(']');
            }
            sb.append(']');
          }
          sb.append('}');
          res.setContentType("application/json");
          res.getWriter().print(sb.toString());
          break;
        }
        default:{
          res.sendError(400, "Unrecognized type parameter.");
        }
      }
    }
  }
}