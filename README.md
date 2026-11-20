# Jolt nREPL for Kimi Code

A Kimi Code plugin for Jolt/Clojure work. It registers two MCP servers that
you install separately:

- [jolt-nrepl-mcp](https://github.com/jlt-commons/jolt-nrepl-mcp): a
  persistent nREPL session. Eval with per-form output and syntax repair,
  load-file, lookup, completion, clojure.test runs, and starting/stopping
  `jolt nrepl-server` for the project.
- [jolt-lsp](https://github.com/jlt-commons/jolt-lsp): navigation over a
  project index, diagnostics, syntax check/repair, structural edits and
  refactors.

This repo holds only the manifest, the skills and a smoke test. Tool
behaviour is documented in the two projects.

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
/plugins install /path/to/kimi-nrepl-plugin
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
first and last are copied from jolt-nrepl-mcp, `jolt-lsp` from the jolt
skills; recopy them when upgrading the servers.

## Test

```bash
jolt test
```

Starts each server in `kimi.plugin.json` (the binaries on `PATH`) from this
directory and drives it over stdio: tool lists, the refusal without a
project, an autostarted eval with syntax repair, and a jolt-lsp call that finds its project from a file path.

EPL-2.0.
