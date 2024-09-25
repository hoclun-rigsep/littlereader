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
      (:body
        (<! (http/post
              "http://localhost:8000/intent"
              {:as :auto
               :edn-params
               effect}))))))

(defmulti handle-effect (comp first first))

(defmethod handle-effect
  :change-active-view
  ([[[_typ _path] x]]
   (swap! an-atm assoc :active-view x)))
(defmethod handle-effect
  :synchronize
  ([_]
   (anki/synchronize)
   (handle-effect [[:update-due-by-tomorrow]])
   (handle-effect [[:update-due-now]])))
(defmethod handle-effect
  :update-due-by-tomorrow
  ([_]
   (take!
     (anki/due-by-tomorrow)
     (fn [x]
       (swap!
         an-atm
         (fn [atm]
           (assoc
             atm :words
             (into
               (into {} (map #(hash-map % {:due-by-tomorrow true})) x)
               (map
                 (fn [[k v]]
                   [k (if ((set x) k)
                        (assoc v :due-by-tomorrow true)
                        (dissoc v :due-by-tomorrow))]))
               (:words atm)))))))))
(defmethod handle-effect
  :update-due-now
  ([_]
   (take!
     (anki/due-now)
     (fn [x]
       (swap!
         an-atm
         (fn [atm]
           (assoc
             atm :words
             (into
               (into {} (map #(hash-map % {:due-now true})) x)
               (map
                 (fn [[k v]]
                   [k (if ((set x) k)
                        (assoc v :due-now true)
                        (dissoc v :due-now))]))
               (:words atm)))))))))
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
     (handle-effect [[:update-due-by-tomorrow]])
     (handle-effect [[:clear-staging-area]]))))
(defmethod handle-effect
  :clear-staging-area
  ([_]
   (doseq [i (keys (:words @an-atm))]
     (handle-effect [[:unstage-card [i]]]))))
(defmethod handle-effect
  :stage-card
  ([[[_typ [id]] attempt]]
   (swap! an-atm update-in [:words id] dissoc :again :hard :good :easy)
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
#_(defmethod handle-effect
  :add-word
  ([[[_typ _path] word]]
   (go
     (let [id (first (<! (anki/words->cards [word])))]
       (handle-effect [[:unsuspend-word] id])
       (swap! an-atm update-in [:words id] (fnil #(conj % {}) {}))))))
(defmethod handle-effect
  :unsuspend-word
  ([[[_typ _path] id]]
   (anki/unsuspend [id])))
(defmethod handle-effect
  :bring-in-random
  ([]
   (go
     (let [new-word (<! (anki/bring-in-random-new-card))]
       (swap! an-atm assoc :latest-random-new-word new-word)
       (handle-effect [[:add-word] new-word])))))
(defmethod handle-effect
  :advance
  ([[[_typ _path] cnt]] (swap! an-atm update :current #(min (inc %) (dec cnt)))))
(defmethod handle-effect
  :goback
  ([[[_typ _path] cnt]] (swap! an-atm update :current #(max 0 (dec %)))))
(defmethod handle-effect
  :add-note
  ([[[_typ _path] wrd]]
   (go
     (let [id (<! (anki/add-note wrd))]
       (anki/bring-in-random-new-card id)
       (swap! an-atm update-in [:words id] (fnil #(conj % {}) {}))))))
