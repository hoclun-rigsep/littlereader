(ns build
  (:refer-clojure :exclude [test])
  (:require [shadow.cljs.devtools.api :as shadow]
            [clojure.tools.build.api :as b]))

(def app-name "littlereader")
(def version "0.1.0-SNAPSHOT")
(def main 'littlereader.server.core)
(def class-dir "target/classes")
(def build-folder "target")
; folder where we collect files to pack in a jar
(def jar-content (str build-folder "/classes"))
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file-name  (format  "%s/%s-standalone.jar" build-folder app-name))

(defn test "Run all the tests." [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis     basis
                   :main      'clojure.main
                   :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- uber-opts [opts]
  (assoc opts
         :main main
         :uber-file (format "target/%s-%s.jar" "littlereader" version)
         :basis (b/create-basis {})
         :class-dir class-dir
         :src-dirs ["src"]
         :ns-compile [main]))

(defn ci "Run the CI pipeline of tests (and build the uberjar)." [opts]
  (test opts)
  (b/delete {:path "target"})
  (let [opts (uber-opts opts)]
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src"] :target-dir class-dir})
    (println (str "\nCompiling " main "..."))
    (b/compile-clj opts)
    (println "\nBuilding JAR...")
    (b/uber opts))
  opts)

(defn clean [_]
  (b/delete {:path build-folder})
  (println (format "Build folder \"%s\" removed" build-folder)))

(defn uber [_]
  (clean nil)
  (shadow/release :app)

  (b/copy-dir {:src-dirs   ["resources"]
               :target-dir jar-content})

  (b/compile-clj {:basis     basis
                  :src-dirs ["src"]
                  :class-dir jar-content})

  (b/uber {:class-dir jar-content
           :uber-file uber-file-name
           :basis     basis
           :main      main})

  (println (format "Uber file created: \"%s\"" uber-file-name)))
