(ns littlereader.state)

(defonce an-atm
  (atom {:due-now {}
         :due-by-tomorrow {}
         :pending-input {:again #{}
                         :hard  #{}
                         :good  #{}
                         :easy  #{}}}))

