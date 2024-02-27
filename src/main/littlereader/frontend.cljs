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
    [littlereader.lemmas :refer [lemmas]]
    [littlereader.ui-frame :refer [dispatch-prop
                                   ; effect-dispatcher
                                   connect-atom
                                   c-a
                                   connect-chan]]))

(def ca (partial c-a an-atm))
(defn set-background-color [color]
  (set! (.-backgroundColor (.-style (.-body js/document))) color))

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
                              :word w}))))))
      (d/div {:style {:display "flex" :gap "10px"}}
             (d/button {:class "btn btn-primary"
                        :on-click #(handle-effect [[:submit]])}
                       "Submit")
             (d/button {:class "btn btn-secondary"
                        :on-click #(handle-effect [[:clear-staging-area]])}
                       "Clear")))))

(defnc word-you-can-stage [{:keys [dispatch id word style]}]
  (let [[s _s-s] (ca [:words id])
        style (or style {:font-size "3rem"
                         :width "18rem"
                         :background-color "#aaa"
                         :margin "6px"
                         :border-radius "35% 35% 0% 0%"})
        attempt->color
        {:again "red" :hard "yellow" :good "green" :easy "blue"}
        little-button
        (fn [x]
          (d/span
            {:style
             {:cursor "pointer" :width "15%"
              :background-color (x attempt->color)}
             :on-click
             #(dispatch [(if (x s) :unstage-card :stage-card) x])}
            (if (x s) " ⋅  " "    ")))]
    (d/div
      {:style style}
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

;; random fonts would be good
(defnc word-at-a-time [{:keys [dispatch word-ids-hook]}]
  (let [[word-ids _] word-ids-hook
        [state set-state] (helix.hooks/use-state #{})
        [current set-current] (helix.hooks/use-state 0) 
        [id wrd] (get (vec state) current)]
    (helix.hooks/use-effect
      [word-ids]
      (when (seq word-ids)
        (go (set-state (<! (anki/cards->words' word-ids))))))
    (d/div
      ($ word-you-can-stage
         {:id id :word wrd
          :style {:width "100%"
                  :margin "1rem"
                  :padding "1rem"
                  :font-size "20rem"
                  :background-color "#aaa"
                  :border-radius "35% 35% 0% 0%"}
          :dispatch (fn [& args]
                      (set-current #(min (inc current) (dec (count state))))
                      (apply (dispatch-prop dispatch id) args))})
      (d/button
        {:on-click #(set-current (max 0 (dec current)))}
        \←)
      (d/button
        {:on-click #(set-current (min (inc current) (dec (count state))))}
        \→))))

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
  (let [[s s-s] (hooks/use-state "")
        [w _] (connect-chan
                (go
                  (let [wrds (<! (anki/findCards "is:suspended"))]
                    (<! (anki/cards->words wrds)))))]
    (<>
      (d/select {:value s
                 :on-change #(s-s (.. % -target -value))}
                (for [i lemmas :when ((set w) i)] (d/option {:key i} i)))
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
           
           (d/br)
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
