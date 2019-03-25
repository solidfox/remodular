(ns remodular.rum
  (:require [ysera.test :refer [is=]]
            [cljs.spec.alpha :as s]
            [remodular.engine :as engine]
            [rum.core :as rum]))

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
                           (engine/reduce-actions! event app-state-atom))
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
                 (if (engine/needs-render old-state new-state)
                   (render-app-state (::render-input new-state)))))
    (render-app-state (::render-input (deref app-state-atom)))))
