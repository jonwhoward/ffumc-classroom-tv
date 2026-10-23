# Franklin First classroom TV

A home screen for the Sunday school Fire TVs (Toshiba C350, Fire OS 8): the church logo, a clock,
the room and the class meeting in it, and one big button per HDMI input.

- **Settings:** `tv/classroom.conf` — buttons, help email address and the Sunday class schedule.
  Edit it here; every new build includes it.
- **Install on a TV:** download `ffumc-classroom-tv.apk`, `install.sh` and `classroom.conf` from
  the latest release into one folder on the Mac, then run
  `bash install.sh <room> <tv-ip>`, e.g. `bash ~/Downloads/install.sh B138 192.168.10.125`.
- **Undo:** `bash install.sh --undo <tv-ip>`.
- **Staff escape:** press the remote's Menu button three times to reach Amazon's home and settings.

The Home-button takeover (`Guard.java`) and input screen (`Input.java`) come from
[dumbtv](https://github.com/humblemuzzu/dumbtv), MIT licensed — see `LICENSE.dumbtv`.
GitHub Actions builds the app on every push to `main` and publishes it as the `latest` release.
