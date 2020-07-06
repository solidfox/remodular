(ns remodular.core
  (:require [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.spec.test.alpha :as stest]
            [remodular.devtools :as dt]
            [remodular.environment :as env]
            [ysera.error :refer [error]]
            [ysera.test :as yt :refer [is is= is-not]]))

(s/def ::state any?)
(s/def ::state-path (s/with-gen (s/and #(not (nil? %)) seqable?)
                                (fn [] (gen/return []))))
(def mock-state-path [:mock-module :state])

(defn triggered-by-descendant-of-child?
  {:test (fn []
           (is (triggered-by-descendant-of-child? {:state-path [:child :path :grandchild :path]}
                                                  [:child :path]))
           (is (triggered-by-descendant-of-child? {:state-path (list :child :path)}
                                                  [:child :path]))
           (is (triggered-by-descendant-of-child? {:state-path [:child :path]}
                                                  [:child :path]))
           (is-not (triggered-by-descendant-of-child? {:state-path [:other :child :path :grandchild :path]}
                                                      [:child :path])))}
  [event child-state-path]
  {:pre [(map? event)
         (sequential? child-state-path)]}
  (= child-state-path
     (take (count child-state-path)
           (:state-path event))))

(defn triggered-by-child?
  {:test (fn []
           (is (triggered-by-child? {:state-path [:child :path]}
                                    [:child :path]))
           (is-not (triggered-by-child? {:state-path [:child :path :grandchild :path]}
                                        [:child :path])))}
  [event child-state-path]
  {:pre [(map? event)
         (sequential? child-state-path)]}
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

(s/def ::name keyword?)

(s/def ::data any?)

(s/def ::event (s/keys :opt-un [::name
                                ::data
                                ::state-path]))
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
    (merge {:fn-and-args fn-and-args}
           (when-not (nil? name)
             {:name name})
           (when-not (nil? source-event)
             {:source-event source-event})
           (when-not (nil? state-path)
             {:state-path state-path}))

    (sequential? args)
    {:fn-and-args args}))


(defn create-event
  {:spec (s/fdef create-event :args (s/cat :kwargs (s/keys :opt-un [::name
                                                                    ::data
                                                                    ::state-path
                                                                    ::actions])))
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

(s/def ::fn-and-args (s/cat :fn fn? :args (s/* any?)))
(s/def ::action map?)
(s/def ::actions (s/or ::fn-and-args
                       (s/coll-of ::fn-and-args)
                       ::action
                       (s/coll-of ::action)))

(s/def ::event-context (s/keys :req-un [::state-path]))
(def mock-event-context {:state-path mock-state-path})
(s/def ::descendant-actions ::actions)

(s/def ::event-handler-fn (s/with-gen fn?
                                      (fn [] (gen/return (fn [])))))
(def mock-event-handler-fn (fn [event {:keys [state event-context]}]
                             {:bubble-event nil
                              :actions      []}))

(s/def ::exports-key any?)
(s/def ::module-instance-identifier (s/keys :req-un [::exports-key ::state-path]))
(s/def ::module-branch (s/coll-of ::module-instance-identifier))
(s/def ::event-origin-module-branch ::module-branch)
(s/def ::app-trigger-event (s/fspec :args (s/cat :event ::event :kwargs (s/keys :req-un [::event-origin-module-branch]))))
(defn mock-app-trigger-event [event {:keys [event-origin-module-branch]}] event)
(s/def ::module-context (s/keys :req-un [::app-trigger-event ::state-path]
                                :opt-un [::module-branch]))
(s/def ::parent-context ::module-context)
(s/def ::child-state-path ::state-path)

(defn noop-event-handler-fn [_ _] [])

(defn add-module-instance-identifier
  {:test (fn []
           (yt/is= (add-module-instance-identifier {:state-path        [:1 :2]
                                                    :app-trigger-event mock-app-trigger-event}
                                                   ::test-module-key)
                   {:state-path        [:1 :2]
                    :app-trigger-event mock-app-trigger-event
                    :module-branch     [{:exports-key ::test-module-key
                                         :state-path  [:1 :2]}]}))}
  [module-context module-exports-key]
  (let [module-instance-identifier {:exports-key module-exports-key
                                    :state-path  (:state-path module-context)}] ; TODO inject context on event instead
    (update module-context :module-branch (fn [module-branch]
                                            (conj (or module-branch []) module-instance-identifier)))))

(s/def ::child-module-exports-key ::exports-key)

(defn create-child-context
  {:spec (s/fdef create-child-context :args (s/keys* :req-un [::parent-context
                                                              ::child-state-path
                                                              ::child-module-exports-key]))
   :test (fn [] (yt/is= (create-child-context :parent-context {:state-path        []
                                                               :app-trigger-event mock-app-trigger-event}
                                              :child-state-path [:1 :2]
                                              :child-module-exports-key ::test-module-key)
                        {:state-path        [:1 :2]
                         :app-trigger-event mock-app-trigger-event
                         :module-branch     [{:exports-key ::test-module-key
                                              :state-path  [:1 :2]}]}))}
  [& {:keys [parent-context
             child-state-path
             child-module-exports-key]}]
  (-> parent-context
      (assoc :state-path child-state-path)
      (add-module-instance-identifier child-module-exports-key)))

(defn trigger-event
  {:spec (s/fdef trigger-event :args (s/keys* :req-un [::module-context
                                                       (or ::name
                                                           ::event)]
                                              :opt-un [::data]))
   :test (fn [] (let [passed-event   (atom nil)
                      module-context {:app-trigger-event (fn [event & _] (reset! passed-event event)) :module-branch [] :state-path []}]
                  (trigger-event :module-context module-context
                                 :event {:name       :test :data :bar
                                         :state-path [:foo]})
                  (yt/is= (deref passed-event) {:name       :test :data :bar
                                                :state-path [:foo]})
                  (trigger-event :module-context module-context
                                 :name :goj
                                 :data :moj)
                  (yt/is= (deref passed-event) {:name       :goj
                                                :data       :moj
                                                :state-path []})))}
  [& {:keys [module-context name data event]}]
  (let [{app-trigger-event :app-trigger-event
         module-branch     :module-branch} module-context
        app-trigger-event app-trigger-event
        event-to-trigger  (create-event (or event {:name name
                                                   :data data}))]
    (app-trigger-event event-to-trigger
                       {:event-origin-module-branch module-branch})))

(comment "# EVENT HANDLING"
         "An event handler must look as follows"
         '(defn handle-event [event props]
            {:bubble-event "Create the event that the parent should see here."
             :actions      "Any actions that should be performed in response to the event."})
         "By default actions are appended to the actions already added by children, performing child actions first"
         "and then parent actions. To remove or reorder child actions one may return actions inside the bubble event."
         "When the bubble event contains actions other actions are ignored and the bubble event actions are used as is."
         "Example: "
         '(defn handle-event [event props]
            {:bubble-event (create-event {:name    :my-bubbled-event ; :name "may be left out"
                                          :actions (concat [my actions performed before child actions] (:actions event))})
             :actions      [these are ignored and :actions can be left out]}))

(defn bubbles-by-default?
  {:test (fn []
           (yt/is-not (bubbles-by-default? (create-event {:actions []})))
           (yt/is (bubbles-by-default? (create-event {:name :navigation/go-back}))))}
  [event]
  (and (keyword? (:name event))
       (not-empty (namespace (:name event)))))

(defn action?
  {:test (fn []
           (yt/is (action? {:fn-and-args [cons 1]}))
           (yt/is (action? {:name        :test
                            :fn-and-args [cons 1]}))
           (yt/is-not (action? [cons 1])))}
  [x]
  (and (map? x)
       (contains? x :fn-and-args)))

(defn fn-and-args? [x]
  (and (sequential? x)
       (fn? (first x))))

(defn- extract-actions
  {:test (fn []
           (yt/is= (extract-actions [cons 1] [:absolute-state-path :state :path])
                   [(create-action {:fn-and-args [cons 1]
                                    :state-path  [:absolute-state-path :state :path]})])
           (yt/is= (extract-actions [[cons 1]] [:absolute-state-path :state :path])
                   [(create-action {:fn-and-args [cons 1]
                                    :state-path  [:absolute-state-path :state :path]})])
           (yt/is= (extract-actions {:actions [[cons 1]]} [:absolute-state-path :state :path])
                   [(create-action {:fn-and-args [cons 1]
                                    :state-path  [:absolute-state-path :state :path]})])
           (yt/is= (extract-actions {:actions [cons 1]} [:absolute-state-path :state :path])
                   [(create-action {:fn-and-args [cons 1]
                                    :state-path  [:absolute-state-path :state :path]})])
           (yt/is= (extract-actions {:actions nil} [:absolute-state-path :state :path])
                   [])
           (yt/is= (extract-actions nil [:absolute-state-path :state :path])
                   []))}
  [event-handler-result absolute-state-path]
  (if (not event-handler-result)
    []
    (let [new-actions-or-fn-and-args (if-let [actions (:actions event-handler-result)] actions event-handler-result)
          single-action?             (boolean (or (fn? (first new-actions-or-fn-and-args))
                                                  (:fn-and-args new-actions-or-fn-and-args)))
          event-handler-actions      (as-> new-actions-or-fn-and-args $
                                           (if single-action? [$] $)
                                           (map (fn [action-or-fn-and-args]
                                                  (let [action-state-path (concat absolute-state-path
                                                                                  (:state-path action-or-fn-and-args))]
                                                    (cond (action? action-or-fn-and-args)
                                                          (create-action (assoc action-or-fn-and-args
                                                                           :state-path action-state-path))
                                                          (fn-and-args? action-or-fn-and-args)
                                                          (create-action {:fn-and-args action-or-fn-and-args
                                                                          :state-path  action-state-path})
                                                          :else nil)))
                                                $))]
      (remove nil? event-handler-actions))))

(s/def ::inflated-module map?)

(defn create-source-event [event]
  (dissoc event :actions))

(defn handle-event
  {:spec (s/fdef handle-event
                 :args (s/cat :event ::event
                              :module-handle-event (s/nilable fn?)
                              :inflated-module ::inflated-module)
                 :ret ::event)
   :test (fn []
           (let [app-state     {:modules {:parent {:modules :child}}}
                 event-context {:absolute-state-path [:modules :parent]
                                :state-path          [:modules :parent]}]
             (handle-event
               (create-event {:name :test-event
                              :data :event-data})
               (fn [event {:keys [state]}]
                 (yt/is= state {:modules :child})
                 (yt/is (triggered-by-self? event))
                 (yt/is= (:name event) :test-event)
                 (yt/is= (:data event) :event-data)
                 (create-action {:fn-and-args [identity]}))
               {:props               {:state {:modules :child}}
                :state-path          []
                :absolute-state-path []})
             (yt/is= (handle-event
                       (create-event {:name    :test-event
                                      :data    :event-data
                                      :actions [(create-action {:fn-and-args [identity :child-action] :state-path [:modules :parent :modules :child]})]})
                       (fn [_ _] (create-action {:fn-and-args [identity :parent-action]}))
                       {:props               {:state {:modules :child}}
                        :state-path          [:modules :parent]
                        :absolute-state-path [:modules :parent]})
                     {:state-path [:modules :parent]
                      :actions    [(create-action {:fn-and-args [identity :child-action] :state-path [:modules :parent :modules :child]})
                                   (create-action {:fn-and-args [identity :parent-action] :state-path [:modules :parent] :source-event {:name       :test-event
                                                                                                                                        :data       :event-data
                                                                                                                                        :state-path []}})]})
             (yt/is= (handle-event
                       (create-event {:name       ::test-event
                                      :state-path [:child]})
                       (fn [_ _])
                       {:props               {:state {}}
                        :state-path          [:modules :parent]
                        :absolute-state-path [:modules :parent]})
                     {:name       ::test-event
                      :state-path [:modules :parent :child]
                      :actions    []})
             (yt/is= (handle-event
                       (create-event {:name       :test-event
                                      :state-path []})
                       (fn [_ _] [cons 1])
                       {:props {:state {}} :state-path [] :absolute-state-path []})
                     {:state-path []
                      :actions    [(create-action {:fn-and-args  [cons 1]
                                                   :source-event {:name :test-event :state-path []}
                                                   :state-path   []})]})))}
  [event module-handle-event inflated-module]
  (let [event-handler-result          (when (fn? module-handle-event) (module-handle-event event (:props inflated-module)))
        bubble-event                  (or (:bubble-event event-handler-result)
                                          (when (bubbles-by-default? event) (dissoc event :actions)))
        new-actions                   (extract-actions event-handler-result (:absolute-state-path inflated-module))
        new-actions-with-source-event (->> new-actions
                                           (map (fn [action]
                                                  (assoc action :source-event (create-source-event event)))))]
    (create-event
      (-> bubble-event
          (assoc :actions
                 (or (:actions bubble-event) (concat (:actions event)
                                                     new-actions-with-source-event)))
          (prepend-state-path (:state-path inflated-module))))))

(defmulti get-module-exports
          ;(str "Motivation: Using a defmulti rather than pulling this straight from the module's namespace allows"
          ;     "modules to be specified by data in the form of a namespaced keyword."
          ;     "It may also enable looking up modules by other keys such as state path.")
          (fn [exports-key] exports-key))

(s/def ::props (s/keys :req-un [::state]))
(s/def ::get-child-props (s/fspec :args (s/cat :props ::props :child-state-path ::child-state-path)
                                  :ret ::props))
(s/def ::handle-event-result (s/or ::actions
                                   (s/keys :req-un [::bubble-event]
                                           :opt-un [::actions])))
(s/def ::handle-event (s/fspec :args (s/cat :event ::event :props ::props)
                               :ret ::handle-event-result))
(s/def ::get-services (s/fspec :args (s/cat :props ::props)
                               :ret (s/nilable coll?)))
(s/def ::module-exports (s/keys :opt-un [::get-child-props ::handle-event ::get-services]))
(s/fdef get-module-exports :ret ::module-exports)

(defmethod get-module-exports :default
  [exports-key]
  (do (dt/warn (str "Could not find any exports for key " exports-key))
      nil))

(defmethod get-module-exports ::test-module-key
  [_]
  {:get-child-props (fn [props child-state-path] {:state {} :module-context {} :other-prop {}})
   :handle-event    (fn [event props] [conj 1])
   :get-services    (fn [props] [])})

(defn inflate-module
  {:test (fn [] (yt/is= (inflate-module {:exports-key ::test-module-1-key
                                         :state-path  [:module-2]}
                                        {:inflated-parent {:exports-key         ::test-module-key
                                                           :props               {}
                                                           :state-path          [:module-1]
                                                           :absolute-state-path [:module-1]}})
                        {:exports-key         ::test-module-1-key
                         :props               {:state {} :module-context {} :other-prop {}}
                         :state-path          [:module-2]
                         :absolute-state-path [:module-1 :module-2]}))}
  [deflated-module {:keys [inflated-parent root-props]}]
  (assoc deflated-module
    :props (if inflated-parent (let [parent-exports  (get-module-exports (:exports-key inflated-parent))
                                     get-child-props (:get-child-props parent-exports)]
                                 (when (fn? get-child-props)
                                   (get-child-props (:props inflated-parent)
                                                    (:state-path deflated-module))))
                               root-props)
    :absolute-state-path (if inflated-parent (concat (:absolute-state-path inflated-parent)
                                                     (:state-path deflated-module))
                                             (:state-path deflated-module))))

(def mock-module-branch
  [{:exports-key ::test-module-key
    :state-path  []}
   {:exports-key ::test-module-2-key
    :state-path  [:module-1]}
   {:exports-key ::test-module-3-key
    :state-path  [:module-2]}])

(defn inflate-module-branch
  {:test (fn [] (yt/is= (inflate-module-branch mock-module-branch {})
                        [{:exports-key         ::test-module-key
                          :props               {}
                          :state-path          []
                          :absolute-state-path []}
                         {:exports-key         ::test-module-2-key
                          :props               {:state {} :module-context {} :other-prop {}}
                          :state-path          [:module-1]
                          :absolute-state-path [:module-1]}
                         {:exports-key         ::test-module-3-key
                          :props               nil
                          :state-path          [:module-2]
                          :absolute-state-path [:module-1 :module-2]}]))}
  [module-branch root-props]
  (->> module-branch
       (reduce (fn [inflated-module-branch deflated-module]
                 (conj inflated-module-branch
                       (let [inflated-parent (last inflated-module-branch)]
                         (inflate-module deflated-module (if inflated-parent
                                                           {:inflated-parent inflated-parent}
                                                           {:root-props root-props})))))

               [])))

(defn get-handle-event [{:keys [exports-key]}]
  (:handle-event (get-module-exports exports-key)))

(defn get-actions
  {:spec (s/fdef get-actions
                 :args (s/cat :event ::event
                              :kwargs (s/keys :req-un [::root-props
                                                       ::event-origin-module-branch]
                                              :opt-un [::log-options]))
                 :ret ::actions)
   :test (fn []
           (let [test-event (create-event {:name       :test-event
                                           :state-path []})]
             (yt/is= (get-actions test-event
                                  {:root-props                 {}
                                   :event-origin-module-branch [(first mock-module-branch)]})
                     [(create-action {:fn-and-args  [conj 1]
                                      :source-event (create-source-event test-event)
                                      :state-path   []})])
             (yt/is= (get-actions test-event
                                  {:root-props                 {}
                                   :event-origin-module-branch mock-module-branch})
                     [(create-action {:fn-and-args  [conj 1]
                                      :source-event {:state-path [:module-1]}
                                      :state-path   []})])))}
  [event {:keys [root-props
                 event-origin-module-branch
                 log-options]}]
  (let [inflated-module-branch (inflate-module-branch event-origin-module-branch root-props)]
    (dt/log-if log-options
               "Getting actions..."
               (->> inflated-module-branch
                    (reverse)
                    (reduce
                      (fn [reduced-event inflated-module]
                        (dt/log-if log-options "Module" (select-keys inflated-module [:state-path :exports-key]) "Event" reduced-event
                                   "Result"
                                   (let [module-handle-event (get-handle-event inflated-module)]
                                     (handle-event reduced-event module-handle-event inflated-module))))
                      event)
                    :actions))))

(when env/debug (stest/instrument))
