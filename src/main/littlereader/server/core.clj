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
    [jumblerg.middleware.cors :refer [wrap-cors]]
    [muuntaja.core :as m]
    [muuntaja.middleware :as middleware]
    ,)
  
  (:gen-class))

(defmulti handle-effect (comp first first))
(defmethod handle-effect :test ([& _] {:reatrd 6}))

(defmulti ring-handler #(string/split (apply str (rest (:uri %2))) #"/"))

(defmethod ring-handler ["anki"]
  ([_sys req]
   (let [url "http://localhost:8765"]
     (client/post
       url
       {:content-type :json
        :as :stream
        :form-params
        (:body-params req)}))))

(defmethod ring-handler
  ["intent"]
  ([_sys req] (response (handle-effect (:body-params req)))))

(defn handler-with-middleware [sys]
  (-> (partial #'ring-handler sys)
      (middleware/wrap-format)
      (ring.middleware.resource/wrap-resource "public")
      (ring.middleware.stacktrace/wrap-stacktrace)
      (wrap-cors identity)
      ; (ring.middleware.resource/wrap-resource "")
      ; ring.middleware.params/wrap-params
      ; middleware/wrap-params
      ; (middleware/wrap-format-request)
      ; (middleware/wrap-format-response)
      ; (middleware/wrap-format-negotiate)
      ,))

(defmethod ring-handler
  [""]
  ([sys req] ((handler-with-middleware sys) (assoc req :uri "/index.html"))))

(defn run-server
  ([sys] (run-server sys "8080"))
  ([sys & args]
   (let [port (Integer. (first args))]
   (println "Starting server on port " port)
   (run-jetty
     (fn [req]
       ((#'handler-with-middleware sys) req))
     {:join? true
      :port port}))))

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
(def server-8000 (future (#'run-server (start-system!) "8000")))
(future-cancel server-8000))
