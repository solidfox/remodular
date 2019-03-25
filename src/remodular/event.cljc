(ns remodular.event
  (:require [clojure.spec.alpha :as s]
            [remodular.core :as core]
            [clojure.spec.test.alpha :as stest]
            [remodular.environment :as env]
            [ysera.test :as yt]
            [remodular.devtools :as dt]))

(s/def ::name qualified-keyword?)
(s/def ::state map?)
(s/def ::state-path (s/and #(not (nil? %)) seqable?))
(s/def ::event (s/keys :req [::name
                             ::state-path]
                       :opt [::data]))
(s/def ::source-event ::event)
(s/def ::fn-and-args (s/cat :fn fn?
                            :args (s/* any?)))

(s/def ::action (s/keys :req [::state-path
                              ::fn-and-args]
                        :opt [::name
                              ::source-event]))
(s/def ::action-list (s/and seqable?
                            (s/coll-of ::action)))
(s/def ::event-handler-fn fn?)
(s/def ::event-handler (s/keys :req-un [::state-path
                                        ::event-handler-fn]))

(defn create-action
  {:spec (s/fdef create-action
                 :args (s/cat :kwargs (s/or :state-path (s/keys :req-un [::fn-and-args
                                                                         ::state-path]
                                                                :opt-un [::name])
                                            :source-event (s/keys :req-un [::fn-and-args
                                                                           ::source-event]
                                                                  :opt-un [::name])))
                 :ret ::action)
   :test (fn []
           (yt/is= (create-action {:name        ::test
                                   :fn-and-args [identity]
                                   :state-path  []})
                   {::name        ::test
                    ::fn-and-args [identity]
                    ::state-path  []})
           (yt/is= (create-action {:fn-and-args [identity :test]
                                   :state-path  []})
                   {::fn-and-args [identity :test]
                    ::state-path  []}))}
  [{name         :name
    fn-and-args  :fn-and-args
    state-path   :state-path
    source-event :source-event}]
  (merge (when-not (nil? name) {::name name})
         {::fn-and-args fn-and-args
          ::state-path  (or state-path (:state-path source-event))}))

(defn create-event
  {:spec (s/fdef create-event :args (s/cat :kwargs (s/keys :req-un [::name
                                                                    ::state-path])))
   :test (fn []
           (yt/is= (create-event {:name       ::old-name
                                  :state-path []
                                  :data       {}})
                   {::name       ::old-name
                    ::state-path []
                    ::data       {}}))}
  [{name       :name
    state-path :state-path
    data       :data}]
  {::name       name
   ::state-path state-path
   ::data       data})

(defn append-action
  [actions action]
  (concat actions [action]))

(defn get-actions-from-event
  {:spec (s/fdef get-actions-from-event
                 :args (s/cat :state map?
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
