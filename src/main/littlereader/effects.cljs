(ns littlereader.effects
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan take! <! timeout] :as async]
    [littlereader.state :refer [an-atm]]
    [littlereader.anki :as anki]))

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
     (swap! an-atm assoc :due-by-tomorrow
            (zipmap (<! (anki/due-by-tomorrow)) (repeat true))))))
(defmethod handle-effect
  :update-due-now
  ([_]
   (go
     (swap! an-atm assoc :due-now
            (zipmap (<! (anki/due-now)) (repeat {:due-now true}))))))
(defmethod handle-effect
  :submit ([[_]]
           (anki/raw-input-results (:pending-input @an-atm))
           (handle-effect [[:update-due-now]])
           (handle-effect [[:clear-staging-area]])))
(defmethod handle-effect
  :clear-staging-area
  ([_] (swap! an-atm assoc-in [:pending-input]
              {:again #{} :hard #{} :good #{} :easy #{}})))
(defmethod handle-effect
  :stage-card
  ([[[_typ path] attempt]]
   (swap! an-atm update-in [:pending-input attempt] conj (first path))))
(defmethod handle-effect
  :unstage-card
  ([[[_typ path]]]  ;; maybe generalize with specter
   (swap! an-atm update-in (butlast path) #(set (remove (hash-set (last path)) %)))))
(defmethod handle-effect
  :an-intent ([[x & args]] (println x args)))
(defmethod handle-effect
  :but-click ([[[typ path] arg1]] (println typ path arg1)))
(defmethod handle-effect
  :inc-state ([[[_ path]]] (swap! an-atm update-in path inc)))

