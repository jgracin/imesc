(ns imesc.activator.kafka
  (:require [kinsky.client :as client]
            [integrant.core :as integrant])
  (:import [org.apache.kafka.clients.admin AdminClient KafkaAdminClient NewTopic]
           [java.time Duration]))

(defmethod integrant/init-key :kafka/producer [_ producer-opts]
  (client/producer producer-opts
                   (client/string-serializer)
                   (client/edn-serializer)))

(defmethod integrant/halt-key! :kafka/producer [_ producer]
  (client/close! producer))

(defn send! [configuration topic k v]
  {:pre [(contains? configuration :kafka/producer)
         (string? topic)]}
  (client/send! (:kafka/producer configuration) topic k v))
