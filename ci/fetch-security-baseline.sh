#!/bin/sh
# Downloads the security-v1 bundle this repository is pinned to, verifies it,
# and renders the waivers declared in .quality.yaml into the ignore file the
# blocking gates read.
#
# Usage (from a Drone step, image alpine:3.21):
#   sh ci/fetch-security-baseline.sh
#
# The version is read from .quality.yaml, not passed in: it belongs in the
# reviewable contract, not in a pipeline argument. Reads from standards-local
# are anonymous, so no credential is involved.
#
# This lives in the repository rather than being inlined into the pipeline so
# that the logic is reviewable on its own, and so the `\1` backreference below
# survives: Drone strips backslash-digit from step commands before the shell
# sees them, but leaves files in the checkout alone.
#
# Correctness does not depend on this file being right: a broken or truncated
# download fails the SHA256SUMS check, and the build stops.
#
# See ChillCode/development-standards, security/README.md.
set -eu

REPO="${SECURITY_BUNDLE_REPO:-https://artifacts.chillcode.de/api/v1/repositories/standards-local/download/chillcode/security-v1}"

if [ ! -f .quality.yaml ]; then
  echo "No .quality.yaml found in the repository root." >&2
  exit 1
fi

# Quote-agnostic on purpose: Prettier rewrites "1.1.0" to '1.1.0' in YAML, and
# the format gate must not be able to break the security gate. Scoped to the
# security: block so the prGate: block cannot capture it.
VERSION=$(sed -n '/^security:/,/^[^[:space:]]/p' .quality.yaml \
  | sed -n 's/^[[:space:]]*artifactVersion:[[:space:]]*\(.*\)$/\1/p' \
  | head -n1 | sed 's/#.*//' | tr -cd '0-9.')

# A partially matched value is worse than none: it would pin a version nobody
# declared. Anything that is not x.y.z is treated as missing.
case "$VERSION" in
  *.*.*) ;;
  *) VERSION="" ;;
esac

if [ -z "$VERSION" ]; then
  echo "security.artifactVersion is not declared in .quality.yaml." >&2
  echo "Pin an exact published security bundle version, for example:" >&2
  echo "" >&2
  echo "  security:" >&2
  echo "    baseline: security-v1" >&2
  echo '    artifactVersion: "1.1.0"' >&2
  exit 1
fi

echo "security-v1 bundle: $VERSION"
rm -rf .chillcode-security
mkdir -p .chillcode-security

# busybox wget exits non-zero on 404, so a missing or unpublished version fails
# the build instead of scanning against nothing.
#
# Backing off rather than hammering: Artifact Keeper answers a burst of requests
# with 429, and a bundle is several files in a row. Short fixed retries gave up
# inside the rate-limit window and failed a blocking gate on a condition that
# clears by itself.
fetch() {
  n=0
  until wget -q -O ".chillcode-security/$1" "$REPO/$VERSION/$1"; do
    n=$((n + 1))
    if [ "$n" -ge 6 ]; then
      echo "Could not download $1 from $REPO/$VERSION" >&2
      exit 1
    fi
    sleep $((n * 5))
  done
}

# SHA256SUMS is the bundle's own index, so this script carries no hardcoded
# file list that goes stale when a bundle version gains a file.
fetch SHA256SUMS
while read -r _ name; do
  [ -n "$name" ] || continue
  mkdir -p "$(dirname ".chillcode-security/$name")"
  fetch "$name"
done < .chillcode-security/SHA256SUMS

# Fails the build if a single byte differs from what was published.
(cd .chillcode-security && sha256sum -c SHA256SUMS)

# Waivers: the SEC-* exceptions in .quality.yaml become the ignore file the
# blocking gates read. The renderer ships with the bundle and was just
# checksum-verified, so this repository cannot substitute a looser one. It
# refuses a waiver that is expired, unscoped, over its policy ceiling, or for a
# policy that does not allow waivers at all.
if [ -f .chillcode-security/render-waivers.sh ]; then
  apk add --no-cache yq >/dev/null
  sh .chillcode-security/render-waivers.sh
fi
