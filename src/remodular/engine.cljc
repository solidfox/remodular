(ns remodular.engine
  "Utility functions for setting up an engine using the remodular architecture."
  (:require [ysera.test :refer [is=]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [remodular.core :as core]
            [remodular.devtools :as dt]
            [remodular.environment :as env]
            [ysera.test :as yt]))

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
  [app-state {fn-and-args :fn-and-args
              state-path  :state-path
              :or         {state-path []}
              :as         _action}]
  (let [[change-fn & args] fn-and-args]
    (if (empty? state-path)
      (apply change-fn
             app-state
             args)
      (apply update-in
             app-state
             state-path
             change-fn
             args))))

(defn reduce-actions
  [state actions]
  (if (empty? actions)
    ;TODO abort swap when there are no actions?
    state
    (reduce perform-action
            state
            actions)))

(defn reduce-event
  {:spec (s/fdef reduce-event :args (s/cat :event ::core/event
                                           :root-props (s/keys :req-un [::core/state])
                                           :event-origin-module-branch ::core/event-origin-module-branch))
   :test (fn [] (yt/is= (reduce-event {:name :test-event}
                                      {:state [2]}
                                      core/mock-module-branch)
                        [2 1]))}
  [event root-props event-origin-module-branch & {:keys [log-options]}]
  (let [actions (core/get-actions event {:root-props                 root-props
                                         :event-origin-module-branch event-origin-module-branch
                                         :log-options                log-options})
        state   (:state root-props)]
    (reduce-actions state actions)))

(defmulti perform-services
          (fn [{mode :mode
                :as  _state} _services _reduce-event]
            mode))

(when env/debug (stest/instrument))
