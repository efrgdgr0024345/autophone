# Live OpenAI checks — 15 October 2026

All six revised cases passed using the securely injected `BLACKCAT_TEST_API_KEY2`.
No key value was logged, saved, exported, sent to an emulator or packaged in an APK.
Model metadata requests returned HTTP 200 for `gpt-6.1-sol`, `gpt-6-luna` and
`gpt-4o-mini-tts`; generation used the configured default and the speech model.

| Case | Live result |
| --- | --- |
| Detailed Ubuntu security plan | 18 commands, nine roadmap phases; 65.04 seconds; production parser and Full Auto grammar accepted every command |
| Idle terminal preflight | Fresh shell prompt accepted; no command inferred |
| Clipped terminal page | Marked unreadable, visible marker/exit code identified, revision flagged |
| Readable numbered page | Correct marker, exit code and MORE state; all four body lines transcribed |
| Feedback-driven remaining plan | Five supported commands, four phases, including `cat /etc/os-release`; no mixed clarification/commands |
| Natural OpenAI speech | `gpt-4o-mini-tts`, `marin`, PCM; 422,400 bytes / 8.8 seconds, non-silent samples and accepted production content type |

## What was tested

The Java harness calls the actual compiled Kotlin `PlanCodec`, `AutoObservationCodec`
and `OpenAiSpeechCodec` to construct requests. A host-only Python HTTPS client uses
TLS verification, blocks redirects, sets bounded time/size limits and supplies the
injected secret through the supported OpenAI route. The actual JSON responses are
then parsed by the compiled production codecs. Returned commands also pass the
unchanged production `AutoRunPolicy.canRunReviewed` check. No returned command is
executed by this live harness.

The images are synthetic JPEGs of actual disposable Linux output from the
[earlier image loop](../../ui14.1/image-loop/README.md). Three independent requests
cover preflight, clipped output and readable output. They are not a continuous live
phone-camera/OpenAI/Bluetooth run. Speech bytes were checked on the host; audible
quality and phone playback were not established.

The paid batch ran against code at `887a98d37d23f95714aada1f5d094a885729198e`.
The final source `28fec6a187cca84f92fa14821167d660c5436eed` adds only pinned Cancel UI
and its native regression. All six request hashes were reproduced by the final
compiled codecs and all five live JSON responses were reparsed successfully, without
additional paid requests. [result.json](result.json) records both source identities.
The earlier build hash in that file identifies the live wire-test build, not the
final downloadable APK; the final APK hash is in the main verification file.

## Failures that led to UI14.2

The [initial batch](initial-ui14.1-failures.json) passed all image and speech cases,
but its detailed plan hit the 60-second deadline. An identical request with a
longer host-only deadline completed in [90.4 seconds](initial-plan-latency-diagnostic.json),
returning 17 commands and ten phases. Its original deadline would discard that plan.
The initial replan was valid JSON but included wildcard arguments and `apt-cache`,
which Auto correctly rejects. These were real failures, not passing simulations.

UI14.2 allows a bounded 180-second planning read and a 195-second outer deadline;
photo and speech deadlines remain unchanged. Model instructions now explicitly
exclude unlisted tools, quoted wildcards, prohibited file paths and unsupported
subcommands. The command policy itself was not relaxed. Concise field guidance
reduces repetition while preserving complete roadmaps. Cancel is pinned above the
bottom navigation during requests, including Results with a collapsed goal.

There were two six-case paid batches and one separately diagnosed planning request;
no automatic paid retries. This does not guarantee all future model outputs will
satisfy policy or that the account will never encounter billing/rate limits.

The request files contain synthetic input and no authorization headers. Published
response extracts retain status, model, usage and message content while omitting
provider identifiers. The harness sources and original result summaries are included
for review. Audio and credentials are not published.
