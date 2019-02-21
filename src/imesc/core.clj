(ns imesc.core
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.initiator :as initiator]
            [imesc.initiator.kafka :as initiator.kafka]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as activator.kafka]
            [imesc.notifier.console]
            [imesc.notifier.email]
            [imesc.notifier.phone]
            [integrant.core :as integrant]
            [environ.core :refer [env]]))

(def should-exit? (atom false))

(def default-activator-poll-millis (or (env "ACTIVATOR_POLL_MILLIS") 5000))

;; We must synchronize alarm repository access between the Initiator and the
;; Activator to avoid race conditions when canceling and updating alarms.
(defonce repository-lock (Object.))

(defn initialize!
  ([]
   (initialize! config/config))
  ([configuration]
   (-> configuration
       integrant/prep
       integrant/init)))

(defn make-kafka-based-main-input-loop [exit-condition-fn]
  (initiator/make-main-input-loop
   exit-condition-fn
   initiator.kafka/kafka-request-polling-fn
   (fn [request]
     (locking repository-lock
       (initiator/process-request (:alarm/repository @config/system)
                                  request)))))

(defn -main
  "Starts the system."
  [& args]
  (logger/info "Starting...")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! should-exit? false)))
  (reset! config/system (initialize!))
  (let [activator-loop (activator/make-activator-loop default-activator-poll-millis
                                                      (fn [] @should-exit?)
                                                      activator/default-repository-polling-fn
                                                      activator/default-processing-fn)]
    (future (activator-loop)))
  (let [initiator-loop (make-kafka-based-main-input-loop (fn [] @should-exit?))]
    (future (initiator-loop))))

(comment
  (-main)
  (reset! should-exit? true)
  (reset! should-exit? false)
  (integrant/halt! @config/system)
  (satisfies? imesc.alarm/AlarmRepository (:alarm/repository @config/system))
  (activator/activate {:id "3f18c862-5406-4246-a15c-33205966b06b" :at (java.time.ZonedDateTime/now),
                       :channel "phone" :phone-number "38599000001" :message "new-order-unconfirmed"})
  )
