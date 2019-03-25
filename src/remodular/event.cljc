(ns remodular.event
  (:require [clojure.spec.alpha :as s]
            [remodular.core :as core]
            [clojure.spec.test.alpha :as stest]
            [remodular.environment :as env]
            [ysera.test :as yt]
            [remodular.devtools :as dt]))

(s/def ::name qualified-keyword?)
(s/def ::event (s/keys :req [::name
                             ::core/state-path]
                       :opt [::data]))
(defn create-event
  {:spec (s/fdef create-event :args (s/cat :kwargs (s/keys :req-un [::name
                                                                    ::core/state-path])))
   :test (fn []
           (yt/is= (create-event {:name       ::old-name
                                  :state-path []
                                  :data       {}})
                   {::name            ::old-name
                    ::core/state-path []
                    ::data            {}}))}
  [{name       :name
    state-path :state-path
    data       :data}]
  {::name            name
   ::core/state-path state-path
   ::data            data})

(s/def ::source-event ::event)
(s/def ::fn-and-args (s/cat :fn fn?
                            :args (s/* any?)))
(s/def ::action (s/keys :req [::core/state-path
                              ::fn-and-args]
                        :opt [::name
                              ::source-event]))
(defn create-action
  {:spec (s/fdef create-action
                 :args (s/cat :kwargs (s/or :state-path (s/keys :req-un [::fn-and-args
                                                                         (or ::core/state-path
                                                                             ::source-event)]
                                                                :opt-un [::name])))
                 :ret ::action)
   :test (fn []
           (yt/is= (create-action {:name        ::test
                                   :fn-and-args [identity]
                                   :state-path  []})
                   {::name            ::test
                    ::fn-and-args     [identity]
                    ::core/state-path []})
           (yt/is= (create-action {:fn-and-args [identity :test]
                                   :state-path  []})
                   {::fn-and-args     [identity :test]
                    ::core/state-path []}))}
  [{name         :name
    fn-and-args  :fn-and-args
    state-path   :state-path
    source-event :source-event}]
  (merge (when-not (nil? name) {::name name})
         {::fn-and-args     fn-and-args
          ::core/state-path (or state-path (:state-path source-event))}))

(s/def ::event-handler-fn fn?)
(s/def ::event-handler (s/keys :req-un [::core/state-path
                                        ::event-handler-fn]))
(defn create-event-handler
  {:spec (s/fdef create-event-handler
                 :args (s/cat :kwargs (s/keys* :req-un [::event-handler-fn
                                                        ::core/state-path]))
                 :ret ::event-handler)
   :test (fn [] (yt/is= (create-event-handler :event-handler-fn identity
                                              :state-path [])
                        {::event-handler-fn identity
                         ::core/state-path  []}))}
  [& {:keys [event-handler-fn state-path]}]
  {::event-handler-fn event-handler-fn
   ::core/state-path  state-path})
(defn append-action
  [actions action]
  (concat actions [action]))

(s/def ::action-list (s/and seqable?
                            (s/coll-of ::action)))
(defn get-actions-from-event
  {:spec (s/fdef get-actions-from-event
                 :args (s/cat :state ::core/state
                              :event-handler ::event-handler
                              :event ::event
                              :descendant-actions ::action-list)
                 :ret (s/cat :event ::event :action-list ::action-list))
   :test (fn [] (yt/is= (get-actions-from-event {:test {:path {:key :this-should-be-passed}}}
                                                {:state-path       [:test :path]
                                                 :event-handler-fn (fn [state event descendants-actions]
                                                                     (when (and (= (:key state) :this-should-be-passed)
                                                                                (= (::name event) ::test-event))
                                                                       [(create-event {:name       ::bubbled-test-event
                                                                                       :state-path []})
                                                                        (append-action descendants-actions
                                                                                       (create-action {:fn-and-args [identity :parent] :state-path []}))]))}
                                                (create-event {:name       ::test-event
                                                               :state-path []})
                                                [(create-action {:fn-and-args [identity :child] :state-path []})])
                        [(create-event {:name       ::bubbled-test-event
                                        :state-path []})
                         [(create-action {:fn-and-args [identity :child] :state-path []})
                          (create-action {:fn-and-args [identity :parent] :state-path []})]]))}

  [state
   event-handler
   event
   descendant-actions]
  (let [{state-path       :state-path
         event-handler-fn :event-handler-fn}                ; fn [state event descendant-actions] -> [bubbled-event actions]
        event-handler]
    (event-handler-fn (get-in state state-path) event descendant-actions)))

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
  {:spec (s/fdef get-actions
                 :args (s/cat :event ::event
                              :state map?
                              :event-handler-chain (s/coll-of ::event-handler))
                 :ret ::action-list)}
  [event state event-handler-chain]
  (->> event-handler-chain
       (reduce
         (fn [[event actions] event-handler]
           (if event
             (get-actions-from-event state event-handler event actions)
             (reduced [nil actions])))
         [event []])
       (second)))

(when env/debug (stest/instrument))
