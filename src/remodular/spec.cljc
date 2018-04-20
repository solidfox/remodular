(ns remodular.spec
  (:require #?(:clj [clojure.spec.alpha :as s]
               :cljs [cljs.spec.alpha :as s])))

(s/def ::name keyword?)
(s/def ::data map?)
(s/def ::actions seqable?)
(s/def ::event (s/and map?
                      (s/keys :opt [::name ::data ::actions])))
;
;(s/def :unq/action (s/cat :function fn? :arguments (s/* true)))
;
;(s/def ::action (s/keys :req-un [::action ::state-path]))
;
;
;
;
;[{:action [fn * args]
;  :state-path []}]
