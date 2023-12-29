(ns littlereader.ui-frame
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan take! <! put! pipeline] :as async]
    [helix.core :refer [defnc $]]
    [helix.hooks :as hooks]
    [helix.dom :as d]
    ["react-dom/client" :as rdom]))

(defn connect-atom
  ([atm]
   (connect-atom atm []))
  ([atm keypath]
   (let [watch-key (js/Object.)
         [state set-state] (hooks/use-state (get-in @atm keypath))]
     ;; do I need to clean up the watch?
     (hooks/use-effect
       :once
       (add-watch atm watch-key
                  (fn [k r o n]
                    (let [o (get-in o keypath o)
                          n (get-in n keypath n)]
                      (when
                        (not= o n)
                        (set-state n)))))
       #(remove-watch atm watch-key))
     [state set-state])))

(defn connect-chan
  ([c] (connect-chan nil c (map identity)))
  ([init c] (connect-chan init c (map identity)))
  ([init c xform]
   (let [[state set-state] (hooks/use-state init)
         c' (chan)]
     (pipeline 1 c' xform c)
     (hooks/use-effect
       :once
       (let [cleanup? (chan)]
         (go (loop []
               (let [[v ch] (alts! [c' cleanup?])]
                 (cond
                   (= ch cleanup?) nil
                   (nil? v) nil
                   :else (do (set-state v)
                             (recur))))))
         #(do (put! cleanup? true)))) ;; couldn't this just close the 
     [state set-state])))

(defn dispatch-prop [dispatch & ids]
  (fn [x]
    (dispatch
      (if-not (seq ids) x
        (let [x (if (vector? (first x))
                  x
                  (apply vector [(first x) nil] (rest x)))]
          (update-in
            x
            [0 1]
            concat
            ids))))))

(defmulti handle-effect (comp first first))

(defn effect-dispatcher [& intents]
  (doseq [intent intents]
    (handle-effect intent)))

(comment
(def a-chan (chan))
(def b-chan (chan))
(def b=loop
  (go-loop []
           (println "B" (<! b-chan))
           (if (= (<! b-chan) 0)
             0
             (recur))))
(def a=loop
  (go-loop []
    (println "A" (<! b-chan))
    (if (= (<! b-chan) 0)
      0
      (recur))))

(comment (put! a-chan 121))


(defnc counter-component [{:keys [num]}]
  (let [[state set-state] (connect num a-chan)]
      (d/div state)))

(defnc somecomp []
  (let [[state set-state] (hooks/use-state 0)]
    (hooks/use-effect
      []
      (let [cleanup? (chan)]
        (go-loop []
                 (let [[v ch] (alts! [a-chan cleanup?])]
                   (cond
                     (= ch cleanup?) (println "reatrd")
                     (nil? v) (println "reatrd")
                     :else (do (set-state v)
                               (recur)))))
        #(do (put! cleanup? true))))
    (d/div "Hello, Earth!" state)))

)
