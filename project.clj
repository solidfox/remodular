(defproject remodular "0.3.1"
  :description "FIXME: write description"
  :deploy-repositories [[:clojars {:url "[https://clojars.org/solidfox/](https://clojars.org/solidfox/)"
                                   :sign-releases false}]]
  :url ""
  :license {}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/test.check "0.10.0-alpha4"]
                 [ysera "1.3.0"]]

  :plugins [[lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"])
