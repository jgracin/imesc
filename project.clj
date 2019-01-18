(defproject imesc "0.1.0-SNAPSHOT"
  :description "Escalation system"
  :url "http://example.com/FIXME"
  :license {:name "Apache License, Version 2.0"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/spec.alpha "0.2.176"]
                 [org.clojure/core.specs.alpha "0.2.44"]
                 [org.clojure/core.async "0.4.490"]
                 [manifold "0.1.8"]
                 [integrant "0.7.0"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-servlet "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-json "0.4.0"]
                 [compojure "1.6.1"]
                 [org.apache.kafka/kafka-clients "2.1.0"]
                 [spootnik/kinsky "0.1.23"]
                 [com.novemberain/monger "3.5.0"]
                 [clojurewerkz/quartzite "2.1.0"]
                 [environ "1.1.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [ch.qos.logback/logback-classic "1.2.3"]]
  :main ^:skip-aot imesc.core
  :plugins [[lein-environ "1.1.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [ring "1.7.1"]]
                   :env {:case-repository-type "in-memory"}}
             :test {:env {:case-repository-type "in-memory"}
                    :dependencies [[org.clojure/test.check "0.9.0"]
                                   [ring "1.7.1"]]}})
