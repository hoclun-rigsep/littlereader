(ns littlereader.ui-frame
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan put! pipeline] :as async]
    [cljs-http.client :as http]
    [helix.hooks :as hooks]
    [littlereader.effects :refer [handle-effect]]))

(defn c-a
  ([atm]
   (c-a atm [] identity))
  ([atm keypath]
   (c-a atm keypath identity))
  ([atm keypath f]
   (let [watch-key (js/Object.)
         [state set-state] (hooks/use-state (f (get-in @atm keypath)))]
     ;; do I need to clean up the watch?
     (hooks/use-effect
       :once
       (add-watch atm watch-key
                  (fn [_k _r o n]
                    (let [o (f (get-in o keypath o))
                          n (f (get-in n keypath n))]
                      (when
                        (not= o n)
                        (set-state n)))))
       #(remove-watch atm watch-key))
     [state set-state])))

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
                  (fn [_k _r o n]
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

(defn dispatch-prop
  [dispatch & ids]
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

(defn lockout-dispatch
  "Don't dispatch f until t msec have passed since last time"
  ([f]
   (lockout-dispatch 1000 f))
  ([t f]
   (let [x (atom nil)
         timeout-id (atom (js/setTimeout #(reset! x nil) t))]
     (fn [& args]
       (when (nil? @x)
         (js/clearTimeout @timeout-id)
         (reset! x true)
         (reset! timeout-id (js/setTimeout #(reset! x nil) t))
         (apply f args))))))

(defn effect-dispatcher [& intents]
  (doseq [intent intents]
    (handle-effect intent)))

(defn send-intent [intent]
  '(call-backend))
