(ns imesc.core
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [imesc.input.kafka :as kafka]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:import imesc.alarm.AlarmRepository)
  (:gen-class))

(def running (atom true))

(s/def :notification/process-id         string?)
(s/def :notification/action-name        #{:start :stop})
(s/def :notification/channel            #{:console :email :phone})
(s/def :notification/delay-in-seconds   nat-int?)
(s/def :notification/id                 string?)
(s/def :notification/due-by             (partial instance? java.time.ZonedDateTime))

(defn ->alarm-specification [request]
  {:dummy (str (java.util.UUID/randomUUID))})

(defmacro ignoring-exceptions [& body]
  `(try ~@body (catch Exception e# (logger/error "skipping exception:" e#))))

(defmacro ignoring-exceptions-but-sleep [delay-millis & body]
  `(try ~@body (catch Exception e#
                 (logger/error "skipping exception:" e#)
                 (Thread/sleep ~delay-millis))))

(defn valid? [request]
  (and (map? request)
       (#{"start" "stop"} (:action request))))

(defn start-new-process? [request process-already-exists?]
  (and (= "start" (:action request))
       (not process-already-exists?)))

(defn cancel-process? [request]
  (= "stop" (:action request)))

(defn process-request [^AlarmRepository r request]
  (if-not (valid? request)
    (logger/warn "ignoring invalid request:" request)
    (let [pid (:notification/process-id request)]
      (cond
        (start-new-process? request (alarm/exists? r pid))
        (alarm/insert r (->alarm-specification request))

        (cancel-process? request)
        (alarm/delete r pid)))))

(defn main-input-loop [request-supplying-fn request-processing-fn]
  (logger/info "Entering main input loop...")
  (loop []
    (ignoring-exceptions-but-sleep
     1000
     (doseq [request (request-supplying-fn)]
       (logger/debug "processing" request)
       (ignoring-exceptions
        (request-processing-fn request))))
    (when @running (recur)))
  (logger/info "Main input loop finished."))

(defn initialize! []
  (-> config/config
      integrant/prep
      integrant/init))

(def default-request-supplying-fn
  #(->> (kafka/poll-request (:kafka/request-consumer @config/system))
        (map :value)))

(defn -main
  "Starts the system."
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! running false)))
  (reset! config/system (initialize!))
  (future
    (main-input-loop default-request-supplying-fn
                     (partial process-request (:alarm/repository @config/system)))))

(comment
  (do
    (try (integrant/halt! @config/system)
         (catch Throwable _))
    (reset! config/system (initialize!))
    )

  (reset! running false)
  (reset! running true)
  (kafka/poll-request (:kafka/request-consumer @config/system))

  )
