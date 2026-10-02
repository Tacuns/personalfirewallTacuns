# TacUNS Personal Firewall

An on-device DNS firewall for Android. It blocks ads, trackers and harmful websites for every
app on the phone, lets you block individual apps on Wi-Fi or mobile data, and shows what each
app connects to — without an account, and without sending your activity to any server run by
this app.

This repository contains the firewall and security code of the TacUNS Firewall app.

## What it does

- **Website blocking** — your own block list (a rule for `example.com` also covers
  `www.example.com`), an *Always allow* list, country blocking, a blocking schedule, and a
  downloadable ad/tracker blocklist (StevenBlack hosts) plus up to 3 lists of your own.
- **App blocking** — block any app on Wi-Fi, on mobile data, or both.
- **Extra protection** — look-alike (typosquatting) site warnings and blocking, Safe Search
  for Google / Bing / DuckDuckGo and YouTube restricted mode, protection levels
  (Normal / Strict / Kids), and a watch-only mode that reports without blocking.
- **Your DNS choice** — Google, Cloudflare, the network's own servers, or a custom server,
  with an optional backup server.
- **Activity** — a log of which app looked up which website, with a plain-language reason for
  every block, an Allow/Block button with Undo, and a country map.
- **Security tab, alerts and change history**, App Lock (salted PBKDF2-SHA256, 600,000
  iterations), backup / restore, and 14 languages.

## How it works

TacUNS uses Android's [`VpnService`](https://developer.android.com/reference/android/net/VpnService)
to create a local tunnel on the phone — no remote VPN server is involved.

1. The phone's DNS server is set to an address inside the tunnel (`10.0.0.1`), so website
   look-ups reach the firewall first (`SentinelVpnService`, `DnsInterceptor`).
2. The app that made the look-up is identified with Android's `getConnectionOwnerUid`
   (`SessionManager`).
3. The rules decide (`RuleEngine`, `DomainCheck`): *Always allow* wins, then your own rules,
   then the blocklists.
4. Blocked names get a "does not exist" (NXDOMAIN) answer; allowed look-ups are sent to the DNS
   server you chose, by its address (`DnsForwarder`).
5. When at least one app is blocked, the tunnel also carries that app's traffic so it can be
   cut completely.
6. Each decision is written to the on-device activity log (`LogManager`), which you can keep
   for 6 hours to 30 days, or switch off.

The app UI and the firewall run in two processes; they share state only through the on-device
databases and a small options file.

## Known limitations

These come from how a DNS-based, no-root firewall works on Android:

- It filters **DNS look-ups**. An app that connects to a server by a fixed IP address, or that
  uses its own encrypted DNS, is not filtered by name.
- Blocklist entries match the **exact name only**; your own rules also cover sub-names.
- While at least one app is blocked, website blocking applies only to the blocked apps
  (Android allows one VPN tunnel and the routing has to choose).
- Android "Private DNS" in strict mode cannot be combined with the firewall; the app warns
  you when it is on.
- The tunnel is IPv4-only; Android blocks IPv6 traffic of apps that use the tunnel, so they
  fall back to IPv4.

## Privacy

- Rules, settings and the activity log stay on the phone and are excluded from Android cloud
  backup and device-to-device transfer.
- Network connections made by the app: DNS look-ups to the DNS server you chose (the map's
  country look-ups go the same way), and blocklist downloads from their public addresses.
- Backups are saved to the Downloads folder. The activity history is only added when you
  tick "Include activity history".
- No account, no analytics, no ads.
- Privacy policy: https://www.tacuns.net/apps/tacuns-firewall/privacy-policy

## Building

Requirements: JDK 17, Android SDK with platform 36.

```
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests
```

Create `local.properties` with your SDK path (`sdk.dir=...`) if Android Studio has not done it.
Release builds are not signed by this project's Gradle files; sign them with your own key.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).

The licence covers the source code. It does **not** grant permission to use the TacUNS name or
logo (Apache License 2.0, section 6). If you publish a modified version, please use your own
name and icon.

## Security

Please report vulnerabilities privately — see [SECURITY.md](SECURITY.md).
