(ns remodular.core
  (:require [clojure.pprint :refer [pprint]]
            [ysera.test :refer [is is= is-not]]
            [ysera.error :refer [error]]
            [clojure.spec.alpha :as s]))

(s/def ::state map?)
(s/def ::state-path (s/and #(not (nil? %)) seqable?))

(defn triggered-by-me?
  {:test (fn []
           (is (triggered-by-me? {}))
           (is (triggered-by-me? {:state-path []}))
           (is-not (triggered-by-me? {:state-path [:child :path]})))}
  [event]
  (empty? (:state-path event)))

(defn triggered-by-descendant-of-child?
  {:test (fn []
           (is (triggered-by-descendant-of-child? [:child :path]
                                                  {:state-path [:child :path :grandchild :path]}))
           (is (triggered-by-descendant-of-child? [:child :path]
                                                  {:state-path (list :child :path)}))
           (is (triggered-by-descendant-of-child? [:child :path]
                                                  {:state-path [:child :path]}))
           (is-not (triggered-by-descendant-of-child? [:child :path]
                                                      {:state-path [:other :child :path :grandchild :path]})))}
  [child-state-path event]
  (= child-state-path
     (take (count child-state-path)
           (:state-path event))))

(defn triggered-by-child?
  {:test (fn []
           (is (triggered-by-child? [:child :path]
                                    {:state-path [:child :path]}))
           (is-not (triggered-by-child? [:child :path]
                                        {:state-path [:child :path :grandchild :path]})))}
  [child-state-path event]
  (= (:state-path event)
     child-state-path))

(defn- prepend-state-path
  {:test (fn []
           (is= (-> {}
                    (prepend-state-path [:1 :2]))
                {:state-path [:1 :2]})
           (is= (-> {:state-path [:3 :4]}
                    (prepend-state-path [:1 :2]))
                {:state-path [:1 :2 :3 :4]})
           (is= (-> {:state-path (list :3 :4)}
                    (prepend-state-path (list :1 :2)))
                {:state-path [:1 :2 :3 :4]}))}
  [map state-path]
  {:pre [(s/valid? map? map)
         (s/valid? seqable? state-path)]}
  (update map :state-path (fn [path-to-deep-descendant]
                            (concat state-path path-to-deep-descendant))))

(defn prepend-state-path-to-services
  {:test (fn []
           (is= (-> [{} {}]
                    (prepend-state-path-to-services [:1 :2]))
                (repeat 2 {:state-path [:1 :2]})))}
  [services state-path]
  (map (fn [service] (prepend-state-path service state-path))
       services))

(defn- prepend-state-path-to-event
  {:test (fn []
           (is= (-> {:state-path [:3 :4]
                     :actions    [{:state-path [:3 :4]}
                                  {:state-path []}]}
                    (prepend-state-path-to-event [:1 :2]))
                {:state-path [:1 :2 :3 :4]
                 :actions    [{:state-path [:1 :2 :3 :4]}
                              {:state-path [:1 :2]}]}))}
  [event path-to-me]
  (-> event
      (prepend-state-path path-to-me)
      (update :actions (fn [actions]
                         (map (fn [action]
                                (prepend-state-path action path-to-me))
                              actions)))))

