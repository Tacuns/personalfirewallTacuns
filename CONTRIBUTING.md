# Contributing to TacUNS Firewall

Thank you for helping. Before you start:

## Reporting problems

- **Security problems:** please follow [SECURITY.md](SECURITY.md) — do not open a public issue.
- **Bugs:** open an issue with your Android version, device, app version and the steps to
  reproduce. Please do not include your activity log or other personal data.

## Code changes

1. Build and test before opening a pull request:
   ```
   ./gradlew assembleDebug testDebugUnitTest
   ```
2. Keep a change small and focused on one thing, and explain *why* in the pull request.
3. Match the style of the surrounding code (Kotlin, Jetpack Compose).
4. The firewall path (`core/vpn/`, `core/rules/`) decides whether users stay protected and
   online — changes there need a clear explanation and tests.
5. Every text a user sees is translated into all 14 languages in `app/src/main/res/values*/`.
6. Only submit code you wrote yourself or that is compatible with the Apache License 2.0.
   Do not copy code from GPL-licensed projects.

## Licence of contributions

By submitting a contribution you agree that it is licensed under the Apache License 2.0, the
same licence as this project (see section 5 of the [LICENSE](LICENSE)).

## Code of conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
