(ns remodular.engine
  (:require [ysera.test :refer [is=]]
            [cljs.spec.alpha :as s]
            [remodular.event :as event]))

(defn perform-action
  {:test (fn []
           (is= (-> {:a {:b "pre"}}
                    (perform-action (event/create-action {:fn-and-args [assoc :b "post"]
                                                          :state-path  [:a]})))
                {:a {:b "post"}})
           (is= (-> {:b "pre"}
                    (perform-action (event/create-action {:fn-and-args [assoc :b "post"]
                                                          :state-path  []})))
                {:b "post"})
           (is= (-> {:c {:a {:b "pre"}}}
                    (perform-action (event/create-action {:fn-and-args [assoc :b "post"]
                                                          :state-path  [:c :a]})))
                {:c {:a {:b "post"}}}))}
  [app-state {fn-and-args :event/fn-and-args
              state-path  :event/state-path
              :or         {state-path []}
              :as         _action}]
  (let [[change-fn & args] fn-and-args
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
