(ns imesc.core
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.alarm.mongodb]
            [imesc.initiator :as initiator]
            [imesc.initiator.kafka :as initiator.kafka]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as activator.kafka]
            [imesc.activator.console]
            [imesc.activator.email]
            [imesc.activator.phone]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:gen-class))

;; We must synchronize alarm repository access between the Initiator and the
;; Activator to avoid race conditions when canceling and updating alarms.
(defonce repository-lock (Object.))

(defn make-kafka-based-main-input-loop [repository request-consumer exit-condition-fn]
  (initiator/make-main-input-loop
   exit-condition-fn
   (initiator.kafka/kafka-request-polling-fn request-consumer)
   (fn [request]
     (locking repository-lock
       (initiator/process-request repository request)))))

(defmethod integrant/init-key :imesc.core/exit-flag [_ config]
  (atom false))

(defn- ->adapter-registry [notifier-adapters]
  (reduce (fn [m {:keys [type adapter]}]
            (assoc m type adapter))
          {}
          notifier-adapters))

(defmethod integrant/init-key :imesc/activator [_ {:keys [exit-flag repository
                                                          producer poll-millis
                                                          notifier-adapters]}]
  (let [activator-loop
        (activator/make-activator-loop
         poll-millis
         (fn [] @exit-flag)
         (activator/default-repository-polling-fn repository)
         (activator/default-processing-fn repository producer
                                          (->adapter-registry notifier-adapters)))]
    (future (activator-loop))))

(defmethod integrant/init-key :imesc/initiator [_ {:keys [exit-flag repository request-consumer]}]
  (let [initiator-loop (make-kafka-based-main-input-loop repository request-consumer (fn [] @exit-flag))]
    (future (initiator-loop))))

(defn -main
  "Starts the system."
  [& args]
  (logger/info "Starting...")
  (let [system (config/initialize!)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(reset! (:exit-flag system) true)))))
