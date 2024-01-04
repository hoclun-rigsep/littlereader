(ns littlereader.server.effects
  (:require
    [littlereader.anki :as anki]))

(defmulti handle-effect (comp first first))
