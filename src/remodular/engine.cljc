(ns remodular.engine
  "Utility functions for setting up an engine using the remodular architecture."
  (:require [ysera.test :refer [is=]]
            [cljs.spec.alpha :as s]
            [remodular.core :as core]))

(defn perform-action
  {:spec (s/fdef perform-action
                 :args (s/cat :app-state ::core/state
                              :action ::core/action)
                 :ret ::core/state)
   :test (fn []
           (is= (-> {:a {:b "pre"}}
                    (perform-action (core/create-action {:fn-and-args [assoc :b "post"]
                                                         :state-path  [:a]})))
                {:a {:b "post"}})
           (is= (-> {:b "pre"}
                    (perform-action (core/create-action {:fn-and-args [assoc :b "post"]
                                                         :state-path  []})))
                {:b "post"})
           (is= (-> {:c {:a {:b "pre"}}}
                    (perform-action (core/create-action {:fn-and-args [assoc :b "post"]
                                                         :state-path  [:c :a]})))
                {:c {:a {:b "post"}}}))}
  [app-state {fn-and-args ::core/fn-and-args
              state-path  ::core/state-path
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

(defn reduce-actions
  [state actions]
  (when (not-empty actions)
    (reduce perform-action
            state
            actions)))

(defn reduce-event!
  {:spec (s/fdef reduce-event! :args (s/cat :state-atom any?
                                            :event ::core/event
                                            :event-handler-chain ::core/event-handler-chain))}
  [state-atom event event-handler-chain]
  (swap! state-atom
        (fn [state]
          (let [actions (core/get-actions event state event-handler-chain)]
            (reduce-actions state actions)))))

(defmulti perform-services
          (fn [{mode :mode
                :as  _state} _services _reduce-event]
            mode))

(defn needs-render [old-state new-state]
  (not (identical? (::render-input old-state) (::render-input new-state))))
