# Foz Backlog

Ideas parked for future sessions, roughly in priority order. Research results
from Oct 2026 included so we don't re-verify.

## Big bets

### LiteRT-LM runtime migration
Current MediaPipe LLM Inference engine only runs legacy `.task` exports.
Migrating to LiteRT-LM (`.litertlm`) unlocks ~60 modern models from
litert-community: Qwen3-0.6B/1.7B/4B/8B, gemma-4 E2B/E4B/12B, Ministral-3-3B,
Granite 4.x, LFM2.5, Falcon-H1, SmolLM3, Spark-X. New dependency + different
API + new file format. This is the path to a genuinely capable on-device
assistant; everything below benefits.

### Screen context — read-only (Phase B)
- `FozAccessibilityService` (`canRetrieveWindowContent`): walk
  AccessibilityNodeInfo of active window → compact text snapshot (labels,
  content-descriptions, editable fields), cap ~2-3k chars, transient
  (never stored/logged — matches memory privacy policy).
- Opt-in via system Accessibility settings (deep-link + onboarding card).
- Inject "Current screen: ..." into assistant prompt when invoked from the
  bubble; `read_screen` / `summarize_screen` tools.
- Persistent "Foz is reading your screen" notification while active.

### Screen agent — act (Phase C, after Phase B)
Same AccessibilityService + `dispatchGesture()` (API 24+ OK) for taps/swipes,
`ACTION_CLICK`/`ACTION_SCROLL_FORWARD` on matched nodes,
`GLOBAL_ACTION_BACK/HOME`.
- Tool grammar: `screen_tap {text}`, `screen_scroll {direction}`,
  `screen_back`, `screen_home`.
- Agent loop: read screen → model emits ONE action → execute → re-read →
  repeat; max ~5-8 steps per request.
- Separate explicit toggle from read-only; transparency notification required.
- Reliability: moderate with Qwen 1.5B (simple flows: open/tap/scroll), much
  better after LiteRT-LM (Qwen3-4B class). Text-only models — no screenshots.

## Assistant UX

### Rituals / routines ("good morning")
Fixed template chains: weather + today's agenda + pending reminders in one
reply. All building blocks exist (tools + calendar + weather). Mostly prompt
orchestration + a trigger UI (suggestion chips or voice shortcut).

### Wake word "Hey Foz"
Battery/privacy tradeoffs; needs always-on mic model. Revisit after
LiteRT-LM migration (some litertlm ASR/KWS models may serve).

### Bubble polish
- Persist bubble position across restarts.
- Auto-restore bubble after boot (BOOT_COMPLETED + overlay permission check).
- Long-press bubble menu (dismiss, settings, voice).
- Long-press = voice input directly (skip chat panel).

## Hygiene

### CI (GitHub Actions)
assembleDebug + testDebugUnitTest on every push/PR. ~30 min.

### Orchestrator tests
Fake LlmEngine driving AssistantManager's tool loop: permission
pause/resume, confirmations, multi-tool chains, memory extraction gating.
Protects the most complex code in the app.

### Metered-network guard for model downloads
Qwen 1.5B is 1.6 GB; confirm or defer downloads on mobile data. Check
ModelDownloader for a NetworkType constraint / pre-download dialog.

## Deferred model candidates (no-token .task era)
- Phi-4-mini-instruct 3.8B (MIT, ungated): `Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task`
  ~4 GB download, ~5-6 GB RAM — flagship tier for big devices. Also has a
  q8_ekv4096 .task (4096 ctx!).
- DeepSeek-R1-Distill-Qwen-1.5B (MIT, ungated, 70k downloads): reasoning
  model, emits <think> chains — needs tag stripping + UX for long waits.
- Rejected: SmolLM-135M (weak quality, poor pt-BR), TinyLlama-1.1B
  (outclassed by Qwen 1.5B).

## Done (for reference)
- v1.3: model manager (4 specs), encrypted subject memory + recall_memory,
  mic button toggle + 24dp shift, settings nav-bar insets, cold-start app
  cache, overlay bubble + notification context (the `notifications` tool
  existed since Phase 5 — read/clear actions).
