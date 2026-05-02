#!/bin/sh

set -eu

repo_root="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

git -C "$repo_root" config core.hooksPath "$repo_root/.githooks"
chmod +x "$repo_root/.githooks/commit-msg"

echo "Git hooks installed from $repo_root/.githooks"
