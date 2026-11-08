#!/usr/bin/env bash
# JARVIS X API-independence guard. Runs in CI on every push; any failure blocks the build.
#  1. No cloud-AI / telemetry endpoint or SDK appears anywhere in the source.
#  2. Only ModelDownloader.kt may open network connections.
#  3. No networking or analytics library is declared as a dependency.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0
SRC="app core third_party"

echo "1) Cloud AI, telemetry and ad identifiers"
pattern='api\.openai\.com|generativelanguage\.googleapis\.com|aiplatform\.googleapis\.com|api\.anthropic\.com|api\.groq\.com|openrouter\.ai|api\.deepseek\.com|api-inference\.huggingface\.co|router\.huggingface\.co|api\.elevenlabs\.io|api\.mistral\.ai|api\.cohere|picovoice|firebase|crashlytics|google-services|gms\.permission\.AD_ID|com\.google\.ai\.client|generativeai'
if grep -RInE "$pattern" $SRC --include='*.kt' --include='*.java' --include='*.cpp' --include='*.h' --include='*.kts' --include='*.xml' --include='*.toml' --include='*.txt' gradle/; then
  echo "   FAIL: cloud AI or telemetry reference found above"; fail=1
else
  echo "   ok"
fi

echo "2) Network access only in ModelDownloader.kt and research/WebSearch.kt"
net='HttpURLConnection|openConnection\(|HttpsURLConnection|java\.net\.Socket|okhttp3|retrofit2|io\.ktor|WebSocket|URL\('
offenders=$(grep -RIlE "$net" app core third_party --include='*.kt' --include='*.java' \
  | grep -v -e 'app/src/main/java/com/jarvisx/app/models/ModelDownloader.kt' -e 'app/src/main/java/com/jarvisx/app/research/WebSearch.kt' || true)
if [ -n "$offenders" ]; then
  echo "   FAIL: network code outside ModelDownloader.kt:"; echo "$offenders"; fail=1
else
  echo "   ok"
fi

echo "2b) Optional search may only contact its allow-list"
hosts=$(grep -oE 'ALLOWED_HOSTS = setOf\([^)]*\)' core/src/main/kotlin/com/jarvisx/core/research/Research.kt || true)
if [ "$hosts" != 'ALLOWED_HOSTS = setOf("news.google.com", "en.wikipedia.org", "hi.wikipedia.org")' ]; then
  echo "   FAIL: search allow-list changed or missing: $hosts"; fail=1
elif ! grep -q 'require(ResearchPolicy.isAllowed(url))' app/src/main/java/com/jarvisx/app/research/WebSearch.kt; then
  echo "   FAIL: WebSearch no longer enforces the allow-list"; fail=1
else
  echo "   ok"
fi

echo "3) No networking / analytics dependencies"
if grep -RInE 'okhttp|retrofit|ktor|firebase|crashlytics|play-services|google-services|sentry|bugsnag|amplitude|mixpanel|segment' --include='*.kts' --include='*.toml' . ; then
  echo "   FAIL: networking or analytics dependency declared"; fail=1
else
  echo "   ok"
fi

echo "4) Library modules do not request INTERNET"
if grep -RIl 'android.permission.INTERNET' core third_party 2>/dev/null; then
  echo "   FAIL: a library manifest requests INTERNET"; fail=1
else
  echo "   ok"
fi

[ "$fail" = 0 ] && echo "API-independence check passed." || { echo "API-independence check FAILED."; exit 1; }
