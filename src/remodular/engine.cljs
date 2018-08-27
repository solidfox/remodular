(ns remodular.engine
  (:require [rum.core :as rum]
            [cljs.pprint :refer [pprint]]
            [ysera.test :refer [is=]]
            [cljs.core :refer-macros [assert]]))

(defn perform-action
  {:test (fn []
           (is= (-> {:a {:b "pre"}}
                    (perform-action {:fn-and-args [assoc :b "post"]
                                     :state-path  [:a]}))
                {:a {:b "post"}})
           (is= (-> {:b "pre"}
                    (perform-action {:fn-and-args [assoc :b "post"]
                                     :state-path  []}))
                {:b "post"})
           (is= (-> {:c {:a {:b "pre"}}}
                    (perform-action {:fn-and-args [assoc :b "post"]
                                     :state-path  [:c :a]}))
                {:c {:a {:b "post"}}}))}
  [app-state {fn-call    :fn-and-args
              state-path :state-path
              :or        {state-path []}
              :as        _action}]
  (let [[change-fn & args] fn-call
        state-path (concat [::render-input] state-path)]
    (if (> (count state-path) 0)
      (apply update-in
             app-state
             state-path
             change-fn
             args)
      (apply change-fn
             app-state
             args))))

(defn reduce-actions!
  [{actions :actions}
   app-state-atom]
  (when (not-empty actions)
    (swap! app-state-atom
           (fn [app-state]
             (reduce perform-action
                     app-state
                     actions)))))

(defmulti perform-services
          (fn [{mode :mode
                :as  _state} _services _reduce-event]
            mode))

(defn needs-render [old-state new-state]
  (not (identical? (::render-input old-state) (::render-input new-state))))

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
                           (reduce-actions! event app-state-atom))
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
                               (perform-services app-state services reduce-event))))]
    (add-watch app-state-atom app-id
               (fn [_ _ old-state new-state]
                 (if (needs-render old-state new-state)
                   (render-app-state (::render-input new-state)))))
    (render-app-state (::render-input (deref app-state-atom)))))
