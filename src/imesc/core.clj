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
            [environ.core :refer [env]]))

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

(defn -main
  "Starts the system."
  [& args]
  (logger/info "Starting...")
  (let [system (config/initialize!)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(reset! (:exit-flag system) true)))))

(defmethod integrant/init-key :imesc.core/exit-flag [_ config]
  (atom false))

(defmethod integrant/init-key :imesc/activator [_ {:keys [exit-flag repository producer poll-millis]}]
  (let [activator-loop
        (activator/make-activator-loop poll-millis
                                       (fn [] @exit-flag)
                                       (activator/default-repository-polling-fn repository)
                                       (activator/default-processing-fn repository producer))]
    (future (activator-loop))))

(defmethod integrant/init-key :imesc/initiator [_ {:keys [exit-flag repository request-consumer]}]
  (let [initiator-loop (make-kafka-based-main-input-loop repository request-consumer (fn [] @exit-flag))]
    (future (initiator-loop))))

(comment
  (-main)
  (config/initialize!)
  (defn system [] (deref (deref #'config/-system)))
  (reset! (:imesc.core/exit-flag (system)) true)
  (reset! (:imesc.core/exit-flag (system)) false)
  (config/halt!)
  (-> config/config integrant/prep (integrant/init [:imesc/initiator]))
  (-> config/config integrant/prep (integrant/init [:imesc/activator]))

  (activator/activate {:id "3f18c862-5406-4246-a15c-33205966b06b" :at (java.time.ZonedDateTime/now),
                       :channel "phone" :phone-number "38599000001" :message "new-order-unconfirmed"})
  )
