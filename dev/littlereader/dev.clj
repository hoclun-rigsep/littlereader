(ns littlereader.dev
  (:require [shadow.cljs.devtools.server]
            [shadow.cljs.devtools.api]))

(defn shadow-watch []
  (#'shadow.cljs.devtools.server/start!)
  (let [url "http://localhost:8765"]
    (#'shadow.cljs.devtools.api/watch
      :app
      {:config-merge
       [{:closure-defines
         {'littlereader.anki/url url}}]})))

(comment
  (shadow.cljs.devtools.api/watch :app))

(defn -main [& _]
  (shadow-watch))
