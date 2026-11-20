# jolt-kimi-plugin

A Kimi Code plugin for Jolt/Clojure work. It registers two MCP servers that
you install separately:

- [jolt-nrepl-mcp](https://github.com/jlt-commons/jolt-nrepl-mcp): a
  persistent nREPL session. Eval with per-form output and syntax repair,
  load-file, lookup, completion, clojure.test runs, and starting/stopping
  `jolt nrepl-server` for the project.
- [jolt-lsp](https://github.com/jlt-commons/jolt-lsp): navigation over a
  project index, diagnostics, syntax check/repair, structural edits and
  refactors.

It also runs hooks that repair and lint each Jolt/Clojure file the agent
writes, with jolt-lsp; see Hooks below. Tool behaviour is documented in the
two projects.

## Requirements

On `PATH`:

- `jolt-nrepl-mcp` v0.4.1 or newer
- `jolt-lsp` v0.3.2 or newer
- `jolt` 0.8.25 or newer (`nrepl_start` runs `jolt nrepl-server`)

Older versions run, but a call that names no project acts on the plugin's
own directory instead of being refused.

Download the binaries from the
[jolt-nrepl-mcp](https://github.com/jlt-commons/jolt-nrepl-mcp/releases) and
[jolt-lsp](https://github.com/jlt-commons/jolt-lsp/releases) releases
(macOS arm64, Linux x86_64), or build them from a checkout with `jolt dist`
(output in `target/`), and put them on `PATH`:

```bash
tar xzf jolt-nrepl-mcp-v0.4.1-aarch64-macos.tar.gz
xattr -d com.apple.quarantine jolt-nrepl-mcp-v0.4.1-aarch64-macos/jolt-nrepl-mcp  # macOS only
cp jolt-nrepl-mcp-v0.4.1-aarch64-macos/jolt-nrepl-mcp ~/bin/
```

and the same for `jolt-lsp`. Check with `jolt-lsp --version`.

## Install

```text
/plugins install /path/to/jolt-kimi-plugin
```

Then `/reload` or `/new`. Kimi runs the plugin from its managed copy, so
reinstall after changing this directory.

## Use

Pass the project on the first call: `cwd` for the `nrepl_*` tools, `root`
(or an absolute `file`) for the `jolt_*` tools. Both are remembered for later
calls. If Kimi starts the servers in the plugin's directory, a call that
names no project is refused rather than acting on the plugin itself.

A project with a `deps.edn` and no running nREPL gets `jolt nrepl-server`
started on the first eval. `JOLT_NREPL_MCP_AUTOSTART=0` turns that off; set
it in the server's `env` block in `kimi.plugin.json`.

Skills: `jolt-nrepl` (loaded at session start: the REPL-first workflow),
`jolt-lsp` (the navigation and refactoring tools) and `clj-refactor`. The
copies are written from [jolt-skills](https://github.com/jlt-commons/jolt-skills)
by its `sync.sh`; edit them there.

## Hooks

The same checks as [jolt-claude-plugin](https://github.com/jlt-commons/jolt-claude-plugin)
gives Claude Code, run by its `jolt-claude.kimi` namespace (fetched through
the `:hooks` alias in `deps.edn`, so the first hook run needs network). Kimi
ignores what a PostToolUse hook prints, so the checks sit on the hooks that
can block:

- PreToolUse on `Write` and `Edit` of a `.clj`, `.cljc`, `.edn` or `.jolt`
  file: the call is refused when the text it would leave does not read as
  Clojure, with the line and column. Text jolt-lsp can repair (a dropped
  closer) goes through.
- PostToolUse: the file is repaired on disk (`jolt-lsp check --fix`) and
  noted for the session.
- Stop: the files changed since the last stop are linted with the project's
  `.clj-kondo/config.edn` and `.jolt-lsp/config.edn`; an error-level finding
  blocks the stop and goes back to the agent as a `file:line:col` list.
  Warnings are listed with errors but never block on their own.

Unlike Claude Code, lint errors arrive when the agent tries to finish rather
than after each edit. The hooks need `jolt` and `jolt-lsp` on `PATH`;
without jolt-lsp they do nothing.

## Test

```bash
jolt test
```

Starts each server in `kimi.plugin.json` (the binaries on `PATH`) from this
directory and drives it over stdio: tool lists, the refusal without a
project, an autostarted eval with syntax repair, and a jolt-lsp call that finds its project from a file path. It also runs each hook in the manifest as Kimi would: a refused edit, a repair on disk, and a Stop that reports a lint error.

EPL-2.0.
