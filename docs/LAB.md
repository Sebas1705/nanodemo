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
| `bench` | Benchmark suite | Fixed task sets with automatic scorers run over every available tier and repetition; leaderboard, per-task heatmap, latency distribution, determinism, context-scaling curve. |
| `memory` | Context management | Rolling summarisation and a long-term fact store to stay inside the 1k/2k token budgets of the small engines. |
| `guard` | Guardrails | Prompt-injection detection, poisoned-document demo for RAG, output validation. |

## Mobile-specific measurements

Beyond latency and tokens/s: battery charge and thermal status deltas per run, process memory (PSS),
cold vs warm start, and energy per 1k tokens where the device exposes it. See the `bench` package.

## Conventions

- Repo content (code, comments, docs, commits) is in English; UI strings stay in Spanish.
- A pipeline step never calls a backend directly: it goes through `runStep`.
- Anything non-deterministic is measured over several repetitions and reported with spread, not a single number.
- Pure logic (chunking, scoring, parsing, routing heuristics) must be unit-tested; UI is verified by building and, where possible, running.
