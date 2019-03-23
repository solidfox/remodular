(ns remodular.event
  (:require [clojure.spec.alpha :as s]
            [remodular.core :as core]
            [clojure.spec.test.alpha :as stest]
            [remodular.environment :as env]
            [ysera.test :as yt]
            [remodular.devtools :as dt]))

(s/def ::name keyword?)
(s/def ::event (s/keys :req [::name]
                       :opt [::data]))

(s/def ::state-path seqable?)
(s/def ::event-handler-fn fn?)

(s/def ::event-handler (s/keys :req-un [::state-path ::event-handler-fn]))

(defn create-event
  "Takes over ownership of an event by applying a view-module's own API naming to it."
  {:test (fn []
           (yt/is= (create-event {:name :old-name
                                  :data {}})
                   {::name :old-name
                    ::data {}}))}
  [{name :name
    data :data
    :as  event}]
  {::name name
   ::data data})

(defn append-action
  [actions action]
  (concat actions [action]))

(defn get-actions-from-event
  {:spec (s/fdef get-actions-from-event
                 :args (s/cat :state map?
                              :event-handler ::event-handler
                              :event ::event
                              :descendant-actions seqable?)
                 :ret (s/coll-of map?))
   :test (fn [] (yt/is= (get-actions-from-event {:test {:path {:key :this-should-be-passed}}}
                                                {:state-path       [:test :path]
                                                 :event-handler-fn (fn [state event descendants-actions]
                                                                     (when (and (dt/spy (= (:key state) :this-should-be-passed))
                                                                                (= (::name event) :test-event))
                                                                       [(create-event {:name :bubbled-test-event})
                                                                        (append-action descendants-actions (core/create-action {:fn-and-args [identity :parent]}))]))}
                                                (create-event {:name :test-event})
                                                [(core/create-action {:fn-and-args [identity :child]})])
                        [(create-event {:name :bubbled-test-event}) [(core/create-action {:fn-and-args [identity :child]})
                                                                     (core/create-action {:fn-and-args [identity :parent]})]]))}

  [state
   event-handler
   event
   descendant-actions]
  (let [{state-path       :state-path
         event-handler-fn :event-handler-fn}                ; fn [state event descendant-actions] -> [bubbled-event actions]
        event-handler]
    (event-handler-fn (get-in state state-path) event descendant-actions)))
(when env/debug (stest/instrument [`get-actions-from-event]))

;(comment "Example event-handler-chain"
;         ({:state-path       []
;           :event-handler-fn (event-handler [state event actions] -> more-actions)
;           {:state-path       [:pages :welcome]
;            :event-handler-fn (event-handler [state event actions] -> more-actions)
;                             {:state-path       [:modules :user-profile]
;                              :event-handler-fn (event-handler [state event actions] -> more-actions)}}})
;         "Reduced to"
;         ({:state-path [:pages :welcome :modules :user-profile] :action-fn (fn [module-state]
;                                                                             modified-module-state)}))

(defn get-actions
  [event state event-handler-chain]
  (->> event-handler-chain
       (reduce
         (fn [[event actions] event-handler]
           (if event
             (get-actions-from-event state event-handler event actions)
             (reduced [nil actions])))
         [event []])))
