(defproject remodular "0.2.0-SNAPSHOT"
  :description "FIXME: write description"
  :url ""
  :license {}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/test.check "0.10.0-alpha4"]
                 [rum "0.11.2"]
                 [ysera "1.2.0"]]

  :plugins [[lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"])
