# Lab: AI architectures and measurement

The app started as a raw comparison of five inference tiers. The **Lab** turns it into a testbed for
AI *architectures* (what you build around a model) and for *measurement* (how well, how fast, how
expensive, how hot). Everything runs through the same five tiers, so any architecture can be tried on
any engine and compared.

## Foundation

| Piece | Where | What it does |
|---|---|---|
| `runStep` | `IInferenceRepository` | One isolated generation on a chosen tier with its own `GenerationConfig`. It does not touch the Chat conversation, serialises access per tier (a model can serve one request at a time) and records a metric. Pipelines are built from these steps. |
| `PipelineTrace` / `TraceStep` | `domain:models` | The visible record of a pipeline run: each step has a kind, the tier that ran it, timings, tokens and a short input/output. |
| `TraceRecorder` | `feature:lab` | Builds a trace as a `StateFlow` while a pipeline runs; the UI renders it live (`PipelineTraceView`). |
| `Experiment` registry | `feature:lab` | One entry per experiment; the hub screen is generated from it. |

## Experiments

Each experiment lives in its own package under `feature:lab` (UI + view model + pure logic) and is
registered in `ExperimentRegistry`. Pure logic has JVM unit tests.

| Id | Architecture / measurement | Core idea |
|---|---|---|
| `sandbox` | Single step | Run one prompt on a tier with a full config and watch the trace. Smoke test of the foundation. |
| `rag` | On-device RAG | Chunk and embed documents on the phone, retrieve by cosine similarity, answer with citations, compare with/without retrieval, judge groundedness. |
| `agent` | ReAct agent | Multi-step loop with several tools (time, calculator, device info, web search, notes, document lookup) and a visible thought/action/observation trace. Measures tool-use reliability per tier. |
| `router` | Hybrid edge/cloud | Classify the prompt, route it (local vs cloud), cascade with escalation on low confidence, and a PII gateway that redacts locally before anything reaches the cloud. |
| `refine` | Planner, worker, critic | Plan, execute, critique and revise; LLM-as-judge; self-consistency (N samples, majority vote); prompt-strategy comparison (zero-shot, few-shot, chain-of-thought). |
| `bench` | Benchmark suite | 22 tasks in 7 categories with deterministic scorers, run over every usable tier and repetition; ranking, per-category heatmap, latency bars, determinism, device columns, saved sessions you can compare (CPU vs GPU, before vs after) and CSV/JSON export. |
| `scaling` | Context scaling | Time to first token against prompt size per engine; the least-squares cost of a longer prompt in ms per 1000 characters. |
| `soak` | Thermal soak | Back-to-back generations for 1-10 minutes; speed over time, first-vs-last-third change, peak thermal status and coarse battery drain. |
| `memory` | Context management | Rolling summarisation and a long-term fact store to stay inside the 1k/2k token budgets of the small engines. |
| `guard` | Guardrails | Prompt-injection detection, poisoned-document demo for RAG, output validation. |

## Status

Implemented: foundation, `sandbox`, `rag`, `agent`, `bench`, `scaling`, `soak`, device measurements. In progress: `router`
(with the PII gateway) and `refine` (planner/critic, judge, strategies). Planned: `memory`, `guard`.
None of the UI has been run on a device yet; pure logic is covered by JVM tests with fake executors.

## Mobile-specific measurements

Every generation records, besides latency and tokens/s, the change in process memory (PSS, which includes the native
memory of a loaded model), the battery charge counter and the thermal status (`DeviceProbe`, `GenerationMetric`, Room v5,
CSV export). The battery counter is coarse and only meaningful summed over many runs; `soak` and `bench` aggregate it.
Cold vs warm start is the separate `loadTimeMs`.

## Conventions

- Repo content (code, comments, docs, commits) is in English; UI strings stay in Spanish.
- A pipeline step never calls a backend directly: it goes through `runStep`.
- Anything non-deterministic is measured over several repetitions and reported with spread, not a single number.
- Pure logic (chunking, scoring, parsing, routing heuristics) must be unit-tested; UI is verified by building and, where possible, running.
