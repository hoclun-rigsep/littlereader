(ns littlereader.server.core
(:require
    [clojure.string :as string]
    [clojure.edn :refer []]
    ; [nrepl.server :as nrepl-server]
    ; [cider.nrepl :refer (cider-nrepl-handler)]
    [clj-http.client :as client]
    [ring.middleware.resource]
    [ring.middleware.stacktrace]
    [ring.middleware.params :refer [wrap-params]]
    [ring.adapter.jetty :refer [run-jetty]]
    [ring.handler.dump :refer [handle-dump]]
    [ring.util.request]
    [ring.util.response :refer [file-response response header]]
    ; [jsonista.core :as json]
    ; [muuntaja.core :as m]
    ,))

(defn my-handler [sys req]
  nil)

(defn handler-with-middleware [sys]
  (-> (partial #'my-handler sys)
      ; (ring.middleware.resource/wrap-resource "")
      ; ring.middleware.params/wrap-params
      ; middleware/wrap-params
      ; (middleware/wrap-format)
      (ring.middleware.stacktrace/wrap-stacktrace)
      ; (middleware/wrap-format-request)
      ; (middleware/wrap-format-response)
      ; (middleware/wrap-format-negotiate)
      ,))

(defn run-server
  ([sys] (run-server sys "8080"))
  ([sys & args]
   (run-jetty
     (fn [req]
       ((#'handler-with-middleware sys) req))
     {:join? false
      :port (Integer. (first args))})))

;; need a way to make sure this gets shut down i guess
(def system nil)
(defn start-system! []
  (let [sys {}]
    (alter-var-root #'system (constantly sys))
    sys))

(defn -main [& args]
  ; (defonce nrepl (start-nrepl))
  (future
    (apply #'run-server (start-system!) args)))

(comment
(def server-8080 (future (#'run-server (start-system!) "8080")))
(future-cancel server-8080))
