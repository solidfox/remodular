(ns util.services-mock.util
  (:require [util.personas.lisa-flitig.service-mocks.get-account-transactions
             :refer [get-account-transactions-responses]]
            [ysera.test :refer [is is-not is=]]))

(defn deep-match
  {:test (fn []
           (is
             (deep-match "test" ".*"))
           (is-not
             (deep-match ".*" "test"))
           (is-not
             (deep-match "test" "test-not"))
           (is
             (deep-match {:foo {:bar "test"}} {:foo {:bar "test"}}))
           (is-not
             (deep-match {:foo {:bar "test"}} {:foo   {:bar "test"}
                                               :extra :key}))
           (is
             (deep-match {:foo {:bar   {:arbitrary "content"}
                                :alice "test"
                                :bob   "mint"}}
                         {:foo {:bar   ".*"
                                :alice "test"
                                :bob   "mint"}}))
           (is-not
             (deep-match {:foo "test"}
                         {:foo "test-not"})))}

  [value mock-template]
  (if (map? mock-template)
    (and (= (count (keys value)) (count (keys mock-template)))
         (->> (keys mock-template)
              (filter
                (fn [key]
                  (not (deep-match (key value) (key mock-template)))))
              (empty?)))
    (or (= ".*" mock-template)
        (= value mock-template))))

(defn requests-match [request mock-request]
  (deep-match request mock-request))

(defn urls-match [url mock-url]
  (= url mock-url))

(defn get-mock-response
  {:test (fn []
           (is= (get-mock-response {:url     "serviceEndpoints.getAccountTransactions"
                                    :request {:accountNumber     "902210377XX"
                                              :currentPageNumber 0
                                              :searchCriterion   {:fromDate   "",
                                                                  :toDate     "",
                                                                  :fromAmount "",
                                                                  :toAmount   ""}}})
                {:response {:transactions {:moreExist false}}}))}
  [url-request]
  (as-> get-account-transactions-responses $
        (filter (fn [mock]
                  (and (urls-match (:url url-request) (:url mock))
                       (requests-match (:request url-request) (:request mock))))
                $)
        (first $)
        (:response $)))
