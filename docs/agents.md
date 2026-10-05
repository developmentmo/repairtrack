# Agent pipeline

Four Claude agents take a request from Slack through to staging. They do not talk to each other directly. They hand
work over through **GitHub issues, labels and pull requests**, so every step is visible and traceable, and a human
can step in at any point.

```text
Slack ─► triage agent ──issue + label agent:backend / agent:frontend──► backend agent / frontend agent
        (Claude scheduled task)                                            │ branch agent/<area>-<n>, pull request
                                                                           ▼
                               CI ──red──► same agent fixes it (at most AGENT_MAX_FIX_ROUNDS rounds)
                                │
                              green
                                ▼
                     scope gate ──PR touches other files──► agent:needs-human
                                │
                                ▼
                     review agent ──changes──► same agent fixes them
                                │       └──needs_human──► agent:needs-human
                             approve
                                ▼
                     squash merge to main ──► CD: staging (automatic) ──► production (your approval)
                                │
                                └──► triage agent reports back in the Slack thread
```

| Agent | Where it runs | Instructions | Can change |
|---|---|---|---|
| Triage (Slack) | Claude scheduled task, 08:07/12:07/16:07/20:07 Amsterdam time | [`.claude/agents/triage.md`](../.claude/agents/triage.md) | Slack messages (prefixed `🤖 RepairTrack-assistent namens Iljaas:`), GitHub issues |
| Backend | [`agent-worker.yml`](../.github/workflows/agent-worker.yml) | [`.claude/agents/backend.md`](../.claude/agents/backend.md) | `backend/**` |
| Frontend | [`agent-worker.yml`](../.github/workflows/agent-worker.yml) | [`.claude/agents/frontend.md`](../.claude/agents/frontend.md) | `app/**` |
| Review | [`agent-review.yml`](../.github/workflows/agent-review.yml) | [`.claude/agents/reviewer.md`](../.claude/agents/reviewer.md) | nothing; merges, comments and labels only |

All of them follow [`CLAUDE.md`](../CLAUDE.md).

## Labels

| Label | Meaning |
|---|---|
| `agent:backend`, `agent:frontend` | Starts that agent on the issue. You can also add it yourself to an issue you wrote. |
| `agent:fix` | Add it to an agent PR by hand to make the agent address the latest comments. |
| `agent:blocked` + `area:<x>` | Waits for another agent's PR. The review agent releases it after the merge (`Unblocks: #n` in the PR). |
| `agent:needs-human` | An agent stopped. Read its comment, decide, then continue by hand or remove the label and add `agent:fix` / `agent:<area>`. |
| `source:slack`, `slack:notified` | Issues from Slack, and whether the Slack thread has been told the outcome. |

## Safety

- **Only trusted actors can start an agent.** Only people with write access, and the Claude GitHub App for
  follow-ups, can add labels. claude-code-action also checks this itself (`allowed_bots` contains only the Claude
  app).
- **Workflow definitions cannot be changed by a pull request.** `workflow_run`, `issues` and `pull_request_target`
  always use the workflows on `main`, so an agent PR cannot alter the pipeline that judges it.
- **Hard scope.** The settings file [`.claude/agent-settings.json`](../.claude/agent-settings.json) denies edits to
  `.github/`, `.claude/`, `CLAUDE.md`, `deploy/` and `docs/operations.md`, and reading `.env` files. On top of that,
  the deterministic scope gate in `agent-review.yml` refuses every PR that changes files outside `backend/` or `app/`,
  or touches secrets or signing material.
- **What is reviewed is what is merged.** The review agent merges with `--match-head-commit <sha>`, exactly the
  commit that CI tested.
- **Production still needs you.** A merge only reaches staging. Production keeps the required reviewer on the
  `production` environment.
- **Loops are bounded.** There are at most `AGENT_MAX_FIX_ROUNDS` fix rounds per PR (default 3), then
  `agent:needs-human`.
- **Slack text is data, not instructions.** The triage agent refuses anything other than bug reports and change
  requests, and reports such messages to you.

Remaining risk: the agents run code they wrote themselves (the tests) on a GitHub runner that holds the Anthropic
key. Keep the key in a separate Anthropic workspace with a spend limit (see Costs).

## Setup (once)

1. **Install the Claude GitHub App** (https://github.com/apps/claude) on `developmentmo/repairtrack`.
2. **Add one repository secret** (Settings → Secrets and variables → **Actions** → tab **Secrets** →
   **New repository secret**; not an environment secret, and not under Codespaces or Dependabot):
   - `ANTHROPIC_API_KEY`: an API key from the Claude Console, billed per use. Put it in its own workspace with a
     monthly spend limit.
   - or `CLAUDE_CODE_OAUTH_TOKEN`: uses your Claude subscription (Pro/Max). Generate it with `claude setup-token`.
3. **Run Actions → "Agent - setup labels" → Run workflow.**
4. **Check the branch protection of `main`.** If it requires an approving review, the review agent cannot merge.
   Either leave approvals off (required status checks are fine), or put the Claude app on the bypass list.
5. **Optional repository variables:**

   | Variable | Default | |
   |---|---|---|
   | `AGENT_MODEL` | `claude-sonnet-5` | model for the backend and frontend agents |
   | `AGENT_REVIEW_MODEL` | `claude-opus-5-5` | model for the review agent |
   | `AGENT_MAX_TURNS` | `80` | maximum turns per agent run |
   | `AGENT_MAX_FIX_ROUNDS` | `3` | fix rounds per PR before `agent:needs-human` |
   | `AGENT_BOT_LOGIN` | `claude[bot]` | login of the Claude GitHub App bot |
6. **Triage agent:**
   - connect Slack in Claude (Settings → Connectors);
   - give the Claude cloud environment access to this repository;
   - create the scheduled task with the prompt from `.claude/agents/triage.md`, filled in with the channel to watch.

**Test:** create an issue such as "[frontend] Typo in the login screen", add `agent:frontend`, and follow it under
Actions.

## Pausing and switching off

- **Everything on GitHub:** Actions → "Agent - implement" and "Agent - review" → `⋯` → Disable workflow.
- **One PR or issue:** add `agent:needs-human`.
- **Slack:** pause the scheduled task in Claude.

## Costs

- Each agent run uses Anthropic API tokens. A small fix costs a few tens of cents; a larger feature with tests and
  a few fix rounds can cost a few euros.
- It also uses GitHub Actions minutes: backend runs include the full `mvnw verify`.
- The triage agent runs on your Claude subscription.
- Limit spend with a spend limit on the API key, `AGENT_MAX_TURNS`, and `AGENT_MAX_FIX_ROUNDS`.
