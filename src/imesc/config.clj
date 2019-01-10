(ns imesc.config
  (:require [integrant.core :as integrant]
            [environ.core :refer [env]]))

(defonce system (atom {}))

(def request-topic (or (env "REQUEST_TOPIC") "imesc.request"))

(def config
  {:kafka/request-consumer {:topic request-topic
                            :consumer-opts {:bootstrap.servers "localhost:9092"
                                            :group.id "imesc-request-processor"
                                            :enable.auto.commit true
                                            :max.poll.records 1}}
   :alarm/repository {:host "localhost" :port 27017}})
