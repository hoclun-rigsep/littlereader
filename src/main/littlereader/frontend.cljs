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
                                   connect-chan
                                   lockout-dispatch]]))

(goog-define img-host-url "https://brooklyn.ambergers.name/littlereader-images/")
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
      (d/h3 {:style {:display "inline-block"}} "Staging area")
      (d/button {:class "btn btn-primary btn-sm mx-2"
                 :on-click #(handle-effect [[:submit]])}
                "Submit")
      (d/button {:class "btn btn-secondary btn-sm"
                 :on-click #(handle-effect [[:clear-staging-area]])}
                "Clear")
      (d/div
        {:style {:display "flex" :margin-top "1rem" :margin-left "1rem" :gap "15px"}}
        (for [[k v] {:again again :hard hard :good good :easy easy}]
          (d/div {:key k}
                 (d/span {:style {}} (d/h6 (name k)))
                 (for [w v]
                   (d/div {:key w :style {:flex-direction "column"}}
                          ($ staging-area-word
                             {:id w
                              :word w})))))))))

(defnc word-you-can-stage [{:keys [dispatch id word style show-little-buttons]}]
  (let [[s _s-s] (ca [:words id])
        style (or style {:font-size "3rem"
                         :width "18rem"
                         :background-color "#aaa"
                         :margin "6px"
                         :border-radius "35% 35% 0% 0%"})
        attempt->color
        {:again "red" :hard "#EBE836" :good "green" :easy "blue"}
        little-button
        (fn [x]
          (d/span
            {:style
             {:cursor "pointer" :width "15%"
              :border-radius "35%"
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
      (when-not (false? show-little-buttons)
        (d/div
          {:style
           {:display "flex" :justify-content "space-around" :line-height "50%"}}
          (little-button :again)
          (little-button :hard)
          (little-button :good)
          (d/span
            {:style
             {:cursor "pointer" :width "15%"
              :border-radius "35%"
              :background-color "blue"}
             :on-click
             #(dispatch [(if (:easy s) :unstage-card :stage-card) :good])}
            (if (:easy s) " ⋅  " "    ")))))))

;; random fonts would be good
(defnc word-at-a-time [{:keys [dispatch word-ids-hook d d']}]
  (let [[word-ids _] word-ids-hook
        [state set-state] (helix.hooks/use-state #{})
        [current] (ca [:current])
        [id wrd] (get (vec state) current)
        [right-so-far] (ca [:words]
                         (comp
                           count
                           (partial into {}
                                    (filter
                                      (fn [[_ v]] ((some-fn :good :easy) v))))))
        [img? set-img?] (helix.hooks/use-state nil)
        [show-img? set-show-img?] (helix.hooks/use-state nil) ]
    (helix.hooks/use-effect
      :once
      ; this shuld be an effect
      (swap! an-atm assoc :current 0))
    #_(helix.hooks/use-effect
        []
        (set-background-color "#ccc"))
    (helix.hooks/use-effect
      [word-ids]
      (when (seq word-ids)
        (set-background-color "#ccc")
        (go (set-state (shuffle (<! (anki/cards->words' word-ids)))))))
    (helix.hooks/use-effect
      [current]
      (set-img? nil)
      (set-show-img? nil))
    (d/div
      {:style {:padding "3vw" :background-color "#ccc"}}
      (d/div {:style {:float "right" :margin "2rem"}}
             (d/button {:class ["btn" "btn-lg" "btn-primary"]
                        :on-click #(dispatch [[:change-active-view] :landing])}
                       \⨯)
             (d/div {:class ["fs-1" "btn-lg"]} right-so-far))
      ;; add delay before showing 
      ($ word-you-can-stage
         {:id id :word wrd
          :style {:background-color "#ccc" :width "94vw" :font-size "calc(20vw)"}
          :dispatch 
          (fn [& args]
            (let [[[_ _]] args]
              (if (not img?)
                (d [[:advance] (count state)])
                (do (set-show-img? true)
                    (js/setTimeout
                      #(.scrollIntoView
                         (aget (js/document.getElementsByTagName "img") 0))
                      200)))
              (apply (dispatch-prop d' id) args)))})
      (when wrd
        (d/img {:id "theimage"
                :style {:display (if show-img? "inherit" "none")}
                :on-click #(dispatch [[:advance] (count state)])
                :on-load (fn [e] (println "img retrieved for" wrd) (set-img? e))
                :src (str img-host-url wrd)}))
      (d/br)
      (d/button
        {:style {:width "20vw" :font-size "calc(8vw)"}
         :on-click #(dispatch [[:goback]])}
        \←)
      (d/button
        {:style {:width "20vw" :font-size "calc(8vw)"}
         :on-click #(dispatch [[:advance] (count state)])}
        \→))))

(defnc words [{:keys [dispatch word-ids-hook h]}]
  (let
    [[word-ids _] word-ids-hook
     [state set-state] (helix.hooks/use-state #{})]
    (helix.hooks/use-effect
      [word-ids]
      (when (seq word-ids)
        (go (set-state (<! (anki/cards->words' word-ids))))))
    (when (seq word-ids)
      (d/div
        h
        #_(d/span (count word-ids))
        (d/div
          {:style {:display "flex"
                   :flex-wrap "wrap"
                   :justify-content "space-evenly"}}
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

(defnc note-adder [{:keys [dispatch]}]
  (let [[s s-s] (helix.hooks/use-state "")]
    (<>
      (d/input {:value s
                :on-change #(s-s (.. % -target -value))})
      (d/button {:class ["btn" "btn-sm" "btn-secondary"]
                 :disabled (nil? (seq s))
                 :on-click #(dispatch [[:add-note] s])}
                "Add"))))

(defnc word-adder [{:keys [dispatch]}]
  (let [[s s-s] (hooks/use-state "")
        [x _] (connect-chan
                (go
                  (when-let [wrds (seq (<! (anki/findCards "is:new")))]
                    (<! (anki/cards->words wrds)))))
        [w _] (connect-chan
                (go
                  (when-let [wrds (seq (<! (anki/findCards "is:suspended")))]
                    (<! (anki/cards->words wrds)))))]
    (<>
      (d/select {:value s
                 :on-change #(s-s (.. % -target -value))}
                (for [i lemmas #_#_ :when ((set w) i)]
                  (d/option {:key i} i)))
      (d/button {:class ["btn" "btn-sm" "btn-secondary"]
                 :on-click #(dispatch [[:add-word] s])}
                "Add"))))

(defnc app []
  (let
    [[active-view]
     (ca [:active-view])
     words-not-due-hook
     (c-a an-atm [:words]
          (comp
            keys
            (partial into {} (remove (fn [[_ v]] (:due-by-tomorrow v))))))
     word-ids-hook
     ;; this one gets you "due right now"
     (c-a an-atm [:words]
          (comp
            keys
            (partial into {} (filter (fn [[_ v]] (:due-now v))))))
     ;; this one gets you "due by tomorrow""
     #_(c-a an-atm [:words]
          (comp
            keys
            (partial into {} (filter (fn [[_ v]] (:due-by-tomorrow v))))))]
    (helix.hooks/use-effect :once
                            (handle-effect [[:update-due-now]])
                            (handle-effect [[:update-due-by-tomorrow]]))
    (helix.hooks/use-effect [active-view]
                            (set-background-color "#fff"))
    
    (case active-view
        :slides
        (d/div
          {:style {:background-color "#ccc"}}
          ($ word-at-a-time
             {:dispatch (dispatch-prop handle-effect)
              :d (lockout-dispatch (dispatch-prop handle-effect))
              :d' (lockout-dispatch (dispatch-prop handle-effect))
              :word-ids-hook word-ids-hook}))
        :landing
        (d/div
          {}
          (d/div {:style {:float "right" :margin "2rem"}}
                 (d/button {:class  ["btn" "btn-lg" "btn-primary"]
                            :on-click #(handle-effect [[:change-active-view] :slides])} \▶))
          (d/br)
          ($ staging-area {:dispatch (dispatch-prop handle-effect)})
          (d/br)
          ($ words
             {:h (d/h3 "Some words not due")
              :dispatch (dispatch-prop handle-effect)
              :word-ids-hook words-not-due-hook})
          ($ words
               {:h (d/h3 "Due now!") #_(d/h3 "Due by tomorrow")
                :dispatch (dispatch-prop handle-effect)
                :word-ids-hook
                word-ids-hook})
          (d/div {:style {:margin "2rem"
                          :display "flex"
                          :flex-wrap "wrap"
                          :gap "10px"}}
                 (d/button {:on-click #(handle-effect [[:bring-in-random]])
                            :class ["btn" "btn-primary"]} "Bring in random")
                 (d/button {:on-click #(handle-effect [[:synchronize]])
                            :class ["btn" "btn-primary"]} "Synchronize")
                 ($ word-adder {:dispatch (dispatch-prop handle-effect)})
                 ($ note-adder {:dispatch (dispatch-prop handle-effect)})))
        (d/div (str active-view)))))

(defonce root (rdom/createRoot (js/document.getElementById "app")))
(.render root ($ app))
