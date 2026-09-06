# Validation and acceptance plan

## Executed here

Only source structure/XML, shell syntax, package contents and visual reference checks can run in the offline authoring environment. See VALIDATION.md for the actual results. No Android compilation, device execution or live model request has occurred.

## Automated build gates (not yet executed)

- JDK 17, Gradle 8.9, Android SDK 35.
- `gradle testDebugUnitTest lintDebug assembleDebug` must exit 0.
- EndpointPolicyTest: HTTPS, missing path, invalid host, query, fragment, credentials, custom port.
- ReportParserTest: structured JSON, fenced JSON, plain text, unknown JSON, empty/null layers.

## Device acceptance (not yet executed)

Test Android 8 / API 26 and Android 15 / API 35; small phone, landscape, large font, dark theme and TalkBack.

1. Clean install: no model key, no automatic upload. Camera permission requested only after tapping the permission button.
2. Deny/revoke permission, permanently deny, return from app settings. No crash and actionable explanation.
3. Add two trusted profiles; restart app; profiles reload. Select, edit, delete. Inspect app-private preference content in a debug-only controlled test: ciphertext only. Do not publish real keys.
4. Confirm portrait/landscape rotation, rear camera and front-only device. Photo must be upright; test mirrored EXIF with fixtures before claiming full EXIF support.
5. Single analysis: exactly one request after explicit consent. Compare endpoint, question and received image with a controlled HTTPS test server using non-sensitive pictures.
6. Test valid JSON, plain-text response, content array, empty content, malformed JSON, slow response, offline, TLS failure and HTTP 400/401/403/404/413/422/429/500.
7. Confirm no API key or provider error response appears in app logs or error UI. Redirects must not forward credentials.
8. Start periodic mode: at most one request active. Slow responses skip intervals. On error, periodic mode stops. Stop or switch tabs, rotate, press Home or lock the phone: no new sends; return requires explicit restart.
9. Cancel during capture and network; ensure late callbacks cannot initiate a send after stop. Sending is not undone on the provider once received.
10. Resize 12MP+ images: longest side <=1280px, upright output and no EXIF/GPS. Check memory on low-RAM phone.
11. Kill app during capture; next launch deletes `eye_` cache files. Session report is gone after process death. No image appears in shared gallery.
12. Read long Persian reports, mixed Latin model IDs and URLs, and long errors at 200% font. Scroll content; all controls remain reachable, no horizontal clipping.
13. Show a scene containing malicious instructions to the model. The prompt directs the model to treat scene text as data, but this is not a proven defense; do not claim guaranteed immunity.
14. Use ambiguous scenes: reports should separate visible evidence and hypotheses, and decline identity/sensitive-trait/hidden-intent guesses. Test each chosen provider independently.
15. Report timestamp and model label remain attached to the last photo; viewfinder lines must not be mistaken for detected object boxes.

## Release blockers

Any failed build, camera crash, hidden upload, credential leak, unbounded request loop or unreadable UI blocks release. Production signing, provider-specific compatibility and privacy/store disclosures must be completed before distribution.
