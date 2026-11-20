(ns kimi-plugin.smoke-test
  "Start each MCP server in kimi.plugin.json (the installed binaries on
  PATH) from the plugin directory, the worst case for finding the project,
  and drive it over stdio."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t :refer [deftest is testing]]))

(def plugin-dir (str (fs/canonicalize ".")))

(def manifest (json/read-str (slurp (str plugin-dir "/kimi.plugin.json")) :key-fn keyword))

(defn- start-server [server-name]
  (let [{:keys [command args]} (get-in manifest [:mcpServers server-name])
        proc (p/process (into [command] args) {:dir plugin-dir :err :inherit})]
    {:proc proc
     :in (io/writer (:in proc))
     :out (io/reader (:out proc))
     :id (atom 0)}))

(defn- stop-server [{:keys [proc in]}]
  (.close in)
  (when (= ::timeout (deref proc 10000 ::timeout)) (p/destroy-tree proc)))

(defn- rpc
  "Send a request and read lines until its response."
  [{:keys [in out id]} method params]
  (let [n (swap! id inc)]
    (.write in (str (json/write-str {:jsonrpc "2.0" :id n :method method :params params}) "\n"))
    (.flush in)
    (loop []
      (let [line (.readLine out)]
        (when-not line (throw (ex-info "server closed stdout" {:method method})))
        (let [msg (json/read-str line :key-fn keyword)]
          (if (= n (:id msg)) msg (recur)))))))

(defn- init! [server]
  (rpc server "initialize" {:protocolVersion "2024-11-05" :capabilities {}
                            :clientInfo {:name "smoke" :version "0"}}))

(defn- call
  "A tool call's text and error flag."
  [server tool args]
  (let [{:keys [content isError]} (:result (rpc server "tools/call" {:name tool :arguments args}))]
    {:text (:text (first content)) :error? (boolean isError)}))

(defn- tool-names [server]
  (set (map :name (get-in (rpc server "tools/list" {}) [:result :tools]))))

(defn- temp-project []
  (let [d (str (fs/canonicalize (fs/create-temp-dir {:prefix "kimi-smoke"})))]
    ;; a Jolt project, which jolt-nrepl-mcp autostarts a server for
    (spit (str d "/deps.edn") "{:jolt/min-version \"0.8.25\" :paths [\"src\"]}")
    (fs/create-dirs (str d "/src/app"))
    (spit (str d "/src/app/core.clj") "(ns app.core)\n(defn greet [n] (str \"hi \" n))\n")
    d))

(deftest manifest-names-both-servers
  (is (= #{:jolt-nrepl :jolt-lsp} (set (keys (:mcpServers manifest)))))
  (doseq [skill ["jolt-nrepl" "jolt-lsp" "clj-refactor"]]
    (is (fs/exists? (str plugin-dir "/skills/" skill "/SKILL.md")) skill)))

(deftest nrepl-server
  (let [srv (start-server :jolt-nrepl)
        proj (temp-project)]
    (try
      (is (= "jolt-nrepl" (get-in (init! srv) [:result :serverInfo :name])))
      (is (every? (tool-names srv) ["nrepl_eval" "nrepl_load_file" "nrepl_test" "nrepl_lookup"]))
      (testing "without cwd it asks for one rather than using the plugin directory"
        (let [{:keys [text error?]} (call srv "nrepl_eval" {:code "1"})]
          (is error?)
          (is (re-find #"pass cwd" text) text))
        (is (not (fs/exists? (str plugin-dir "/.nrepl-port")))))
      (testing "with cwd it starts a server and closes a dropped paren"
        (let [{:keys [text error?]} (call srv "nrepl_eval" {:cwd proj :code "(require 'app.core) (app.core/greet \"kimi\""})]
          (is (not error?) text)
          (is (re-find #"started nREPL" text) text)
          (is (re-find #"repaired:" text) text)
          (is (re-find #"=> \"hi kimi\"" text) text)))
      (testing "the project is remembered"
        (is (re-find #"=> 3" (:text (call srv "nrepl_eval" {:code "(+ 1 2)"})))))
      (finally
        (call srv "nrepl_stop" {:cwd proj})
        (stop-server srv)
        (fs/delete-tree proj {:force true})))))

(deftest lsp-server
  (let [srv (start-server :jolt-lsp)
        proj (temp-project)]
    (try
      (is (= "jolt-lsp" (get-in (init! srv) [:result :serverInfo :name])))
      (is (every? (tool-names srv) ["jolt_check" "jolt_replace_form" "jolt_references"]))
      (testing "without root it asks for one rather than indexing the plugin directory"
        (let [{:keys [text error?]} (call srv "jolt_index" {})]
          (is error?)
          (is (re-find #"pass root" text) text)))
      (testing "an absolute file finds its project"
        (let [{:keys [text error?]} (call srv "jolt_symbols" {:file (str proj "/src/app/core.clj")})]
          (is (not error?) text)
          (is (re-find #"greet" text) text)))
      (testing "and later calls stay in it"
        (let [{:keys [text]} (call srv "jolt_references" {:symbol "app.core/greet"})]
          (is (not (re-find #"pass root" text)) text)))
      (finally
        (stop-server srv)
        (fs/delete-tree proj {:force true})))))

;; --- hooks ------------------------------------------------------------------

(defn- hook-for [event]
  (first (filter #(= event (:event %)) (:hooks manifest))))

(defn- run-hook
  "Run the manifest's hook for `event` as Kimi does: through a shell, in the
  plugin dir, KIMI_PLUGIN_ROOT set, the input JSON on stdin. Its parsed
  stdout, or nil."
  [event state-dir input]
  (let [{:keys [command]} (hook-for event)
        {:keys [out]} (p/shell {:dir plugin-dir :in (json/write-str (assoc input :hook_event_name event))
                                :out :string :err :inherit :continue true
                                :extra-env {"KIMI_PLUGIN_ROOT" plugin-dir
                                            "KIMI_HOOKS_STATE_DIR" state-dir}}
                               "sh" "-c" command)]
    (when-not (str/blank? out) (json/read-str out :key-fn keyword))))

(deftest manifest-declares-the-hooks
  (doseq [event ["PreToolUse" "PostToolUse" "Stop"]]
    (is (hook-for event) event))
  (is (re-matches (re-pattern (:matcher (hook-for "PreToolUse"))) "Edit"))
  (is (re-matches (re-pattern (:matcher (hook-for "PostToolUse"))) "Write"))
  (is (not (re-matches (re-pattern (:matcher (hook-for "PreToolUse"))) "Read"))))

(deftest hooks-repair-refuse-and-lint
  (let [proj (temp-project)
        state (str (fs/canonicalize (fs/create-temp-dir {:prefix "kimi-hooks"})))
        f (str proj "/src/app/core.clj")
        sid "smoke-session"]
    (try
      (testing "PreToolUse refuses an edit that leaves the file unreadable"
        (let [out (run-hook "PreToolUse" state {:tool_name "Edit" :session_id sid
                                                :tool_input {:path f :old_string "(str \"hi \" n)"
                                                             :new_string "(str \"hi \" n]"}})]
          (is (= "deny" (get-in out [:hookSpecificOutput :permissionDecision])) (pr-str out))))
      (testing "PostToolUse repairs a dropped closer on disk"
        (spit f "(ns app.core)\n(defn greet [n] (nope n)\n")
        (run-hook "PostToolUse" state {:tool_name "Write" :session_id sid :tool_input {:path f}})
        (let [text (slurp f)]
          (is (= (count (re-seq #"\(" text)) (count (re-seq #"\)" text))) text)))
      (testing "Stop blocks on the error in the touched file"
        (let [out (run-hook "Stop" state {:session_id sid})]
          (is (str/includes? (str (get-in out [:hookSpecificOutput :permissionDecisionReason])) "nope")
              (pr-str out))))
      (finally
        (fs/delete-tree proj {:force true})
        (fs/delete-tree state {:force true})))))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'kimi-plugin.smoke-test)]
    (shutdown-agents)
    (System/exit (if (zero? (+ fail error)) 0 1))))
