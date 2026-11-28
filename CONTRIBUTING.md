# Contributing to Tapwing

Open an issue for substantial proposals. Submit focused pull requests and
describe the change, what you tested and the origin of any third-party code
or assets. Before submitting code, run:

```sh
./gradlew testDebugUnitTest assembleDebug
git diff --check
```

## Licensing & Developer Certificate of Origin (DCO)

Tapwing is licensed under the **GNU General Public License v3.0 or later**
(`GPL-3.0-or-later`; see [LICENSE](LICENSE) and [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md)).
All contributions are accepted under `GPL-3.0-or-later` (including the Section 7
additional permission in [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md)).

Every commit must include a `Signed-off-by` line certifying the
[Developer Certificate of Origin (DCO)](https://developercertificate.org/):

```text
Signed-off-by: Your Name <you@example.com>
```

You can add this automatically with `git commit -s`. By adding `Signed-off-by`,
you certify that you wrote the contribution or otherwise have the right to
submit it under `GPL-3.0-or-later`. Identify any separately licensed portions
and their notices in the pull request.

See [TRADEMARKS.md](TRADEMARKS.md) for trademark and branding rules.
Keep keys, signing files and personal data out of commits and screenshots.
Use the [private reporting process](SECURITY.md) for vulnerabilities.
