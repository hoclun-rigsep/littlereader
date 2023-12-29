(ns littlereader.frontend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [helix.core :refer [defnc $]]
    [helix.hooks :as hooks]
    [helix.dom :as d]
    ["react-dom/client" :as rdom]
    [littlereader.anki :as anki]
    [littlereader.ui-frame :refer [dispatch-prop
                                   effect-dispatcher
                                   handle-effect
                                   connect-atom
                                   connect-chan]]))

(def an-atm (atom {:pending-input {:again #{} :hard #{} :good #{} :easy #{}}}))

(defmethod handle-effect
  :stage-card ([[[typ path] attempt]]
               (swap! an-atm update-in [:pending-input attempt] conj (first path))))
(defmethod handle-effect
  :unstage-card ([[[typ path] attempt]]
               (swap! an-atm update-in [:pending-input attempt] conj (first path))))
(defmethod handle-effect
  :an-intent ([[x & args]] (println x args)))
(defmethod handle-effect
  :but-click ([[[typ path] arg1]] (println typ path arg1)))
(defmethod handle-effect
  :inc-state ([[[_ path]]] (swap! an-atm update-in path inc)))

#_(defnc counter [{:keys [atm dispatch]}]
  (let [[state set-state] (connect-atom atm)]
  (d/div
    (d/button {:on-click #(dispatch [:inc-state])} "inc")
    )))

(defnc staging-area-word [{:keys [dispatch id key word]}]
  (let [[s s-s] (connect-chan (anki/card->word id))]
    (d/span {:on-click #(dispatch [:an-intent])} s)))

(defnc staging-area [{:keys [dispatch]}]
  (let [[s s-s] (connect-atom an-atm [:pending-input])]
    (d/div "DOG"
           (for [[k v] s]
             (d/div (str k)
                    (for [w v]
                      ($ staging-area-word
                         {:key w
                          :id w
                          :word w
                          :dispatch
                          (dispatch-prop dispatch :pending-input k w)})))))))

(defnc word [{:keys [dispatch id key word]}]
  (d/div word
           (d/button {:on-click #(dispatch [:stage-card :again])} "Again")
           (d/button {:on-click #(dispatch [:stage-card :hard])} "Hard")
           (d/button {:on-click #(dispatch [:stage-card :good])} "Good")
           (d/button {:on-click #(dispatch [:stage-card :easy])} "Easy")))

(defnc words [{:keys [dispatch]}]
  (let [[state set-state] (connect-chan nil
                            (go (<! (anki/cards->words' (<! (anki/due-now))))))]
    (d/div
      (for [[id wrd] state]
        ($ word {:id id :word wrd :key id :dispatch (dispatch-prop dispatch id)})))))

(defnc app []
  (let [[state set-state] (connect-atom an-atm)]
    (d/div
      {}
      "Hello, World!"
      (str state)
      ($ staging-area {:dispatch (dispatch-prop handle-effect)})
      (d/br)
      ($ words {:dispatch (dispatch-prop handle-effect)}))))

(defonce root (rdom/createRoot (js/document.getElementById "app")))
(.render root ($ app))
