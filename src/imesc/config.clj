(ns imesc.config
  (:require [integrant.core :as integrant]
            [environ.core :refer [env]]))

(defonce system (atom {}))

(def request-topic (or (env "REQUEST_TOPIC") "imesc.requests"))

(def bootstrap-servers (or (env "KAFKA_BOOTSTRAP_SERVERS") "localhost:9092"))

(def config
  {:kafka/request-consumer {:topic request-topic
                            :consumer-opts {:bootstrap.servers bootstrap-servers
                                            :group.id "imesc-request-processor"
                                            :enable.auto.commit true
                                            :auto.commit.interval.ms 1000
                                            :max.poll.records 1
                                            :max.poll.interval.ms 10000}}
   :alarm/repository {:host "localhost" :port 27017}
   :kafka/producer {:bootstrap.servers bootstrap-servers
                    :client.id "imesc.activator"
                    :batch.size 0
                    :acks "all"
                    :request.timeout.ms 10000}})
