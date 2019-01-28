(ns imesc.config
  (:require [integrant.core :as integrant]
            [environ.core :refer [env]]))

(defonce system (atom {}))

(def request-topic (or (env "REQUEST_TOPIC") "imesc.requests"))

(def config
  {:kafka/request-consumer
   {:topic request-topic
    :consumer-opts {:bootstrap.servers "localhost:9092"
                    :group.id "imesc-request-processor"
                    :enable.auto.commit true
                    :auto.commit.interval.ms 1000
                    :max.poll.records 1
                    :max.poll.interval.ms 10000}}
   :alarm/repository {:host "localhost" :port 27017}})
