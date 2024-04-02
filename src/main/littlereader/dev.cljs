(ns littlereader.dev
  (:require [portal.web]))

(defonce _ (add-tap portal.web/submit))
(def p (portal.web/open))
