(ns littlereader.anki
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go]]))
  (:require #?(:cljs [cljs-http.client :as http]
               :clj  [clj-http.client :as http])
            #?(:cljs [cljs.core.async :refer [chan <!] :as async]
               :clj  [clojure.core.async :refer [chan go <!] :as async])))


#?(:cljs
   (goog-define url "http://localhost:8000/anki"))

(def attempt {:again 1 :hard 2 :good 3 :easy 4 1 1 2 2 3 3 4 4})

(comment
  ; clj
  (:body (http/get "http://localhost:8000" {:accept :edn :as :auto}))

  ;cljs
  (go
    (println 
      (:body (<! (http/post
            "http://localhost:8000/intent"
            {:as :auto
             :edn-params
             [[:test]]}))))))

#?(:clj
   (defn act
     ([action] (act action nil))
     ([action params]
      (async/thread
        ((some-fn :result :error)
         (:body
           (http/post
             "http://localhost:8765"
             {:content-type :json
              :as :auto
              :form-params
              (merge
                {:action (name action)
                 :version 6}
                (when params
                  {:params params}))}))))))
   :cljs
   (defn act
     ([action] (act action nil))
     ([action params]
      (http/post
        url ; "anki" "http://localhost:8765"
        {:channel (chan 1 (comp (map :body) (map (some-fn :result :error))))
         :with-credentials? false
         :json-params (merge {:action (name action)
                              :version 6}
                             (when params {:params params}))}))))

(defn running? [] (act :version))

(defn synchronize [] (act :sync))

(def a-card 1700182610095)

(defn findCards [query]
 (act :findCards {"query" query}))

(defn due-now []
  (findCards "is:due"))

(defn due-by-tomorrow []
  (go (concat (<! (due-now)) (<! (findCards "prop:due=1")))))

(defn cardsToNotes [& cards]
  (act :cardsToNotes
       {"cards" cards}))

(defn cards->words' [cards]
 {:pre [(seq cards)]}
  (go
    (into {}
          (map #(vector (get % :cardId) (get-in % [:fields :Front :value])))
          (<! (act :cardsInfo {:cards cards})))))

(defn cards->words [cards]
  {:pre [(seq cards)]}
  (go
    (into #{}
          (map #(get-in % [:fields :Front :value]))
          (<! (act :cardsInfo {:cards cards})))))

(defn card->word [card]
  (go (first (<! (cards->words [card])))))

(defn words->cards [words]
  (findCards
    (apply str (interpose " or " (map #(str \w \: \" % \") words)))))

(defn answerCards [ease cards]
  (let [ease (get attempt ease)]
    (act :answerCards
         {:answers
          (map
            (fn [card]
              {"cardId" card
               "ease" ease})
            cards)})))

(defn raw-input-results [data]
  (go
    (doseq [[k v] data]
      (answerCards
        (get attempt k)
        v))))

(defn input-results [data]
  (go
    (doseq [[k v] data]
      (answerCards
        (get attempt k)
        (<! (words->cards v))))))

(defn setEaseFactors [ease-permille cards]
  (act :setEaseFactors 
       {:cards cards
        :easeFactors (repeat (count cards) ease-permille)}))

(defn bring-in-random-new-card
  ([] (bring-in-random-new-card nil))
  ([word]
   (go
     (let [random-new-card-id
           (if word
             (->> (<! (words->cards [word]))
                  first)
             (->> (<! (findCards "is:new"))
                  rand-nth))]
       (answerCards :good [random-new-card-id])
       (answerCards :good [random-new-card-id])
       (setEaseFactors 1800 [random-new-card-id])
       (first (<! (cards->words [random-new-card-id])))))))
