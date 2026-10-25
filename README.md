# BetterTV

A calm, ad-free home screen for a 3rd-gen streaming stick (model AFTSSS), made for an Indian home: the
time, Bing weather and AQI, today's tithi and festival, your apps as floating logos, live news,
and wallpapers you choose. Greetings in Angika, Maithili, Hindi or English.

Once installed it runs on the TV by itself. A computer is needed only to install it.

## What it does

- **Home screen**
  - Greeting for the time of day.
  - Large clock, and the date in Hindi.
  - Weather pill: temperature, AQI and a rain check.
  - Panchang pill: today's tithi and when it ends.
  - Wi-Fi, Options and Settings buttons.
- **Apps**
  - One row of white logos with a soft glow in the app's colour.
  - The selected app comes forward, and a line above the row changes each visit.
  - More apps holds everything else.
- **Live news**
  - Veto channels open straight into the live stream.
  - YouTube channels open their most-watched live stream in SmartTube.
  - "+ YouTube channel" searches YouTube and adds a channel. Move, rename and remove from the menu.
- **Weather**
  - MSN (Bing) Weather's own data, forecasts by Foreca, with India AQI.
  - Ten days, an hourly rain check, wind, humidity, UV, visibility, pressure, sun and moon.
  - Pick a city or search anywhere in India.
- **Panchang and festivals**
  - Tithi from Drik Panchang and Mithila Panchang.
  - Bihar, Mithila and pan-India Hindu festivals. On those days the greeting becomes a wish with the festival's blessing, and the screen can take the festival's colour.
- **Opening and coming back**
  - Opening an app: the logo rushes forward with "Bye 👋".
  - Coming back: "Welcome back 👋". Or Quick, in Options.
- **Home button**
  - The remote's Home button opens BetterTV.
  - Options › "Home button opens" switches back to the stock home screen and returns, right from the TV.
  - If the system ever drops it, BetterTV restores it itself.
- **Light on a 1 GB stick**
  - About 85–100 MB while on screen, about 10% dropped frames.
  - Wallpapers about 0.1 MB each as JPEG.
  - No endless animations.
- **Private**
  - No ads, no tracking, no accounts.
  - Internet is used only for weather, panchang and YouTube channel search.
  - Crashes are written to a file on the TV (Options › Crash reports) and sent nowhere.

## Install

You need a computer with `adb` (Android platform-tools), JDK 17 and the Android SDK (`platforms;android-25`, any build-tools), and the TV on the same network with ADB debugging switched on in the TV's Developer options.

```sh
adb connect <tv-ip>:5555                       # press Allow on the TV
./launcher/install.sh --build                  # build, install, take over the Home button
```

`install.sh --undo` gives the Home button back to the stock home screen. `adb uninstall tv.dumbtv.home` removes BetterTV.

## Make it yours

Everything lives in one file on the TV, `/sdcard/Android/data/tv.dumbtv.home/files/home.conf`, and most of it is also in Options.

| Setting | What it does |
|---|---|
| `language=angika` | `angika`, `maithili`, `hindi` or `english` greetings and festival wishes |
| `apps.dock=…` | the apps in the home row, in order |
| `live.yt=ID\|Name,…` | YouTube channels in the live row |
| `tagline.<package>=Line one\|Line two` | lines above the row; they rotate each visit |
| `greet.morning=…` | replace a greeting (also `noon`, `evening`, `night`) |
| `festival.2027-11-04=दीपावली` | add or correct a festival day |
| `wallpaper.line.<file>=…` | the line shown under a wallpaper's name |
| `colours=festive` | `calm`, `festive` or `always` |
| `home.button=bettertv` | or `amazon` |

Wallpapers: put 1920×1080 JPEGs in `files/wallpapers/` on the TV (the folder's file names become their titles). A copy script and the wallpapers themselves are kept off this repo, because pictures belong to their owners.

## Build

```sh
cd launcher && ./build.sh         # launcher/build/dumbtv-home.apk
```

Plain Java and Android views, no libraries. Host tests for the parsers: see `.github/workflows/validate.yml`.

## Where the data comes from

| | Source | Notes |
|---|---|---|
| Weather and AQI | MSN (Bing) Weather's data service, forecasts by Foreca | Unofficial. The key is read from MSN's page at run time. Falls back to Open-Meteo. |
| Weather fallback | [Open-Meteo](https://open-meteo.com) | free, no key |
| Tithi | [Drik Panchang](https://www.drikpanchang.com) (New Delhi) | |
| Panchang and Mithila festivals | [Mithila Panchang](https://mithilapanchang.com) | fetched once per day |
| Place search | Open-Meteo geocoding | |
| Font | [Outfit](https://fonts.google.com/specimen/Outfit) | SIL Open Font License |

## Credits

BetterTV started from [dumbtv](https://github.com/humblemuzzu/dumbtv) by humblemuzzu and contributors (MIT): its home-screen base, the Home-button takeover, and the optional ad and tracker clean-up in `scripts/` all come from there. The clean-up is documented in [docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md). Scenery wallpapers used on our TV come from [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT).

Not affiliated with or endorsed by any TV or streaming company; product names belong to their owners.

## Licence

MIT, see [LICENSE](LICENSE).
