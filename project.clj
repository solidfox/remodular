(defproject remodular "0.1.2-SNAPSHOT"
  :description "FIXME: write description"
  :url ""
  :license {}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]
                 [rum "0.11.2"]
                 [ysera "1.2.0"]]

  :plugins [[lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"])
