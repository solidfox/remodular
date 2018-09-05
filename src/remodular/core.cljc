(ns remodular.core
  (:require [clojure.pprint :refer [pprint]]
            [ysera.test :refer [is is= is-not]]
            [ysera.error :refer [error]]
            [#?(:clj  clojure.spec.alpha
                :cljs cljs.spec.alpha) :as s]
            [remodular.spec]))

(defn triggered-by-me?
  {:test (fn []
           (is (triggered-by-me? {}))
           (is (triggered-by-me? {:state-path []}))
           (is-not (triggered-by-me? {:state-path [:child :path]})))}
  [event]
  (empty? (:state-path event)))

(defn no-event-handling [state event]
  event)

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

(defn create-action
  {:test (fn []
           (is= (create-action {:name        "test"
                                :fn-and-args []})
                {:name        "test"
                 :fn-and-args []
                 :state-path  []})
           (is= (create-action {:fn-and-args [:test]})
                {:fn-and-args [:test]
                 :state-path  []}))}
  [{name        :name
    fn-and-args :fn-and-args}]
  (merge (when-not (nil? name) {:name name})
         {:fn-and-args fn-and-args
          :state-path  []}))

(defn update-state [function & args]
  (create-action {:fn-and-args (concat [function] args)}))

(defn append-action
  {:test (fn []
           (is= (append-action {} {:fn-and-args [:dummy-function]})
                {:actions [{:fn-and-args [:dummy-function]}]})
           (let [event {:actions [{:fn-and-args [:dummy-function-1]}]}]
             (is= (append-action event {:fn-and-args [:dummy-function-2]})
                  {:actions [{:fn-and-args [:dummy-function-1]}
                             {:fn-and-args [:dummy-function-2]}]})))}
  [event action]
  (update event :actions concat [action]))

(defn create-event
  "Takes over ownership of an event by applying a view-module's own API naming to it."
  {:test (fn []
           (is= (create-event {:name          :old-name
                               :data       {}
                               :state-path [:child :path]
                               :actions    :should-remain}
                              {:new-name :new-name
                               :new-data {:new-data {}}})
                {:name    :new-name
                 :data    {:new-data {}}
                 :actions :should-remain})
           (is= (create-event {:name          :old-name
                               :data       {}
                               :state-path [:child :path]
                               :actions    :should-remain})
                {:name    :old-name
                 :data    {}
                 :actions :should-remain}))}
  ([child-event {new-name :new-name
                 new-data :new-data}]
   (merge (when-not (nil? new-name) {:name new-name})
          (when-not (nil? new-data) {:data new-data})
          {:actions (:actions child-event)}))
  ([{name :name
     data :data
     :as event}]
   (dissoc event :state-path)))

(defn create-anonymous-event
  ([event]
   {:actions (:actions event)})
  ([]
   {}))

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

(defn- create-trigger-event
  [rum-state handle-event]
  (let [args                 (first (:rum/args rum-state))
        state-path           (:state-path args)
        trigger-parent-event (:trigger-parent-event args)]
    (fn [event]
      (-> (handle-event (deref (::input-ref rum-state)) event)
          ((fn [handled-event]
             (assert (s/valid? :remodular.spec/event handled-event)
                     (str "The event " event " did not conform to spec after being handled:\n"
                          (s/explain-str :remodular.spec/event handled-event)))
             handled-event))
          (prepend-state-path-to-event state-path)
          (trigger-parent-event)))))

(defn modular-component
  "
  A Rum component mixin that enables a component to not be reevaluated when its input is equal.

  Usage:
  A component with this mixin expects a map of arguments as follows:
  {:input                 -- A map with the data that the component depends on.
   :trigger-parent-event  -- A function that takes the an event and asks the parent module to
                             handle it.}
  It then adds the key :trigger-event key with a function that first handles its passed events
  with the handle-event function passed to the mixin -- making sure that handle-event always
  receives the most recent state.

  Discussion:
  Normally the trigger-event function in the props leads to the component always having changed
  props and thus being unable to eliminate re-renders of identical inputs.
  This component excludes the trigger-event function from the equality check while enabling it
  to still work on the most recent state of any parent components.

  Example bugs solved:

  "
  ;TODO Unittest if re-render happens.
  ;TODO Unittest that most recent state is passed to this component and all ancestors when event is triggered by trigger-event and parent state has changed but not this components' state.
  ;TODO Unittest that most recent parent-trigger-event functions are used in the above case.
  ;{:test (fn []
  ;         (is= (-> {:rum/args (list {:input                {}
  ;                                    :state-ref            (atom :something)
  ;                                    :trigger-parent-event identity
  ;                                    :state-path           []})}
  ;                  (:before-render (modular-component identity)))
  ;              {:rum/args (list {:input                {}
  ;                                :state-ref            (atom nil)
  ;                                :trigger-event        :?  ;TODO
  ;                                :trigger-parent-event identity
  ;                                :state-path           []})}))}
  [& [handle-event]]
  (let [validate-preconditions (fn [rum-state]
                                 (let [this-fns-name (str (namespace ::this) "/" (-> #'modular-component meta :name))]
                                   (when (-> rum-state :rum/args first :trigger-event)
                                     (js/console.warn (str this-fns-name ": The trigger-event keyword was already in use. When providing a trigger-event callback to modular component, use trigger-parent-event instead.\n"
                                                           rum-state)))
                                   ;TODO change where trigger-event gets injected.
                                   (when (not (-> rum-state :rum/args first :state-path))
                                     (js/console.warn (str this-fns-name ": No state-path was provided in the argument map.\n"
                                                           rum-state)))))

        handle-event (or handle-event
                         (fn [_state event]
                           (create-anonymous-event event)))]
    {:init          (fn [rum-state _]
                      (assoc rum-state ::input-ref (atom nil)))
     :before-render (fn [rum-state]
                      (validate-preconditions rum-state)
                      (reset! (::input-ref rum-state) (:input (first (:rum/args rum-state))))
                      (update rum-state :rum/args (fn [[head & tail]]
                                                    (conj tail (assoc head :trigger-event
                                                                           (create-trigger-event rum-state handle-event))))))
     :should-update (fn [old-state state]
                      (let [our-old-input (-> (:rum/args old-state)
                                              (first)
                                              (:input))
                            our-new-input (-> (:rum/args state)
                                              (first)
                                              (:input))]
                        (not (= our-old-input our-new-input))))}))
