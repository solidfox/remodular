(ns remodular.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [remodular.devtools :as dt]
            [remodular.environment :as env]
            [ysera.error :refer [error]]
            [ysera.test :as yt :refer [is is= is-not]]))

(s/def ::state map?)
(s/def ::state-path (s/with-gen (s/and #(not (nil? %)) seqable?)
                                (fn [] (gen/return []))))

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

(s/def ::name keyword?)

(s/def ::data any?)

(s/def ::event (s/keys :req-un [::state-path]
                       :opt-un [::name
                                ::data]))
(defn create-action
  {:test (fn []
           (yt/is= (create-action {:name        ::test
                                   :fn-and-args [identity]})
                   {:name        ::test
                    :fn-and-args [identity]})
           (yt/is= (create-action {:fn-and-args [identity :test]})
                   {:fn-and-args [identity :test]})
           (yt/is= (create-action [cons 1])
                   {:fn-and-args [cons 1]}))}
  [{name         :name
    fn-and-args  :fn-and-args
    state-path   :state-path
    source-event :source-event
    :as          args}]
  (cond
    (map? args)
    (merge (when-not (nil? name)
             {:name name})
           (when-not (nil? source-event)
             {:source-event source-event})
           (when-not (nil? state-path)
             {:state-path state-path})
           {:fn-and-args fn-and-args})

    (sequential? args)
    {:fn-and-args args}))


(defn create-event
  {:spec (s/fdef create-event :args (s/cat :kwargs (s/keys :opt-un [::name
                                                                    ::data
                                                                    ::state-path])))
   :test (fn []
           (yt/is= (create-event {:name       ::old-name
                                  :state-path []
                                  :data       {}
                                  :actions    [(create-action [cons 1])]})
                   {:name       ::old-name
                    :state-path []
                    :data       {}
                    :actions    [(create-action [cons 1])]})
           (yt/is= (create-event {})
                   {:state-path []}))}
  [{name       :name
    data       :data
    state-path :state-path
    actions    :actions
    :as        kwargs}]
  (update kwargs :state-path (fn [state-path] (or state-path []))))

(defn triggered-by-self?
  {:test (fn []
           (is (triggered-by-self? (create-event {:name :test :state-path []})))
           (is-not (triggered-by-self? {:state-path [:some :child :path]})))}
  [event]
  (empty? (:state-path event)))

(s/def ::action map?)
(s/def ::actions (s/with-gen (s/and seqable?
                                    (s/coll-of ::action))
                             (fn [] (gen/return []))))

(s/def ::event-context (s/keys :req-un [::state-path]))
(s/def ::descendant-actions ::actions)

(s/def ::event-handler-fn fn?)
(def mock-event-handler-fn (fn [event {:keys [state event-context]}]
                             {:bubble-event nil
                              :actions      []}))

(s/def ::event-handler (s/keys :req-un [::event-context
                                        ::event-handler-fn]))
(defn create-event-handler
  {:test (fn [] (let [event-handler-fn (fn [event & {:keys [state descendant-actions]}]
                                         [nil []])]
                  (yt/is= (create-event-handler {:event-handler-fn event-handler-fn
                                                 :event-context    {:state-path []}})
                          {:event-handler-fn event-handler-fn
                           :event-context    {:state-path []}})))}
  [{:keys [event-handler-fn event-context]}]
  {:event-handler-fn event-handler-fn
   :event-context    event-context})
(s/def ::event-handler-chain (s/coll-of ::event-handler))
(s/def ::app-trigger-event (s/fspec :args (s/cat :event ::event :kwargs (s/keys* :req-un [::event-handler-chain]))))
(s/def ::module-context (s/keys :req-un [::app-trigger-event ::state-path ::event-handler-chain]))
(s/def ::parent-context ::module-context)
(s/def ::child-state-path ::state-path)

(defn noop-event-handler-fn [_ _] {:actions []})

(defn create-event-context [module-context]
  (dissoc module-context :event-handler-chain :app-trigger-event))

(defn add-event-handler-fn
  {:test (fn []
           (let [event-handler-fn mock-event-handler-fn]
             (yt/is= (->> (add-event-handler-fn {:state-path       []
                                                 :event-handler-chain []
                                                 :app-trigger-event   (fn app-trigger-event [x y z])}
                                                event-handler-fn)
                          :event-handler-chain (first) :event-handler-fn)
                     event-handler-fn)))}
  [module-context event-handler-fn]
  (let [event-handler (create-event-handler {:event-handler-fn event-handler-fn
                                             :event-context    (create-event-context module-context)})]
    (update module-context :event-handler-chain (fn [event-handler-chain] (cons event-handler event-handler-chain)))))

(s/def ::child-handle-event ::event-handler-fn)

(defn create-child-context
  {:spec (s/fdef create-child-context :args (s/keys* :req-un [::parent-context
                                                              ::child-state-path]
                                                     :opt-un [::child-handle-event]))}
  [& {:keys [parent-context
             child-state-path
             child-handle-event]}]
  (-> parent-context
      (assoc :state-path child-state-path)
      (add-event-handler-fn (or child-handle-event noop-event-handler-fn))))

(defn trigger-event
  {:spec (s/fdef trigger-event :args (s/keys* :req-un [::module-context
                                                       (or ::name
                                                           ::event)]
                                              :opt-un [::data]))}
  [& {{app-trigger-event   :app-trigger-event
       event-handler-chain :event-handler-chain} :module-context
      :keys                                      [name data event]}]
  (let [app-trigger-event app-trigger-event]
    (app-trigger-event (create-event (or event {:name       name
                                                :data       data}))
                       :event-handler-chain event-handler-chain)))

(comment "# EVENT HANDLING"
         "An event handler must look as follows"
         '(defn handle-event [event {:keys [state event-context]}]
            {:bubble-event "Create the event that the parent should see here."
             :actions      "Any actions that should be performed in response to the event."})
         "By default actions are appended to the actions already added by children, performing child actions first"
         "and then parent actions. To remove or reorder child actions one may return actions inside the bubble event."
         "When the bubble event contains actions other actions are ignored and the bubble event actions are used as is."
         "Example:"
         '(defn handle-event [event {:keys [state event-context]}]
            {:bubble-event (create-event {:name    :my-bubbled-event ; :name "may be left out"
                                          :actions (concat [my actions performed before child actions] (:actions event))})
             :actions      [these are ignored and :actions can be left out]}))

(defn append-action
  [actions action]
  (concat actions [action]))

(defn bubbles-by-default? [event]
  (and (keyword? (:name event))
       (not-empty (namespace (:name event)))))

(defn handle-event
  {:spec (s/fdef handle-event
                 :args (s/cat :kwargs (s/keys :req-un [::app-state
                                                       ::event-handler
                                                       ::event]))
                 :ret ::event)
   :test (fn []
           (let [app-state     {:modules {:parent {:modules :child}}}
                 event-context {:absolute-state-path [:modules :parent]
                                :state-path          [:modules :parent]}]
             (yt/is= (handle-event
                       {:app-state     app-state
                        :event-handler (create-event-handler {:event-context    event-context
                                                              :event-handler-fn (fn [event {:keys [state
                                                                                                   event-context]}]
                                                                                  (yt/is= state {:modules :child})
                                                                                  (yt/is (triggered-by-self? event))
                                                                                  (yt/is= (:name event) :test-event)
                                                                                  (create-action {:fn-and-args [identity :parent-action]}))})
                        :event         (create-event {:name    :test-event
                                                      :actions [(create-action {:fn-and-args [identity :child-action] :state-path [:modules :parent :modules :child]})]})})
                     {:state-path []
                      :actions    [(create-action {:fn-and-args [identity :child-action] :state-path [:modules :parent :modules :child]})
                                   (create-action {:fn-and-args [identity :parent-action] :state-path [:modules :parent]})]})
             (yt/is= (handle-event
                       {:app-state     app-state
                        :event-handler (create-event-handler {:event-context event-context :event-handler-fn (fn [_ _])})
                        :event         (create-event {:name ::test-event})})
                     {:name       ::test-event
                      :state-path []
                      :actions    []})
             (yt/is= (handle-event
                       {:app-state     app-state
                        :event-handler (create-event-handler {:event-context event-context :event-handler-fn (fn [_ _])})
                        :event         (create-event {:name :test-event})})
                     {:state-path []
                      :actions    []}))
           (comment "More thoroughly tested through get-actions below."))}

  [{:keys [app-state
           event-handler
           event]}]
  (let [event-context               (:event-context event-handler)
        absolute-handler-state-path (:absolute-state-path event-context)
        _                           (assert (not (nil? absolute-handler-state-path)) "Remodular: The event handler passed to handle-event did not have an absolute state path. Before passing an event handler to handle-event its relative state path must be resolved to an absolute state path.")
        event-handler-fn            (:event-handler-fn event-handler)
        module-local-state          (get-in app-state absolute-handler-state-path)
        event-handler-result        (event-handler-fn event {:state         module-local-state
                                                             :event-context event-context})
        bubble-event                (or (:bubble-event event-handler-result)
                                        (when (bubbles-by-default? event) (dissoc event :actions)))
        new-actions-or-fn-and-args  (if-let [actions (:actions event-handler-result)] actions event-handler-result)
        action-not-wrapped-in-list? (or (fn? (first new-actions-or-fn-and-args))
                                        (:fn-and-args new-actions-or-fn-and-args))
        event-handler-actions       (as-> new-actions-or-fn-and-args $
                                          (if action-not-wrapped-in-list? [$] $)
                                          (map (fn [action-params]
                                                 (create-action (merge (if (map? action-params)
                                                                         action-params
                                                                         {:fn-and-args action-params})
                                                                       {:state-path (concat absolute-handler-state-path
                                                                                            (:state-path action-params))}))) $))
        new-event-actions           (->> (or (:actions bubble-event)
                                             (concat (:actions event)
                                                     event-handler-actions)))]
    (create-event (merge bubble-event
                         {:actions new-event-actions}))))

(defn resolve-state-paths
  {:test (fn []
           (yt/is= (resolve-state-paths [(create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path [:modules :module2]}})
                                         (create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path [:modules :module1]}})
                                         (create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path []}})])
                   [(create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path          [:modules :module2]
                                                                                                   :absolute-state-path [:modules :module1 :modules :module2]}})
                    (create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path          [:modules :module1]
                                                                                                   :absolute-state-path [:modules :module1]}})
                    (create-event-handler {:event-handler-fn mock-event-handler-fn :event-context {:state-path          []
                                                                                                   :absolute-state-path []}})]))}
  [event-handler-chain]
  (->> event-handler-chain
       (reverse)
       (reduce (fn [event-handler-chain-with-resolved-state-paths event-handler]
                 (let [path-to-parent             (or (-> event-handler-chain-with-resolved-state-paths
                                                          (last)
                                                          (get-in [:event-context :absolute-state-path]))
                                                      [])
                       full-path-to-event-handler (concat path-to-parent (get-in event-handler [:event-context :state-path]))]
                   (conj event-handler-chain-with-resolved-state-paths
                         (assoc-in event-handler [:event-context :absolute-state-path]
                                   full-path-to-event-handler))))
               [])
       (reverse)))

(defn get-actions
  {:spec (s/fdef get-actions
                 :args (s/cat :event ::event
                              :state ::state
                              :event-handler-chain (s/coll-of ::event-handler))
                 :ret ::actions)
   :test (fn []
           (yt/is= (get-actions (create-event {:name       :test-event
                                               :state-path []})
                                {}
                                [(create-event-handler {:event-handler-fn (fn [& args] {:actions [(create-action {:fn-and-args [cons 4]
                                                                                                                  :state-path  [:path 4]})
                                                                                                  [cons 3]]})
                                                        :event-context    {:state-path [:path 3]}})
                                 (create-event-handler {:event-handler-fn (fn [& args] (create-action {:fn-and-args [cons 2]
                                                                                                       :state-path  [:path 3]}))
                                                        :event-context    {:state-path [:path 2]}})
                                 (create-event-handler {:event-handler-fn (fn [& args] [conj 1])
                                                        :event-context    {:state-path [:path 1]}})])
                   [(create-action {:fn-and-args [cons 4]
                                    :state-path  [:path 1 :path 2 :path 3 :path 4]})
                    (create-action {:fn-and-args [cons 3]
                                    :state-path  [:path 1 :path 2 :path 3]})
                    (create-action {:fn-and-args [cons 2]
                                    :state-path  [:path 1 :path 2 :path 3]})
                    (create-action {:fn-and-args [conj 1]
                                    :state-path  [:path 1]})]))}
  [event app-state event-handler-chain & {:keys [log-options]}]
  (dt/log-if log-options
             "Getting actions from event handler chain"
             (->> event-handler-chain
                  (resolve-state-paths)
                  (reduce
                    (fn [reduced-event event-handler]
                      (dt/log-if log-options
                                 "Event handler"
                                 event-handler
                                 "Event"
                                 reduced-event
                                 "Result"
                                 (handle-event {:app-state     app-state
                                                :event-handler event-handler
                                                :event         reduced-event})))
                    event)
                  :actions)))

(when env/debug (stest/instrument))
