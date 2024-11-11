package aces.webctrl.bbmd.web;
import aces.webctrl.bbmd.core.*;
import javax.servlet.http.*;
public class GetStatus extends ServletBase {
  @Override public void exec(final HttpServletRequest req, final HttpServletResponse res) throws Throwable {
    final Snapshot snap = new Snapshot(-1);
    final StringBuilder sb = new StringBuilder(8192);
    snap.toJSON(sb, "", true);
    res.setContentType("application/json");
    res.getWriter().print(sb.toString());
  }
}