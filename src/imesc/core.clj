(ns imesc.core
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.input :as input]
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

(defn -main
  "Starts the system."
  [& args]
  (logger/info "Starting...")
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! should-exit? false)))
  (reset! config/system (initialize!))
  (let [main-loop (input/make-kafka-based-main-input-loop (fn [] @should-exit?))]
    (future (main-loop))))

