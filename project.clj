(defproject imesc "0.1.0-SNAPSHOT"
  :description "Escalation system"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [org.clojure/core.specs.alpha "0.2.44"]
                 [integrant "0.7.0"]
                 [compojure "1.6.1"]
                 [org.apache.kafka/kafka-clients "2.1.0"]
                 [spootnik/kinsky "0.1.23"]
                 [com.novemberain/monger "3.5.0"]
                 [clojurewerkz/quartzite "2.1.0"]
                 [orchestra "2019.02.06-1"]
                 [environ "1.1.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]]
  :aliases {"test" ["run" "-m" "circleci.test/dir" :project/test-paths]
            "tests" ["run" "-m" "circleci.test"]
            "retest" ["run" "-m" "circleci.test.retest"]}
  :main ^:skip-aot imesc.core
  :plugins [[lein-environ "1.1.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [circleci/circleci.test "0.4.1"]]}})
