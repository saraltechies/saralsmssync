# Saral SMS Sync

See the SMS and OTPs from all your Android phones in one place, by [Saral Techies](https://saraltechies.com/).
Free and open source (GPL-3.0). No ads and no account.

**Status: early test version, not on Google Play.** Don't rely on it for anything important yet.

## How it works

1. Start a group on your first phone, then scan its QR code on each of your other phones.
2. On a phone whose SMS you want to see elsewhere, turn on **Send this phone's SMS**.
3. On a phone you want to read them on, turn on **Show SMS from my phones**. A phone can do both.

Each message shows which phone and SIM it arrived on. An OTP gets a one-tap copy button. Messages are deleted
from the app after 7 days. The original SMS on the receiving phone is never changed.

## Privacy and security

- **End-to-end encrypted.** Each SMS is encrypted with AES-256-GCM on the phone that receives it. Only phones
  holding the group key can read it.
- **The key never goes online.** It is created on your phone and passed to your other phones by QR code. On each
  phone it is protected by the Android Keystore and left out of backups.
- **The relay can't read messages.** Encrypted text passes through the public [ntfy](https://ntfy.sh) server,
  which keeps it for a few hours in case a phone is offline. It can see the encrypted text, a random topic name,
  your IP addresses, and when and how often messages are sent.
- **Forwarding shows on the phone.** Forwarding only starts after a clear warning on that phone, shows a permanent
  notification, and stops if the app's notifications are turned off. This code is public, so someone could build a
  copy without these checks: only install this app from us, and check which apps have SMS access on your phones.
- **Anyone with your QR code can read your group's messages.** Treat it like a password. If a phone is lost, leave
  the group on every phone and start a new one. Removing a single phone from a group isn't supported yet.

This code hasn't had an independent security review. Forwarding OTPs also weakens the protection of SMS-based
login, because a code can now be read on more than one device. Only use this on your own phones.

## Permissions

| Permission | Why |
| --- | --- |
| `RECEIVE_SMS` | Notices new SMS while forwarding is on. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Sends and receives encrypted messages. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_REMOTE_MESSAGING`, `POST_NOTIFICATIONS` | The permanent notification, and showing new messages. |
| `RECEIVE_BOOT_COMPLETED` | Carries on after a restart. |

The app deliberately doesn't ask for `READ_SMS`, `SEND_SMS` or call log access, and the build fails if it ever does.
It only sees SMS that arrive after forwarding is turned on.

## Known limits

- Android can hold back some automatic login codes from apps for hours. The app shows when a message arrived late.
- Some phone makers stop background apps. Remove battery restrictions for the app on each phone.
- If you install the APK yourself, Android may block SMS access until you choose **Allow restricted settings** in
  the app's settings page.
- A very long SMS loses its end, and is marked as shortened.
- Messages held by the relay expire after a few hours, so a phone offline for longer can miss them.

## Building

Requires JDK 17 and the Android SDK. `./gradlew testDebugUnitTest assembleDebug` runs the tests and builds the app.
The **SMS round trip** GitHub Actions workflow sends real SMS to emulators, through the relay and back.

## License

GPL-3.0. See [LICENSE](LICENSE). Report security problems as described in [SECURITY.md](SECURITY.md).
