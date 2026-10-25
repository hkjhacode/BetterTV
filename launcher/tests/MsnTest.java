package tv.dumbtv.home;

/** Host-side checks for finding MSN Weather's script bundle and service key, shaped like the real files. */
public final class MsnTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String page = "<script src='https://assets.msn.com/bundles/v1/weather/latest/vendors.31183f1815ca3947.js'></script>"
                + "<script src='https://assets.msn.com/bundles/v1/weather/latest/common.e6299e31fcc5715d.js' type=module>";
        check("https://assets.msn.com/bundles/v1/weather/latest/common.e6299e31fcc5715d.js".equals(Msn.bundle(page)), "bundle");
        check(Msn.bundle("<html></html>") == null, "no bundle");
        String js = "getOneServiceApiKey(){let e;if(x)return y;let t=\"0QfOX3Vn51YCzitbLaRkTTBadtWpgTN8NZLW0C1SEM\";"
                + "switch(this.appType){case\"windowsShell\":e=\"pWw5\";break}";
        check("0QfOX3Vn51YCzitbLaRkTTBadtWpgTN8NZLW0C1SEM".equals(Msn.key(js)), "default web key");
        check(Msn.key("let t=\"short\";switch(this.appType)") == null, "not a key");
        System.out.println("MsnTest ok");
    }
}
