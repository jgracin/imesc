(ns imesc.initiator.kafka
  (:require [kinsky.client :as client]
            [clojure.edn :as edn]
            [integrant.core :as integrant]
            [imesc.config :as config]
            [clojure.tools.logging :as logger])
  (:import [org.apache.kafka.clients.admin AdminClient KafkaAdminClient NewTopic]
           [java.time Duration]))

(defn poll-request [consumer]
  (-> (client/poll! consumer (Duration/ofMillis 1000))
      :by-topic
      vals
      first))

(defn kafka-request-polling-fn [consumer]
  (fn []
    (->> (poll-request consumer)
         (map (comp edn/read-string :value)))))

(defmethod integrant/init-key :imesc.initiator.kafka/request-consumer [_ {:keys [topic consumer-opts]}]
  (let [consumer (client/consumer consumer-opts
                                  (client/string-deserializer)
                                  (client/string-deserializer))]
    (client/subscribe! consumer topic)
    consumer))

(defmethod integrant/halt-key! :imesc.initiator.kafka/request-consumer [_ consumer]
  (client/close! consumer))


