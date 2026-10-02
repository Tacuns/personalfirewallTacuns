# Security policy

## Supported versions

Security fixes are made for the **latest released version** of TacUNS Firewall only.

## Reporting a vulnerability

Please **do not open a public issue** for a security problem.

Send a private report to **support@tacuns.net** with:

- what the problem is and which part of the app it affects,
- the steps to reproduce it (Android version, device, app version),
- what an attacker could do with it.

You will get a reply when the report has been reviewed. Please give us reasonable time to fix
the problem before you publish details.

Areas where a report is especially valuable: the DNS and VPN path (`app/src/main/java/com/sentinel/core/vpn/`),
the rule decision (`core/rules/`), stored data and logs (`core/logs/`), and App Lock (`ui/lock/`).
