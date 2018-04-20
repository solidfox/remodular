(ns remodular.runtime
  (:require [rum.core :as rum]
            [util.services-mock.core :refer [mock-url-request!]]
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
              :as        action}]
  (let [[change-fn & args] fn-call]
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

(defn perform-services [services handle-event & [should-log]]
  (when (not-empty services)
    (when should-log
      (println "---- Performing Services ----")
      (js/console.log services))
    (doseq [{data       :data
             after      :after
             state-path :state-path} services]
      (mock-url-request! data
                         (fn [response]
                           (handle-event {:actions [{:fn-and-args (concat after [response])
                                                     :state-path  state-path}]}))))
    (handle-event {:actions (map (fn [service]
                                   {:fn-and-args (:before service)
                                    :state-path  (or (:state-path service) [])})
                                 services)})))

(defn run-modular-app!
  [{get-view                             :get-view
    get-services                         :get-services
    app-state-atom                       :app-state-atom
    input                                :input
    app-id                               :app-id
    tag-id-for-app                       :tag-id
    {should-log-state-updates :state-updates
     should-log-events        :events
     should-log-services      :services} :logging}]
  (let [app-id           (or app-id "app")
        tag-id-for-app   (or tag-id-for-app "app")
        handle-event     (fn [event]
                           (when should-log-events
                             (println "---- Handling Event ----")
                             (js/console.log event))
                           (reduce-actions! event app-state-atom))
        render-app-state (fn [app-state]
                           (when should-log-state-updates
                             (println "---- Rendering state ----")
                             (js/console.log app-state))
                           (rum/mount (get-view {:input                (assoc input :state app-state)
                                                 :trigger-parent-event handle-event
                                                 :state-path           []})
                                      (js/document.getElementById tag-id-for-app))
                           (when get-services
                             (-> (get-services {:input (assoc input :state app-state)})
                                 (perform-services handle-event should-log-services))))]
    (add-watch app-state-atom app-id
               (fn [_ _ old-state new-state]
                 (render-app-state new-state)))

    (render-app-state (deref app-state-atom))))
