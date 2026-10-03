# Foz Personal Assistant — Roadmap

## Current Status (shipped, branch `feature/personal-assistant`)

- **Phase 1** — Infra: MediaPipe tasks-genai 0.10.35 (Gemma 3 1B int4, GPU→CPU fallback),
  SAF model picker + copy, Settings "Personal Assistant (Experimental)" section,
  floating mic button, AssistantPanel with typed-text Q&A
- **Phase 2** — Voice: system SpeechRecognizer (partial results, error mapping),
  TTS spoken replies, mid-conversation RECORD_AUDIO flow
- **Phase 3** — Tools: 10 tools (get/create events, notes CRUD+search, weather,
  open_app, alarm/timer), prompt-based JSON tool calling with validation,
  max-3-iteration orchestrator, 8 parser unit tests
- **Phase 4** — Polish: volume-down long-press (only when assistant ready),
  smart mic flow (loads model when selected), context safeguards
  (1280-token budget, 6-turn history, 500-char truncation)
- **Phase 5** — 25 tools (launcher prefs, deep links, messaging/dialer, contacts,
  media, notifications), NeedsConfirmation gating for risky actions with
  Yes/No chips in the panel, resumable orchestrator (SuspendedQuery/ResumeAction:
  permission grant or confirmation resumes the exact suspended tool call),
  generic runtime-permission dispatch (any permission, incl. READ_CONTACTS),
  dedicated AssistantSettingsActivity (main Settings shows one entry button),
  READ_CONTACTS opt-in
- **Build/size** — arm64-v8a-only abiFilters + R8 minify/shrinkResources
  (universal release had ~108 MB of 4-ABI MediaPipe native libs → per-ABI ~40 MB),
  MediaPipe keep rules; fixed `f != java.lang.Long` crash in ModelSetupSheet
  (Long/Long division fed to `%f` format)
- **Memory management** — model unloads after 3 min idle (default), on
  TRIM_MEMORY pressure (unconditional at RUNNING_LOW+/background), or via
  "Free memory now"; opt-in "Keep model in memory"; volume trigger + mic
  auto-reload from the file-ready state
- **Phase 7** — Reminders: ReminderRepository (DataStore JSON), exact-alarm
  scheduler with ~5 min inexact fallback (canScheduleExactAlarms), notification
  with Snooze +10 min / Dismiss, boot rescheduling, 3 tools (set_reminder with
  POST_NOTIFICATIONS mid-conversation gate, get_reminders, delete_reminder 🔒);
  ReminderTime pure trigger logic (stale dates roll to the next future
  occurrence, 5-year sanity cap) + JVM tests for trigger rules and repo JSON
- **Phase 8.1** — in-app HuggingFace model download in ModelSetupSheet: gated
  repo → user token (password-masked, stored in DataStore on-device only),
  resume via HTTP Range on the .part file, progress + cancel, 401/403 guidance;
  downloader unit-tested (Content-Range parsing); fixed broken
  "open download page" URL (litert-community/google/… → litert-community/…)
- **Phase 8.2** — conversation history persisted to DataStore (last 20 turns,
  tool activity excluded) and restored on process restart
- **Phase 8.3** — values-pt-rBR translations for all assistant-related strings

## Locked Decisions

| Topic | Decision |
|---|---|
| Messaging (WhatsApp/SMS) | Pre-filled compose; user taps send. No auto-send (no public API; AccessibilityService rejected as fragile/insecure) |
| Contacts | Opt-in READ_CONTACTS, requested mid-conversation |
| Risky actions (hide/delete/clear) | Deterministic Yes/No confirm chips in panel, no extra LLM turn |
| STT / TTS | System SpeechRecognizer / Android TextToSpeech (toggle in Settings) |
| HW trigger | Volume-down long-press, active when assistant enabled + model loaded **or** file-ready (auto-reloads) |
| Model memory | Launcher-first: model unloads after 3 min idle (default), on TRIM_MEMORY pressure (always at RUNNING_LOW+/background levels), or via "Free memory now". Opt-in "Keep model in memory" toggle. Volume trigger/mic auto-reload on demand |

---

## Phase 5 — Launcher superpowers + app actions + confirmations

### A. Launcher superpower tools (write existing DataStore prefs; UI reacts automatically)
| Tool | Args | Implementation |
|---|---|---|
| `set_theme` | mode: dark\|light\|system | PrefsManager.setThemeMode |
| `set_ad_block` | enabled: bool | setAdBlockEnabled (VM syncs VPN service) |
| `pin_app` | name, pinned: bool | setAppPinned + findInstalledApp |
| `hide_app` | name, hidden: bool 🔒confirm | setAppHidden |
| `rename_app` | name, new_name | setCustomAppName |
| `battery` | — | BatteryManager.BATTERY_PROPERTY_CAPACITY |
| `media_control` | action: play\|pause\|next\|previous | MediaControllerManager singleton |
| `notifications` | action: read\|clear 🔒(clear) | NotificationRepository singleton |

### B. App action tools (deep links)
| Tool | Args | Implementation |
|---|---|---|
| `web_search` | query | google.com/search?q= |
| `youtube_search` | query | youtube.com/results?search_query= |
| `navigate_to` | place | geo:0,0?q= |
| `open_url` | url | ACTION_VIEW browser |
| `send_message` | app: whatsapp\|sms, text, contact? or phone? | wa.me/<digits>?text= / sms:<num>?body= (pre-filled) |
| `call` | contact? or phone? | ACTION_DIAL (pre-filled, no auto-call) |
| `search_contacts` | query | ContactsRepository (READ_CONTACTS opt-in) |

### C. Confirmation framework
- New ToolResult: `NeedsConfirmation(tool, args, label)`
- Runtime state: `pendingConfirmation` → panel renders label + Yes/No chips
- Yes → dispatch tool (confirmed), feed result, resume loop; No → feed `{"cancelled": true}`, resume loop
- Orchestrator refactor: query loop becomes resumable (suspended userText + loopHistory + pending tool call)

### D. New files
- `data/ContactsRepository.kt` (ContactsContract phone lookup, permission check)

### E. Manifest / permissions
- `READ_CONTACTS`; MainActivity permission dispatch becomes a generic switch

---

## Phase 6 — Streaming responses
- `LlmEngine.generateStreaming(prompt, onPartial): String` + `cancelGeneration()`
- MediaPipe: `LlmInferenceSession.generateResponseAsync(ProgressListener<String>)` (0.10.35 API)
- Keep active-session ref; Stop button cancels via `cancelGenerateResponseAsync()`
- State: `partialAnswer` → panel shows growing assistant bubble; TTS speaks final only
- Panel: Send button ↔ Stop (square icon) while generating

## Phase 7 — Reminders
- `data/ReminderRepository.kt` — DataStore JSON {id, title, triggerAt, fired}
- `reminder/ReminderScheduler.kt` — setExactAndAllowWhileIdle if canScheduleExactAlarms() else setWindow (~5 min)
- `reminder/ReminderReceiver.kt` — notification (channel "assistant_reminders"), Snooze +10min / Dismiss actions
- `reminder/BootReceiver.kt` — reschedule unfired on BOOT_COMPLETED
- Tools: `set_reminder {title, time, date?}` (past time → tomorrow), `get_reminders`, `delete_reminder {query}` 🔒
- POST_NOTIFICATIONS requested mid-conversation (API 33+) via pendingPermission flow
- Manifest: RECEIVE_BOOT_COMPLETED, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM

---

## Phase 8 — High-value quality items
1. **In-app model download** — Settings: HuggingFace token field → direct download
   `gemma-3-1b-it-int4.task` with progress bar → save to filesDir/assistant
2. **Conversation memory** — persist last N messages to DataStore; restore on process restart
3. **pt-BR strings** — values-pt-rBR for all assistant strings (model already speaks pt)

## Phase 9 — Medium effort QoL
- Model manager: Qwen 0.5B (low-RAM) / Gemma 3 4B (flagship) choice per device
- Assistant overlay bubble from any app (SYSTEM_ALERT_WINDOW, opt-in)
- Orchestrator tests: fake LlmEngine for tool loop + permission pause/resume + confirmations
- CI: GitHub Actions — assembleDebug + testDebugUnitTest on PRs

## Phase 10 — Ambitious (backlog)
- Wake word "Hey Foz" (battery/privacy tradeoffs)
- On-screen context ("summarize this notification" with content in prompt)
- Rituals/routines ("good morning" → fixed template chain: weather + agenda)

---

## Risks & Mitigations
| Risk | Mitigation |
|---|---|
| ~27 tools strain 1B prompt following | Terse descriptions, context budget raised (2048 tokens); rare tools behind Settings "extended actions" toggle if quality drops |
| OEMs restrict exact alarms | Inexact setWindow fallback (~5 min drift), documented |
| wa.me without number | Opens WhatsApp contact picker (acceptable) |
| Low-RAM devices + bigger KV cache | Runtime RAM check in Model sheet; per-message truncation keeps prompts bounded |

## Verification per phase
- Each phase ends with `assembleDebug` + `testDebugUnitTest` green
- Parser/date/reminder-repo tests added with the code they cover
