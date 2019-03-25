(ns remodular.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [remodular.environment :as env]
            [ysera.error :refer [error]]
            [ysera.test :as yt :refer [is is= is-not]]))

(s/def ::state map?)
(s/def ::state-path (s/with-gen (s/and #(not (nil? %)) seqable?)
                                (fn [] (gen/return []))))

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

(s/def ::name qualified-keyword?)
(s/def ::data any?)
(s/def ::event (s/keys :req [::name
                             ::state-path]
                       :opt [::data]))
(defn create-event
  {:spec (s/fdef create-event :args (s/cat :kwargs (s/keys :req-un [::name
                                                                    ::state-path])))
   :test (fn []
           (yt/is= (create-event {:name       ::old-name
                                  :state-path []
                                  :data       {}})
                   {::name            ::old-name
                    ::state-path []
                    ::data            {}}))}
  [{name       :name
    state-path :state-path
    data       :data}]
  {::name            name
   ::state-path state-path
   ::data            data})

(s/def ::source-event ::event)
(s/def ::fn-and-args (s/cat :fn fn?
                            :args (s/* any?)))
(s/def ::action (s/keys :req [::state-path
                              ::fn-and-args]
                        :opt [::name
                              ::source-event]))
(defn create-action
  {:spec (s/fdef create-action
                 :args (s/cat :kwargs (s/or :state-path (s/keys :req-un [::fn-and-args
                                                                         (or ::state-path
                                                                             ::source-event)]
                                                                :opt-un [::name])))
                 :ret ::action)
   :test (fn []
           (yt/is= (create-action {:name        ::test
                                   :fn-and-args [identity]
                                   :state-path  []})
                   {::name            ::test
                    ::fn-and-args     [identity]
                    ::state-path []})
           (yt/is= (create-action {:fn-and-args [identity :test]
                                   :state-path  []})
                   {::fn-and-args     [identity :test]
                    ::state-path []}))}
  [{name         :name
    fn-and-args  :fn-and-args
    state-path   :state-path
    source-event :source-event}]
  (merge (when-not (nil? name) {::name name})
         {::fn-and-args     fn-and-args
          ::state-path (or state-path (:state-path source-event))}))

(s/def ::action-list (s/with-gen (s/and seqable?
                                        (s/coll-of ::action))
                                 (fn [] (gen/return []))))
(s/def ::event-handler-fn
  (s/with-gen
    (s/fspec :args (s/cat :event ::event
                          :kwargs (s/keys* :req-un [::state
                                                    ::action-list])))
    (fn [] (gen/return (fn [event & {:keys [state descendant-actions]}]
                         [(s/gen ::event) (s/gen ::action-list)])))))
(s/def ::event-handler (s/keys :req [::state-path
                                     ::event-handler-fn]))
(defn create-event-handler
  {:spec (s/fdef create-event-handler
                 :args (s/cat :kwargs (s/keys* :req-un [::event-handler-fn
                                                        ::state-path]))
                 :ret ::event-handler)
   :test (fn [] (let [event-handler-fn (fn [event & {:keys [state descendant-actions]}]
                                         [nil []])]
                  (yt/is= (create-event-handler :event-handler-fn event-handler-fn
                                                :state-path [])
                          {::event-handler-fn event-handler-fn
                           ::state-path  []})))}
  [& {:keys [event-handler-fn state-path]}]
  {::event-handler-fn event-handler-fn
   ::state-path  state-path})

(defn append-action
  [actions action]
  (concat actions [action]))
(defn get-actions-from-event
  {:spec (s/fdef get-actions-from-event
                 :args (s/cat :state ::state
                              :event-handler ::event-handler
                              :event ::event
                              :descendant-actions ::action-list)
                 :ret (s/cat :event ::event :action-list ::action-list))
   :test (fn [] (yt/is= (get-actions-from-event {:test {:path {:key :this-should-be-passed}}}
                                                (create-event-handler :state-path [:test :path]
                                                                      :event-handler-fn (fn [event & {:keys [state descendant-actions]}]
                                                                                          (when (and (= (:key state) :this-should-be-passed)
                                                                                                     (= (::name event) ::test-event))
                                                                                            [(create-event {:name       ::bubbled-test-event
                                                                                                            :state-path []})
                                                                                             (append-action descendant-actions
                                                                                                            (create-action {:fn-and-args [identity :parent] :state-path []}))])))
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
  (let [{state-path       ::state-path
         event-handler-fn ::event-handler-fn} event-handler]
    (event-handler-fn event :state (get-in state state-path) :descendant-actions descendant-actions)))

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
