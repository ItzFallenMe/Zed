# Security policy

## Supported versions

Security fixes are applied to the latest commit on `main`. Releases may be
published from tagged commits when a release process is established.

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Contact the
maintainer privately through the email address listed on the repository owner
profile and include:

- a clear description of the issue and its impact;
- the affected commit, screen, or Android version;
- reproducible steps or a minimal proof of concept; and
- any suggested mitigation.

Do not include real API keys, phone numbers, contact data, or private audio in a
report. Remove secrets before sharing logs.

## Secret handling

Zed stores a user-entered Gemini key in private app preferences for local use.
Contributors must never place a key in source, Gradle files, screenshots,
commits, CI logs, or issue comments. Public deployments should use ephemeral
tokens or a backend proxy.
