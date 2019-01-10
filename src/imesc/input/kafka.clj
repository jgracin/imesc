(ns imesc.input.kafka
  (:require [kinsky.client :as client]
            [integrant.core :as integrant])
  (:import [org.apache.kafka.clients.admin AdminClient KafkaAdminClient NewTopic]
           [java.time Duration]))

(defn poll-request [consumer]
  (-> (client/poll! consumer (Duration/ofMillis 1000))
      :by-topic
      vals
      first))

(defmethod integrant/init-key :kafka/request-consumer [_ {:keys [topic consumer-opts]}]
  (let [consumer (client/consumer consumer-opts
                                  (client/string-deserializer)
                                  (client/json-deserializer))]
    (client/subscribe! consumer topic)
    consumer))

(defmethod integrant/halt-key! :kafka/request-consumer [_ consumer]
  (client/close! consumer))

(comment
  (def ac (AdminClient/create {"bootstrap.servers" "localhost:9092"}))
  (-> ac (.createTopics #{(NewTopic. "imesc.request" 1 1)}))
  (-> ac (.deleteTopics #{"imesc.request"}))
  (-> ac .listTopics .names deref)
  (-> ac .listConsumerGroups .all deref)
  (-> ac (.describeTopics #{"imesc.request"}) .all deref)
  (-> ac .describeCluster .controller deref)
  (-> ac .describeCluster .nodes deref)
  (-> ac .close)
  (consumer-group-offsets ac "orderProcessor")
  (-> ac (.listConsumerGroupOffsets "orderProcessor"))

  (def producer
    (client/producer {:bootstrap.servers "localhost:9092"
                      :batch.size 0
                      :acks "all"
                      :max.block.ms 5000
                      :request.timeout.ms 5000}
                     (client/string-serializer)
                     (client/string-serializer)))

  (client/send! producer "imesc.request" "r2" "{\"action\": \"start\"}")
  )
