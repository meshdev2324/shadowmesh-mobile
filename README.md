# ShadowMesh Mobile Native

Native Android and iOS applications for the ShadowMesh VPN service.

## Project Structure

- `android/`: Android application source code (Kotlin, Jetpack Compose).
- `ios/`: iOS application source code (Swift, SwiftUI).
- `.github/workflows/`: CI/CD pipelines for automated builds, tests, and security scans.
- `.husky/`: Git hooks for linting and commit message validation.

## Development Setup

### Prerequisites

- **Android**: Android Studio Koala+, JDK 17.
- **iOS**: Xcode 15+, macOS.
- **Node.js**: For Git hooks and security tooling.

### Initializing Git Hooks

```bash
npm install
```

This will set up Husky and Commitlint.

## CI/CD Workflows

The following workflows are configured in `.github/workflows/`:

- **Android CI (`android.yml`)**: Builds debug APKs, runs unit tests and linting on every push/PR to `main` or `develop`.
- **iOS CI (`ios.yml`)**: Builds the iOS app and runs tests (requires macOS runner).
- **Security Scan (`security.yml`)**: Runs Gitleaks to prevent accidental secret leaks.
- **Release (`release.yml`)**: Automatically builds a release APK and creates a GitHub Release when a tag starting with `v` is pushed.

## Git Workflow

We use [Conventional Commits](https://www.conventionalcommits.org/) for commit messages. This is enforced by `commitlint` via a Husky hook.

Example:
`feat(vpn): add wireguard connection stats polling`

## Security

- Hard-coded secrets and URLs have been moved to `Config` objects in both Android and iOS projects.
- `gitleaks` is integrated into the CI pipeline to detect accidental leaks.
- Use `npm run check-secrets` to run a local scan.
