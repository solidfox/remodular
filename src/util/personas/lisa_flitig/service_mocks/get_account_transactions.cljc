(ns util.personas.lisa-flitig.service-mocks.get-account-transactions)

(def get-account-transactions-responses
  [{:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210367XX",
     :currentPageNumber 0,
     :searchCriterion   {:fromDate   "",
                         :toDate     "",
                         :fromAmount "",
                         :toAmount   ""}},
    :response
    {:response
     {:transactions
      {:moreExist true,
       :preliminaryTransactions
                  [{:currencyDate    "2099-01-25",
                    :transactionText "Lön, Bolaget AB",
                    :amount          23560}
                   {:currencyDate    "2099-01-24",
                    :transactionText "Lön, Företaget AB",
                    :amount          11560}],
       :historicalTransactions
                  [{:amount             250,
                    :accountBalance     10570.1,
                    :transactionId      "1336",
                    :countersignedBy    "Bosse Bolag",
                    :bookKeepingDate    "2017-07-18",
                    :comment            "Squash",
                    :transactionType    "normal",
                    :transactionDate    "2017-07-19",
                    :isSuppliersPayment false,
                    :transactionText    "Friskvårdsbidrag",
                    :signedBy           "Lisa Flitig"}
                   {:amount             -35,
                    :accountBalance     10320.1,
                    :transactionId      "1339",
                    :bookKeepingDate    "2017-07-17",
                    :batchNumber        123,
                    :isDepartingPayment true,
                    :comment            "BNP Paribas",
                    :transactionNumber  123456,
                    :transactionType    "foreignPayment",
                    :transactionDate    "2017-07-17",
                    :isSuppliersPayment false,
                    :transactionText    "Utlandsbetalning"}
                   {:amount             -253,
                    :accountBalance     10317.1,
                    :formattedCardNumber
                                        "**** **** ****7808",
                    :exchangeRate       "1",
                    :transactionId      "1337",
                    :originalCurrency   "SEK",
                    :bookKeepingDate    "2017-07-18",
                    :comment            "Krusty Krab",
                    :transactionType    "mcCard",
                    :originalAmount     -253,
                    :transactionDate    "2017-07-20",
                    :isSuppliersPayment false,
                    :transactionText    "Kortbetalning"}
                   {:transactionDate    "2017-07-17",
                    :bookKeepingDate    "2017-07-13",
                    :transactionType    "domesticPayment",
                    :transactionText    "Betalning",
                    :amount             -495,
                    :transactionId      "1340",
                    :accountBalance     10355.1,
                    :isSuppliersPayment false}
                   {:amount             1250,
                    :accountBalance     11250,
                    :transactionId      "1338",
                    :bookKeepingDate    "2017-07-11",
                    :comment            "Skatteåterbäring",
                    :transactionType    "undefined",
                    :transactionDate    "2017-07-14",
                    :isSuppliersPayment false,
                    :transactionText    "Skatteverket"}
                   {:amount             -299.9,
                    :accountBalance     10850.1,
                    :transactionId      "1417",
                    :bookKeepingDate    "2017-07-13",
                    :comment            "Racketspecialisten",
                    :transactionType    "card",
                    :transactionDate    "2017-07-16",
                    :isSuppliersPayment false,
                    :transactionText    "Betalning Internet"}
                   {:amount             -100,
                    :accountBalance     11150,
                    :transactionId      "1420",
                    :bookKeepingDate    "2017-07-12",
                    :comment            "Tommy Tomhänt",
                    :transactionType    "swish",
                    :transactionDate    "2017-07-15",
                    :isSuppliersPayment false,
                    :transactionText    "Swishbetalning"}
                   {:amount              4513,
                    :accountBalance      4808.42,
                    :formattedCardNumber "",
                    :exchangeRate        "0.0",
                    :bookKeepingDate     "2017-07-12",
                    :batchNumber         "204",
                    :isDepartingPayment  false,
                    :comment             "0053823266",
                    :transactionNumber   "527",
                    :transactionType     "elin",
                    :transactionDate     "2017-07-12",
                    :bankgiroNumber      "53823266",
                    :isSuppliersPayment  true,
                    :transactionText     "GI 00000"}]}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210367XX",
     :currentPageNumber 1,
     :searchCriterion
                        {:fromDate   "",
                         :toDate     "",
                         :fromAmount "",
                         :toAmount   ""}},
    :response
    {:response
     {:transactions
      {:moreExist false,
       :historicalTransactions
                  [{:amount             50,
                    :accountBalance     10000,
                    :transactionId      "1421",
                    :bookKeepingDate    "2017-07-11",
                    :comment            "McDonalds",
                    :transactionType    "swish",
                    :transactionDate    "2017-07-13",
                    :isSuppliersPayment false,
                    :transactionText    "Swishbetalning"}
                   {:amount             -5400,
                    :accountBalance     9950,
                    :formattedCardNumber
                                        "**** **** ****7808",
                    :exchangeRate       "1",
                    :transactionId      "1422",
                    :originalCurrency   "SEK",
                    :bookKeepingDate    "2017-07-06",
                    :comment            "Dennis Brunby Hifi",
                    :transactionType    "mcCard",
                    :originalAmount     -5400,
                    :transactionDate    "2017-07-04",
                    :isSuppliersPayment false,
                    :transactionText    "Kortbetalning"}]}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210367XX",
     :currentPageNumber 0,
     :searchCriterion   ".*"},
    :response
    {:response
     {:transactions
      {:moreExist true,
       :historicalTransactions
                  [{:amount             -35,
                    :accountBalance     10320.1,
                    :transactionId      "1339",
                    :bookKeepingDate    "2017-07-17",
                    :batchNumber        123,
                    :isDepartingPayment true,
                    :comment            "BNP Paribas",
                    :transactionNumber  123456,
                    :transactionType    "foreignPayment",
                    :transactionDate    "2017-07-17",
                    :isSuppliersPayment false,
                    :transactionText    "Utlandsbetalning"}
                   {:transactionDate    "2017-07-17",
                    :bookKeepingDate    "2017-07-13",
                    :transactionType    "domesticPayment",
                    :transactionText    "Betalning",
                    :amount             -495,
                    :transactionId      "1340",
                    :accountBalance     10355.1,
                    :isSuppliersPayment false}]}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210367XX",
     :currentPageNumber ".*",
     :searchCriterion   ".*"},
    :response
    {:response
     {:transactions
      {:moreExist false,
       :historicalTransactions
                  [{:amount             -5400,
                    :accountBalance     9950,
                    :formattedCardNumber
                                        "**** **** ****7808",
                    :exchangeRate       "1",
                    :transactionId      "1422",
                    :originalCurrency   "SEK",
                    :bookKeepingDate    "2017-07-06",
                    :comment            "Dennis Brunby Hifi",
                    :transactionType    "mcCard",
                    :originalAmount     -5400,
                    :transactionDate    "2017-07-04",
                    :isSuppliersPayment false,
                    :transactionText    "Kortbetalning"}]}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210377XX",
     :currentPageNumber ".*",
     :searchCriterion   ".*"},
    :response
    {:response
     {:transactions {:moreExist false}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902210338XX",
     :currentPageNumber ".*",
     :searchCriterion   ".*"},
    :response
    {:response
     {:transactions {:moreExist false}}}}
   {:url
    "serviceEndpoints.getAccountTransactions",
    :request
    {:accountNumber     "902110149XX",
     :currentPageNumber ".*",
     :searchCriterion   ".*"},
    :response
    {:response
     {:transactions {:moreExist false}}}}])
