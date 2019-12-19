(ns remodular.devtools
  (:require [clojure.pprint]))

(defn spy [& args]
  (doall
    (for [arg args]
      (clojure.pprint/pprint arg)))
  (last args))

(def log spy)

(defn log-if [condition & args]
  (if condition
    (apply log args)
    (last args)))

(defn warn [& args]
  (apply spy (concat ["WARNING:"] args)))
