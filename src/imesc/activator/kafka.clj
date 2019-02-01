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

