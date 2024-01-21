(ns littlereader.state)

(defonce an-atm
  (atom {:pending-input {:again #{}
                         :hard #{}
                         :good #{}
                         :easy #{}}}))

