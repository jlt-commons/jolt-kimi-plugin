# Jolt nREPL for Kimi Code

A Kimi Code plugin for Jolt/Clojure work. It bundles two MCP servers, run
from their tagged git releases:

- [jolt-nrepl-mcp](https://github.com/jlt-commons/jolt-nrepl-mcp): a
  persistent nREPL session. Eval with per-form output and syntax repair,
  load-file, lookup, completion, clojure.test runs, and starting/stopping
  `jolt nrepl-server` for the project.
- [jolt-lsp](https://github.com/jlt-commons/jolt-lsp): navigation over a
  project index, diagnostics, syntax check/repair, structural edits and
  refactors.

This repo holds only the manifest, the pinned `deps.edn`, the skills and a
smoke test. Tool behaviour is documented in the two projects.

## Requirements

- Jolt 0.8.25 or newer on `PATH`
- Kimi Code with plugin and MCP support

Prefetch the pinned dependencies from this directory:

```bash
jolt -P
```

## Install

```text
/plugins install /path/to/kimi-nrepl-plugin
```

Then `/reload` or `/new`. Kimi runs the plugin from its managed copy, so
reinstall after changing this directory.

## Use

Kimi starts both servers in the plugin's directory, so pass the project on
the first call: `cwd` for the `nrepl_*` tools, `root` (or an absolute `file`)
for the `jolt_*` tools. Both are remembered for later calls. A call that
names no project is refused instead of acting on the plugin's own directory.

A project with a `deps.edn` and no running nREPL gets `jolt nrepl-server`
started on the first eval. `JOLT_NREPL_MCP_AUTOSTART=0` turns that off; set
it in the server's `env` block in `kimi.plugin.json`.

Skills: `jolt-nrepl` (loaded at session start: the REPL-first workflow),
`jolt-lsp` (the navigation and refactoring tools) and `clj-refactor`. The
first and last are copied from jolt-nrepl-mcp, `jolt-lsp` from the jolt
skills; recopy them when bumping the pins.

## Updating

Bump the `:git/tag` and `:git/sha` of both libraries in `deps.edn`, run
`jolt -P`, then `jolt test`. jolt-nrepl-mcp depends on an older jolt-lsp; the
top-level jolt-lsp pin is the one on the classpath.

## Test

```bash
jolt test
```

Starts each server from `kimi.plugin.json` as Kimi would and drives it over
stdio: tool lists, the refusal without a project, an autostarted eval with
syntax repair, and a jolt-lsp call that finds its project from a file path.

EPL-2.0.
