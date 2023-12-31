(ns littlereader.littlereader
  (:require [clj-http.client :as client]
            ; [membrane.java2d :as java2d]
            ; [membrane.skia :as skia]
            ; [membrane.ui :as ui
            ;  :refer [vertical-layout
            ;          translate
            ;          horizontal-layout
            ;          button
            ;          label
            ;          with-color
            ;          bounds
            ;          spacer
            ;          on]]
            ; [membrane.component :as component :refer [defui defeffect]]
            ; [membrane.basic-components :as basic]
            )
  (:gen-class))

#_(defn act [action params]
  (merge 
    {:action (name action)
     :version 6
     :params params}))

(defn act [action params]
  ((some-fn  :result :error)
   (:body
     (client/post
       "http://localhost:8765"
       {:content-type :json
        :as :auto
        :form-params
        (merge 
          {:action (name action)
           :version 6}
          (when params
            {:params params}))}))))

(defn synchronize [] (act :sync nil))

(def a-card 1700182610095)

(defn findCards [query]
 (act :findCards {"query" query}))

(defn due-now []
  (findCards "is:due"))

(defn cardsToNotes [& cards]
  (act :cardsToNotes
       {"cards" cards}))

(defn cards->words [cards]
  (into #{}
        (map #(get-in % [:fields :Front :value]))
        (act :cardsInfo {:cards cards})))

(defn words->cards [words]
  (findCards
    (apply str (interpose " or " (map #(str \w \: \" % \") words)))))

(defn answerCards [ease cards]
  (let [ease (get {:again 1 :hard 2 :good 3 :easy 4 1 1 2 2 3 3 4 4} ease)]
    (act :answerCards
         {:answers
          (map
            (fn [card]
              {"cardId" card
               "ease" ease})
            cards)})))

(defn input-results [data] 
  (doseq [[k v] data]
    (answerCards
      (get {:again 1 :hard 2 :good 3 :easy 4 1 1 2 2 3 3 4 4} k)
      (words->cards v))))

(defn setEaseFactors [ease-permille cards]
  (act :setEaseFactors 
       {:cards cards
        :easeFactors (repeat (count cards) ease-permille)}))

(defn bring-in-random-new-card
  ([] (bring-in-random-new-card nil))
  ([word]
   (let [random-new-card-id
         (if word
           (->> (words->cards [word])
                first)
           (->> (findCards "is:new")
                rand-nth))]
     (answerCards :good [random-new-card-id])
     (answerCards :good [random-new-card-id])
     (setEaseFactors 1800 [random-new-card-id])
     (first (cards->words [random-new-card-id])))))

(defn greet
  "Callable entry point to the application."
  [data]
  (println (str "Hello, " (or (:name data) "World") "!")))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (greet {:name (first args)}))
