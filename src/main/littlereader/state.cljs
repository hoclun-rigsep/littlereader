(ns littlereader.state)

(defonce an-atm
  (atom {:active-view :landing
         :current 0}))
