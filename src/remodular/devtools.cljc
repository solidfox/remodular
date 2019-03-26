(ns remodular.devtools
  (:require [clojure.pprint]))

(defn spy [& args]
  (doall
    (for [arg args]
      (clojure.pprint/pprint arg)))
  (last args))
