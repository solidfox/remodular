(ns util.services-mock.core
  (:require [util.services-mock.util :as util]))

(def state-atom (atom {:calls 0}))

(defn mock-url-request!
  [url-request callback]
  (js/setTimeout (fn []
                   (let [response (util/get-mock-response url-request)]
                     (callback {:response response
                                :status   200
                                :request  url-request})))
                 (if (= 0 (mod (:calls (deref state-atom)) 2))
                   2000
                   1000))

  (swap! state-atom update :calls inc))

