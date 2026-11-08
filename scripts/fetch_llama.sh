#!/usr/bin/env bash
# Clones llama.cpp (MIT) at the pinned tag into external/llama.cpp.
# The tag lives in scripts/llama_cpp_tag.txt so CI and local builds always match.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TAG="$(tr -d '[:space:]' < "$ROOT/scripts/llama_cpp_tag.txt")"
DEST="$ROOT/external/llama.cpp"
if [ -f "$DEST/CMakeLists.txt" ] && [ "$(git -C "$DEST" describe --tags 2>/dev/null || true)" = "$TAG" ]; then
  echo "llama.cpp $TAG already present"; exit 0
fi
rm -rf "$DEST"
git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$DEST"
echo "llama.cpp $TAG ready at $DEST"
