(ns imesc.core
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]
            [clojure.edn :as edn]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [imesc.input.kafka :as kafka]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:import imesc.alarm.AlarmRepository)
  (:gen-class))

(def should-exit? (atom false))

(s/def :notification/process-id         string?)
(s/def :notification/action-name        #{:start :stop})
(s/def :notification/channel            #{:console :email :phone})
(s/def :notification/delay-in-seconds   nat-int?)
(s/def :notification/id                 string?)
(s/def :notification/due-by             (partial instance? java.time.ZonedDateTime))
(s/def ::request map?)

(defn ->alarm-specification [request]
  {:id (:process-id request)
   :dummy (str (java.time.ZonedDateTime/now))})

(defmacro ignoring-exceptions [& body]
  `(try ~@body (catch Exception e# (logger/error "skipping exception:" e#))))

(defmacro ignoring-exceptions-but-sleep [delay-millis & body]
  `(try ~@body (catch Exception e#
                 (logger/error "skipping exception:" e#)
                 (Thread/sleep ~delay-millis))))

(defn valid? [request]
  (and (map? request)
       (#{:start :stop} (:action request))
       (:process-id request)))

(defn analyze [request process-already-exists?]
  (cond
    (and (= :start (:action request))
         (not process-already-exists?))
    :create-new-process

    (= :stop (:action request))
    :cancel-process

    :else
    :ignore-request))

(s/fdef analyze
  :args (s/cat :request ::request :process-exists? boolean?)
  :ret #{:create-new-process :cancel-process :ignore-request})

(defn process-request [^AlarmRepository r request]
  (let [pid (:process-id request)
        process-already-exists? (boolean (alarm/exists? r pid))]
    (case (analyze request process-already-exists?)
      :create-new-process
      (alarm/insert r (->alarm-specification request))

      :cancel-process
      (alarm/delete r pid)

      :ignore-request
      (logger/debug "ignoring request for process" pid "because it does not change state")

      :else
      (logger/error "BUG: unknown scenario"))))

(defn make-main-input-loop
  "Construct the main input loop based on polling.

  The function request-supplying-fn is called without arguments and is expected
  to return a sequence of requests which need to be processed.

  The function request-processing-fn is called with single argument begin
  request and its return value is ignored.

  The function exit-condition-fn is called without arguments and is expected to
  return true or false. When true, the loop will finish."
  [exit-condition-fn request-supplying-fn request-processing-fn]
  (fn []
    (logger/info "Entering main input loop...")
    (loop []
      (ignoring-exceptions-but-sleep
       1000
       (doseq [request (request-supplying-fn)]
         (logger/debug "processing" request)
         (ignoring-exceptions
          (if-not (valid? request)
            (logger/warn "ignoring invalid request:" request)
            (request-processing-fn request)))))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Main input loop finished.")))

(s/fdef make-main-input-loop
  :args (s/cat :exit-condition-fn (s/fspec :args (s/cat) :ret boolean?)
               :request-supplying-fn (s/fspec :args (s/cat) :ret (s/coll-of ::request))
               :request-processing-fn (s/fspec :args (s/cat :request ::request) :ret any?))
  :ret any?)

(defn initialize!
  ([]
   (initialize! config/config))
  ([configuration]
   (-> configuration
       integrant/prep
       integrant/init)))

(def kafka-request-supplying-fn
  #(->> (kafka/poll-request (:kafka/request-consumer @config/system))
        (map :value)
        (map edn/read-string)))

(defn make-kafka-based-main-input-loop [exit-condition-fn]
  (make-main-input-loop exit-condition-fn
                        kafka-request-supplying-fn
                        (partial process-request (:alarm/repository @config/system))))

(defn -main
  "Starts the system."
  [& args]
  (.addShutdownHook (Runtime/getRuntime) (Thread. #(reset! should-exit? false)))
  (reset! config/system (initialize!))
  (future (make-kafka-based-main-input-loop (fn [] @should-exit?))))

