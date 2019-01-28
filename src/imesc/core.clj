(ns imesc.core
  (:require [clojure.tools.logging :as logger]
            [clojure.edn :as edn]
            [imesc.config :as config]
            [imesc.input :as input]
            [imesc.input.kafka :as kafka]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:gen-class))

(def should-exit? (atom false))

(defn initialize!
  ([]
   (initialize! config/config))
  ([configuration]
   (-> configuration
       integrant/prep
       integrant/init)))

(def kafka-request-polling-fn
  #(->> (kafka/poll-request (:kafka/request-consumer @config/system))
        (map (comp edn/read-string :value))))

(defn make-kafka-based-main-input-loop [exit-condition-fn]
  (input/make-main-input-loop exit-condition-fn
                              kafka-request-polling-fn
                              (partial input/process-request (:alarm/repository @config/system))))

(defn -main
  "Starts the system."
  [& args]
  (logger/info "Starting...")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! should-exit? false)))
  (reset! config/system (initialize!))
  (let [main-loop (make-kafka-based-main-input-loop (fn [] @should-exit?))]
    (future (main-loop))))

