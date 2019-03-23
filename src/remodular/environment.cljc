(ns remodular.environment)

(def debug #?(:clj  true                                    ; TODO: other way to indicate debug mode in java world?
              :cljs goog.DEBUG))
