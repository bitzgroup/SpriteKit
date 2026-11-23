# Contributing to SpriteKit for Android

Thanks for your interest in contributing! This library is a Kotlin/Android port of Apple's
SpriteKit framework. The goal is to behave like the Apple framework it mirrors, so most changes are about
closing a gap with Apple's real behavior.

## Branches and pull requests

This repo uses a Gitflow-style model: branch `feature/<name>` off `develop` and open your pull
request against `develop`. `main` only receives `release/*` and `hotfix/*` merges. CI must pass
before a pull request can be merged.

## Before opening a pull request

Run the same checks CI runs:

```sh
./gradlew ktlintCheck detekt assemble testDebugUnitTest
```

- Add or update unit tests for any behavior change.
- Public API gets KDoc.
- If your change intentionally differs from Apple's API shape or behavior, or knowingly leaves a
  gap, record it in `docs/API_COMPATIBILITY.md`.
- When the change is about matching Apple's behavior, say in the pull request how you know what
  Apple does (Apple's documentation, or a probe run against the real framework).

## Writing for readers outside the project

This is public OSS: comments, KDoc/doc comments, docs, commit messages, and PR descriptions are
read by people who have none of the maintainers' context. Write for them.

- **Describe the code as it is now, not how it got here.** Explain what something does and why.
  Don't use roadmap phase numbers ("added in Phase 2"), PR/issue numbers, or "previously"/"used
  to"/"now" as explanations in code comments. That history belongs in commit messages, PRs, and
  `docs/ROADMAP.md`.
- **Don't leave forward-looking notes that will go stale.** Avoid "until X is wired up" or "will
  be added later" in comments. If a limitation is real, state it plainly and record it in the
  docs (see below); when it's lifted, update or delete the comment in the same change.
- **Cross-references must resolve.** Pointing at a doc section by its heading (for example
  "see `docs/ARCHITECTURE.md`'s "Touch event routing" section") is fine — but make sure the
  section exists, and update references when you rename one.
- **English only** for docs, comments, and commit messages. (Localized UI strings are the only
  exception.)
- **No personal or machine-specific details.** No absolute local paths, email addresses, host
  names, tokens, or credentials. Never commit local configuration such as `local.properties`,
  `.claude/settings.local.json`, or `xcuserdata/`.
- **Don't name private consumer apps.** If a fix was found while building some other app against
  these libraries, describe it generically ("a host app").
