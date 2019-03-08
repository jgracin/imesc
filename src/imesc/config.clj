(ns imesc.config
  (:require [integrant.core :as integrant]
            [environ.core :refer [env]]))

(defonce #^:private -system (atom {}))

(def request-topic (or (env "REQUEST_TOPIC") "imesc.requests"))

(def bootstrap-servers (or (env "KAFKA_BOOTSTRAP_SERVERS") "localhost:9092"))

(def repository-hostname (or (env "REPOSITORY_HOSTNAME") "localhost"))

(def repository-port (or (env "REPOSITORY_PORT") 27017))

(def email-requests-topic (or (env "EMAIL_REQUESTS_TOPIC" "imesc.email-requests")))

(def phone-requests-topic (or (env "PHONE_REQUESTS_TOPIC" "imesc.phone-requests")))

(def config
  {:imesc.core/exit-flag nil
   :imesc.initiator.kafka/request-consumer {:topic request-topic
                                            :consumer-opts {:bootstrap.servers bootstrap-servers
                                                            :group.id "imesc-request-processor"
                                                            :enable.auto.commit true
                                                            :auto.commit.interval.ms 1000
                                                            :max.poll.records 1
                                                            :max.poll.interval.ms 10000}}
   :imesc.alarm.mongodb/repository {:host repository-hostname :port repository-port}
   :kafka/producer {:bootstrap.servers bootstrap-servers
                    :client.id "imesc.activator"
                    :batch.size 0
                    :acks "all"
                    :request.timeout.ms 10000}
   :imesc/initiator {:exit-flag (integrant/ref :imesc.core/exit-flag)
                     :request-consumer (integrant/ref :imesc.initiator.kafka/request-consumer)
                     :repository (integrant/ref :imesc.alarm/repository)}
   :imesc.activator/console-notifier-adapter []
   :imesc.activator/email-notifier-adapter {:topic email-requests-topic
                                            :producer (integrant/ref :kafka/producer)}
   :imesc.activator/phone-notifier-adapter {:topic phone-requests-topic
                                            :producer (integrant/ref :kafka/producer)}
   :imesc/activator {:exit-flag (integrant/ref :imesc.core/exit-flag)
                     :repository (integrant/ref :imesc.alarm/repository)
                     :producer (integrant/ref :kafka/producer)
                     :notifier-adapters [{:type :console
                                          :adapter (integrant/ref :imesc.activator/console-notifier-adapter)}
                                         {:type :email
                                          :adapter (integrant/ref :imesc.activator/email-notifier-adapter)}
                                         {:type :phone
                                          :adapter (integrant/ref :imesc.activator/phone-notifier-adapter)}]
                     :poll-millis (or (env "ACTIVATOR_POLL_MILLIS") 5000)}})

(defn initialize!
  ([]
   (initialize! config))
  ([configuration]
   (reset! -system (-> configuration
                       integrant/prep
                       integrant/init))))

(defn halt!
  ([]
   (halt! @-system))
  ([system]
   (integrant.core/halt! system)))


