# AndroidMediaBar

A home screen for **Android TV** inspired by the cross media bar of the PlayStation 3 era:
horizontal categories, vertical lists, an animated wave background and, as a bonus, support
for **real PS3 themes (`.p3t`)**: their wallpaper and their icons.

Built entirely with Jetpack Compose, designed for the remote (D-pad) first.

---

## Features

### The bar
- **Columns:** Settings, Photo, Music, Video, Games and Apps. Each installed app goes to a column
  automatically, based on its declared category and a few package-name hints.
- **Your layout:** hide the columns you don't use and move any app to the column you prefer.
- **App options:** long-press OK (or the Menu / Info key) to open, move, inspect or uninstall
  an app.
- **TV-friendly:** uses Leanback banners when available, and the bar hides behind side panels so
  the two never fight for attention.
- **Settings shortcuts:** Network, Bluetooth, Display, Sound, Date and time, Apps, Storage and
  Home screen.

### Theme customization
- **Theme color:** "Original" changes every month like the classic bar, or pick one of 13 colors.
- **Background animation:** classic wave, wave with sparkles, floating lights, still wave or none.
- **Wallpaper:** the animated theme color, your own picture, or the wallpaper of an installed PS3
  theme. Blur and brightness are adjustable.
- **Day/night cycle:** the background follows the time of day.
- **Live preview:** each value is previewed while you move through the list.

### PS3 themes (`.p3t`)
Choose **Theme customization → Import PS3 theme (.p3t)…**, pick the file, and then select
**Wallpaper → PS3 theme** and **Icons → PS3 theme**. Both can be switched back at any time.

What gets imported:

| From the theme | Used for |
|---|---|
| `bgimagetable` (HD wallpaper, SD as fallback) | Wallpaper |
| `icontable` (system icons) | Column icons and Settings entries |
| `infotable` (name, author, version) | Shown in the menus |

PS3 themes name their icons with the same fixed system IDs, so the mapping works for any theme:

| Launcher | PS3 icon ID |
|---|---|
| Settings / Photo / Music / Video / Games columns | `icon_setting` / `icon_photo` / `icon_music` / `icon_video` / `icon_game` |
| Apps column | `icon_network` (the PS3 column hosting the store and browser) |
| Theme customization / Columns and apps | `icon_theme_setting` / `icon_system_setting` |
| Network, Bluetooth, Display, Sound, Date and time | `icon_network_setting`, `icon_accessory`, `icon_display_setting`, `icon_sound_setting`, `icon_datetime_setting` |
| Apps, Storage, Home screen, All settings | `icon_gamedata`, `icon_usb`, `icon_user`, `icon_setting_item` |
| Apps with neither banner nor icon | `icon_default_h` |

Every entry has fallbacks: if a theme lacks an icon, the built-in one is drawn instead. The full
mapping lives in [`Ps3Icons.kt`](app/src/main/java/com/launcher/androidmediabar/ui/xmb/Ps3Icons.kt).

> **Which files work?** Any plain `.p3t`: community themes, or the `.p3t` that a PS3 or the
> RPCS3 emulator installs from a PSN theme package you own. Retail PSN `.pkg` files are encrypted
> and are intentionally **not** supported: install them with their license first.

---

## Getting started

**Requirements:** an Android TV device running **Android 16 (API 36)** or later, and Android
Studio with the bundled JDK (AGP 9.5, compile SDK 37).

```bash
git clone <this-repo>
cd AndroidMediaBar
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then make it your home screen: **Settings → Home screen** inside the launcher, or the system
default-apps settings. The app can also be opened like a normal app while trying it out.

To import a theme, the TV needs a file picker or file manager. Many TVs have none built in: any
file manager app from the store will do.

### Controls

| Key | Action |
|---|---|
| ◀ ▶ | Change column |
| ▲ ▼ | Move through the list |
| OK | Open |
| Long-press OK, Menu, Info | Options of the selected item |
| Back / ◀ in a panel | Go back one level |

---

## Under the hood

```
app/src/main/java/com/launcher/androidmediabar/
├── data/
│   ├── AppRepository.kt        installed apps, icons, banners, column guess
│   ├── ColumnLayoutRepository  hidden columns and per-app assignments
│   ├── ThemeRepository.kt      theme settings, wallpapers, PS3 icon storage
│   ├── Ps3ThemeImporter.kt     .p3t → wallpaper + icons + info
│   ├── P3tReader.kt            CXML container parser
│   └── GimDecoder.kt           GIM picture decoder
├── model/                      immutable UI models
└── ui/xmb/                     the bar, side panels, wave background, PS3 icon mapping
```

### The `.p3t` format, briefly
A `.p3t` is a big-endian **CXML** document (magic `P3TF`): a header with (offset, size) pairs
for the element tree, ID table, string table, int/float arrays and file table. Each element is
seven int32 (name, attribute count, parent, previous, next, first child, last child) followed
by 16-byte attributes (name, type, two int32). The types that matter here are int (1),
float (2), string (3), embedded file (6) and ID (7). Embedded files are often zlib-compressed.

Icons are **GIM** pictures. PS3 themes use 32-bit RGBA with rows padded to 16 bytes. The decoder
also handles 16-bit formats, 4/8-bit palettes and PSP-swizzled data. Everything is decoded on
the device with the standard library: no native code and no third-party dependencies.

### Tests
```bash
./gradlew :app:testDebugUnitTest
```
The parser tests run against real themes placed in `app/sampledata/`. That folder is
git-ignored (`*.p3t`, `*.pkg`, `*.rap`), so no theme is ever committed, and the tests are skipped
when it is empty.

---

## Disclaimer

This is an unofficial fan project, not affiliated with or endorsed by Sony Interactive
Entertainment. "PlayStation" and "PS3" are trademarks of their respective owners. The app ships
**no Sony assets or keys**: it only reads theme files that you provide, and does not decrypt PSN
content. Use themes you own or that their authors distribute freely.

## License

[MIT](LICENSE)
