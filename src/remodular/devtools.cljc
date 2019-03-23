(ns remodular.devtools)

(defn spy [& args]
  (doall
    (for [arg args]
      (clojure.pprint/pprint arg)))
  (last args))
