# Two version targets to start: 1.8.9 and latest 1.21.x

The original brief called for 1.16 through 1.21.x plus 1.8.9, but a range is not a set — each minor version is its own mixin target, and every feature would be written and regression-tested six or seven times. Starting with 1.8.9 and the latest 1.21.x exercises both loader paths and covers where competitive players actually are, at the smallest maintenance surface that proves the pattern.

## Consequences

- Adding targets later is additive, so this is cheap to revisit — but only if features are written against a version-abstraction seam from the start rather than against one version's API directly.
- Recorded explicitly because it contradicts the written brief; the narrowing is deliberate, not an oversight.
