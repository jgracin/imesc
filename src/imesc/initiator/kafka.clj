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

(def kafka-request-polling-fn
  (fn []
    (->> (poll-request (:kafka/request-consumer @config/system))
         (map (comp edn/read-string :value)))))

(defmethod integrant/init-key :kafka/request-consumer [_ {:keys [topic consumer-opts]}]
  (let [consumer (client/consumer consumer-opts
                                  (client/string-deserializer)
                                  (client/string-deserializer))]
    (client/subscribe! consumer topic)
    consumer))

(defmethod integrant/halt-key! :kafka/request-consumer [_ consumer]
  (client/close! consumer))

(comment
  (def ac (AdminClient/create {"bootstrap.servers" "localhost:9092"}))
  (-> ac (.createTopics #{(NewTopic. "imesc.requests" 1 1)}))
  (-> ac (.deleteTopics #{"imesc.requests"}))
  (-> ac .listTopics .names deref)
  (-> ac .listConsumerGroups .all deref)
  (-> ac (.describeTopics #{"imesc.requests"}) .all deref)
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
                     (client/edn-serializer)))

  (def sample-start-request
    {:action :start
     :process-id "finpoint"
     :notifications [{:delay-in-seconds 10
                      :channel :console
                      :params {:message "First dummy notification to console."}}
                     {:delay-in-seconds 15
                      :channel :console
                      :params {:message "Second dummy notification to console."}}
                     {:delay-in-seconds 300
                      :channel :email
                      :params {:to ["orders@example.com"]
                               :subject "You have unconfirmed new orders in RoomOrders."
                               :body "Visit https://roomorders.com."}}
                     {:delay-in-seconds 600
                      :channel :phone
                      :params {:phone-number "38599000001"
                               :message "new-order-unconfirmed"}}]})
  (def sample-stop-request
    {:action :stop
     :process-id "finpoint"})
  (client/send! producer "imesc.requests" "key1" sample-start-request)
  (client/send! producer "imesc.requests" "key1" sample-stop-request)

  )
