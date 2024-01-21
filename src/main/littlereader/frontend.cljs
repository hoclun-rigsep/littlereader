(ns littlereader.frontend
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
    [cljs.core.async :refer [chan take! <! timeout] :as async]
    [helix.core :refer [defnc $ <>]]
    [helix.hooks :as hooks]
    [helix.dom :as d]
    ["react-dom/client" :as rdom]
    [littlereader.effects :refer [handle-effect]]
    [littlereader.state :refer [an-atm]]
    [littlereader.anki :as anki]
    [littlereader.ui-frame :refer [dispatch-prop
                                   ; effect-dispatcher
                                   connect-atom
                                   c-a
                                   connect-chan]]))

(def ca (partial c-a an-atm))

(defnc staging-area-word [{:keys [dispatch id]}]
  (let [[s] (connect-chan (anki/card->word id))]
    (d/span {:style {:margin "4px"} :on-click #(dispatch [:unstage-card])} s)))

(defnc staging-area [{:keys [dispatch]}]
  (let
    [[[again] [hard] [good] [easy]]
     (for [i [:again :hard :good :easy]]
       (ca [:words]
           (comp
             keys
             (partial into {} (filter (fn [[_ v]] (i v)))))))]
    (<>
      (d/h3 "Staging area")
      (d/div
        {:style {:display "flex" :margin "30px" :gap "15px"}}
        (for [[k v] {:again again :hard hard :good good :easy easy}]
          (d/div {:key k}
                 (d/span {:style {}} (d/h5 (name k)))
                 (for [w v]
                   (d/div {:key w :style {:flex-direction "column"}}
                          ($ staging-area-word
                             {:id w
                              :word w
                              :dispatch
                              (dispatch-prop dispatch :pending-input k w)}))))))
      (d/div {:style {:display "flex" :gap "10px"}}
             (d/button {:class "btn btn-primary"
                        :on-click #(handle-effect [[:submit]])}
                       "Submit")
             (d/button {:class "btn btn-secondary"
                        :on-click #(handle-effect [[:clear-staging-area]])}
                       "Clear")))))

(defnc word-you-can-stage [{:keys [dispatch id word]}]
  (let [[s s-s] (ca [:words id])
        attempt->color
        {:again "red" :hard "yellow" :good "green" :easy "blue"}

        little-button
        (fn [x]
          (d/span
            {:style
             {:cursor "pointer" :width "3rem"
              :background-color (x attempt->color)}
             :on-click
             #(dispatch [(if (x s) :unstage-card :stage-card) x])}
            (if (x s) " ⋅  " "    ")))]
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

(defnc words [{:keys [dispatch word-ids-hook h]}]
  (let
    [[word-ids _] word-ids-hook
     [state set-state] (helix.hooks/use-state #{})]
    (helix.hooks/use-effect
      [word-ids]
      (when (seq word-ids)
        (go (set-state (<! (anki/cards->words' word-ids))))))
    (d/div
      h
      (d/span (count word-ids))
      (when (seq word-ids)
        (d/div {:style {:display "flex" :flex-wrap "wrap"}}
               (for [[id wrd] (sort-by second state)]
                 ($ word-you-can-stage
                    {:id id :word wrd :key id
                     :dispatch (dispatch-prop dispatch id)})))))))

(defnc state-view []
  (let [[state] (connect-atom an-atm)
        [show-state set-show-state] (helix.hooks/use-state false)]
    (<>
      (d/button
        {:class ["btn" "btn-sm" "btn-secondary"]
         :on-click #(set-show-state not)}
        "Show")
      (d/span {:style {:display (if show-state "block" "none")}} (str state)))))

(defnc word-adder [{:keys [dispatch]}]
  (let [[s s-s] (hooks/use-state "")]
    (<>
      (d/input {:value s
                :on-change #(s-s (.. % -target -value))})
      (d/button {:class ["btn" "btn-sm" "btn-secondary"]
                 :on-click #(dispatch [[:add-word] s])}
                "Add"))))

(defnc app []
  (helix.hooks/use-effect :once
                          (handle-effect [[:update-due-now]])
                          (handle-effect [[:update-due-by-tomorrow]]))
  (d/div
    {}
    (d/div {:style {:display "flex" :gap "10px"}}
           (d/button {:on-click #(handle-effect [[:bring-in-random]])
                      :class ["btn" "btn-primary"]} "Bring in random")
           (d/button {:on-click #(handle-effect [[:synchronize]])
                      :class ["btn" "btn-primary"]} "Synchronize")
           ($ word-adder {:dispatch (dispatch-prop handle-effect)})
           ($ state-view))
    (d/br)
    ($ staging-area {:dispatch (dispatch-prop handle-effect)})
    (d/br)
    ($ words
       {:h (d/h3 "Some words not due")
        :dispatch (dispatch-prop handle-effect)
        :word-ids-hook
        (c-a an-atm [:words]
             (comp
               keys
               (partial into {} (remove (fn [[_ v]] (:due-by-tomorrow v))))))})
    #_($ words
         {:h (d/h3 "Due now!")
          :dispatch (dispatch-prop handle-effect)
          :word-ids-hook
          (c-a an-atm [:words]
               (comp
                 keys
                 (partial into {} (filter (fn [[_ v]] (:due-now v))))))})
    ($ words
       {:h (d/h3 "Due by tomorrow")
        :word-ids-hook
        (c-a an-atm [:words]
             (comp
               keys
               (partial into {} (filter (fn [[_ v]] (:due-by-tomorrow v))))))
        :dispatch (dispatch-prop handle-effect)})))

(defonce root (rdom/createRoot (js/document.getElementById "app")))
(.render root ($ app))
