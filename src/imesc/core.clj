(ns imesc.core
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.initiator :as initiator]
            [imesc.initiator.kafka :as initiator.kafka]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as activator.kafka]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:gen-class))

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
  (reset! should-exit? false)
  )
