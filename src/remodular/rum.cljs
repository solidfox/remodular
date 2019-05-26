(ns remodular.rum
  (:require [ysera.test :refer [is=]]
            [cljs.spec.alpha :as s]
            [remodular.engine :as engine]
            [rum.core :as rum]))

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

(defn console-warn [& args]
  #?(:clj (do (println "WARNING")
              (apply println args))
     :cljs (apply js/console.warn args)))

(defn needs-render [old-state new-state]
  (not (identical? old-state new-state)))

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
                                     (console-warn (str this-fns-name ": The trigger-event keyword was already in use. When providing a trigger-event callback to modular component, use trigger-parent-event instead.\n"
                                                        rum-state)))
                                   ;TODO change where trigger-event gets injected.
                                   (when (not (-> rum-state :rum/args first :state-path))
                                     (console-warn (str this-fns-name ": No state-path was provided in the argument map.\n"
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


(defn run-modular-app!
  [{get-view                             :get-view
    get-services                         :get-services
    app-state-atom                       :app-state-atom
    root-input                           :root-input
    app-id                               :app-id
    app-element-tag-id                   :app-element-tag-id
    {should-log-state-updates :state-updates
     should-log-events        :events
     should-log-services      :services} :logging}]
  (let [app-id           (or app-id "app")
        tag-id-for-app   (or app-element-tag-id "app")
        reduce-event     (fn [event]
                           (when should-log-events
                             (println "---- Reducing Event ----")
                             (js/console.log event))
                           (engine/reduce-actions! app-state-atom (:actions event)))
        render-app-state (fn [app-state]
                           (when should-log-state-updates
                             (println "---- Rendering state ----")
                             (js/console.log app-state))
                           (rum/mount (get-view {:input                (assoc root-input :state app-state)
                                                 :trigger-parent-event reduce-event
                                                 :state-path           []})
                                      (js/document.getElementById tag-id-for-app))
                           (when-let [services (and get-services
                                                    (get-services {:input (assoc root-input :state app-state)}))]
                             (when (not-empty services)
                               (when should-log-services
                                 (println "---- Performing Services ----")
                                 (js/console.log services))
                               (engine/perform-services app-state services reduce-event))))]
    (add-watch app-state-atom app-id
               (fn [_ _ old-state new-state]
                 (if (needs-render old-state new-state)
                   (render-app-state (::render-input new-state)))))
    (render-app-state (::render-input (deref app-state-atom)))))
