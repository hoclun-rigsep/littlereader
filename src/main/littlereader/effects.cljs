(ns littlereader.effects
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan take! <! timeout] :as async]
    [medley.core :refer [deep-merge]]
    [cljs-http.client :as http]
    [littlereader.state :refer [an-atm]]
    [littlereader.anki :as anki]))

(defn backend [effect]
  (go
    (swap!
      an-atm
      deep-merge
      (do (<! (http/post
            "handle-effect"
            {:with-credentials? false}))
          {}))))

(defmulti handle-effect (comp first first))

(defmethod handle-effect
  :synchronize
  ([_]
   (anki/synchronize)
   (handle-effect [[:update-due-by-tomorrow]])
   (handle-effect [[:update-due-now]])))
(defmethod handle-effect
  :update-due-by-tomorrow
  ([_]
   (go
     (let [x (<! (anki/due-by-tomorrow))]
       (swap!
         an-atm
         (fn [atm]
           (assoc atm :words
                  (reduce
                    (fn [val item] (assoc-in val [item :due-by-tomorrow] true))
                    (-> atm :words)
                    x))))))))
(defmethod handle-effect
  :update-due-now
  ([_]
   (go
     (let [x (<! (anki/due-now))]
       (swap! an-atm
              (fn [atm]
                (assoc atm :words
                       (reduce
                         (fn [val item] (assoc-in val [item :due-now] true))
                         (-> atm :words)
                         x))))))))
(defmethod handle-effect
  :submit
  ([[_]]
   (go
     (as-> (@an-atm :words) X
       (group-by (fn [[_k v]] (some #{:again :hard :good :easy} (keys v))) X)
       (update-vals X (fn [v] (map first v)))
       (dissoc X nil)
       (<! (anki/raw-input-results X)))
     (handle-effect [[:update-due-now]])
     (handle-effect [[:clear-staging-area]]))))
(defmethod handle-effect
  :clear-staging-area
  ([_]
   (doseq [i (keys (:words @an-atm))]
     (handle-effect [[:unstage-card [i]]]))))
(defmethod handle-effect
  :stage-card
  ([[[_typ [id]] attempt]]
   (swap! an-atm update-in [:words id] conj [attempt true])
   #_(swap! an-atm update-in [:pending-input attempt] conj id)))
(defmethod handle-effect
  :unstage-card
  ([[[_typ path]]]  ;; maybe generalize with specter
   (swap! an-atm update-in [:words (last path)] dissoc :again :hard :good :easy)
   #_(swap! an-atm update-in (butlast path) #(set (remove (hash-set (last path)) %)))))
(defmethod handle-effect
  :an-intent ([[x & args]] (println x args)))
(defmethod handle-effect
  :but-click ([[[typ path] arg1]] (println typ path arg1)))
(defmethod handle-effect
  :inc-state ([[[_ path]]] (swap! an-atm update-in path inc)))
(defmethod handle-effect
  :add-word
  ([[[typ path] word]]
   (go
     (let [id (first (<! (anki/words->cards [word])))]
       (swap! an-atm update-in [:words id] (fnil #(conj % {}) {}))))))
(defmethod handle-effect
  :bring-in-random
  ([]
   (go
     (let [new-word (<! (anki/bring-in-random-new-card))]
       (swap! an-atm assoc :latest-random-new-word new-word)
       (handle-effect [[:add-word] new-word])))))
