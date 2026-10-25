package tv.dumbtv.home;

import java.util.Map;

/** Host-side checks for reading MSN Weather's page; the sample is shaped like its real markup. */
public final class MsnTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        String[] parts = {"Palam Village, Dwarka, Delhi", "Current weather", "1:18 AM", "21", "°C", "Partly cloudy",
            "Feels like", "‎", "22°", "Expect sunny skies.  The high will be 32°.", "Air quality", "Poor air quality",
            "(", "201", "-", "300", ")", "Primary pollutant", "PM2.5 69 μg/m³", "268", "Wind", "Calm", "(", "0 - 2",
            "km/h", ")", "Smoke rises vertically.", "1 km/h", "Humidity", "Amount of moisture.", "72%", "Visibility",
            "Moderate visibility", "3 km", "Pressure", "Weight of the air.", "1011 mb", "Dew point", "Muggy.", "16°"};
        StringBuilder page = new StringBuilder("<html><script>var Wind='9 km/h';</script>");
        for (String p : parts) page.append("<span>").append(p).append("</span>");
        Map<String, String> m = Msn.parse(page.toString());
        check("21".equals(m.get("temp")), "temp");
        check("Partly cloudy".equals(m.get("condition")), "condition");
        check("22°".equals(m.get("feels")), "feels like");
        check("Expect sunny skies. The high will be 32°.".equals(m.get("summary")), "summary");
        check("268".equals(m.get("aqi")) && "Poor air quality".equals(m.get("aqiBand")), "AQI and band");
        check("PM2.5 69 μg/m³".equals(m.get("pollutant")), "pollutant");
        check("1 km/h".equals(m.get("wind")), "wind skips the 0 - 2 range and the script");
        check("72%".equals(m.get("humidity")) && "3 km".equals(m.get("visibility")), "humidity, visibility");
        check("1011 mb".equals(m.get("pressure")) && "16°".equals(m.get("dew")), "pressure, dew point");
        check(Msn.parse("<p>nothing</p>").isEmpty(), "empty page");
        System.out.println("MsnTest ok");
    }
}
