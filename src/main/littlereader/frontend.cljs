(ns littlereader.frontend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [helix.core :refer [defnc $ <>]]
    [helix.hooks :as hooks]
    [helix.dom :as d]
    ["react-dom/client" :as rdom]
    [littlereader.anki :as anki]
    [littlereader.ui-frame :refer [dispatch-prop
                                   effect-dispatcher
                                   handle-effect
                                   connect-atom
                                   connect-chan]]))

(defonce an-atm
  (atom {:pending-input {:again #{}
                         :hard  #{}
                         :good  #{}
                         :easy  #{}}}))

(defmethod handle-effect
  :submit ([[x]]
           (anki/raw-input-results (:pending-input @an-atm))
           (handle-effect [[:clear-staging-area]])))
(defmethod handle-effect
  :clear-staging-area ([_] (swap! an-atm assoc-in [:pending-input] {:again #{}
                                                                    :hard  #{}
                                                                    :good  #{}
                                                                    :easy  #{}})))
(defmethod handle-effect
  :stage-card ([[[typ path] attempt]]
               (swap! an-atm update-in [:pending-input attempt] conj (first path))))
(defmethod handle-effect
  :unstage-card ([[[typ path] attempt]]  ;; maybe generalize with specter
               (swap! an-atm update-in (butlast path) #(set (remove (hash-set (last path)) %)))))
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

(defnc staging-area-word [{:keys [dispatch id word]}]
  (let [[s s-s] (connect-chan (anki/card->word id))]
    (d/span {:style {:margin "4px"} :on-click #(dispatch [:unstage-card])} s)))

(defnc staging-area [{:keys [dispatch]}]
  (let [[s s-s] (connect-atom an-atm [:pending-input])]
    (<> 
      (d/h3 "Staging area")
      (d/div
        {:style {:display "flex" :margin "30px" :gap "15px"}}
        (for [[k v] s]
          (d/div {:key k}
                 (d/span {:style {}} (d/h5 (name k)))
                 (for [w v]
                   (d/div {:key w :style {:flex-direction "column"}}
                          ($ staging-area-word
                             {:id w
                              :word w
                              :dispatch
                              (dispatch-prop dispatch :pending-input k w)}))))))
      (d/button
        {:class "btn btn-primary"
         :on-click #(handle-effect [[:submit]])}
        "Submit")
      (d/button {:class "btn btn-secondary"
                 :on-click #(handle-effect [[:clear-staging-area]])}
                "Clear"))))

(defnc word [{:keys [dispatch id word]}]
  (let [attempt->color {:again "red" :hard "yellow" :good "green" :easy "blue"}

        little-button
        (fn [x]
          (d/span
            {:style
             {:cursor "pointer" :width "3rem" :background-color (x attempt->color)}
             :on-click
             #(dispatch [:stage-card x])}
            "    "))]
    (d/div
      {:style
       {:font-size "3rem"
        :width "18rem"
        :background-color "#aaa"
        :margin "6px"
        :border-radius "35% 35% 0% 0%"}}
      (d/div
        {:style
         {:width "%100" :text-align "center"}}
        word)
      (d/div
        {:style
         {:display "flex" :justify-content "space-between" :line-height "50%"}}
        (little-button :again)
        (little-button :hard)
        (little-button :good)
        (little-button :easy)))))

(defnc words-due-now [{:keys [dispatch c]}]
  (let [[state set-state] (connect-chan nil
                            (go (<! (anki/cards->words' (<! (anki/due-now))))))]
    (d/div {:style {:display "flex" :flex-wrap "wrap"}}
      (for [[id wrd] state]
        ($ word {:id id :word wrd :key id :dispatch (dispatch-prop dispatch id)})))))

(defnc app []
  (let [[state set-state] (connect-atom an-atm)]
    (d/div
      {}
      "Hello, World!"
      (d/br)
      (str state)
      ($ staging-area {:dispatch (dispatch-prop handle-effect)})
      (d/br)
      ($ words-due-now {:dispatch (dispatch-prop handle-effect)}))))

(defonce root (rdom/createRoot (js/document.getElementById "app")))
(.render root ($ app))
