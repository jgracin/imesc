(ns imesc.initiator
  "Initiator handles incoming requests and initiates escalation processes."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.tools.logging :as logger]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.set :refer [subset?]]
            [imesc.spec]
            [imesc.util :refer [ignoring-exceptions ignoring-exceptions-but-with-sleep]]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [environ.core :refer [env]])
  (:import (java.time ZonedDateTime ZoneId Instant)
           java.util.UUID))

(defn assign-absolute-time [now notification]
  (assoc notification :at (.plusSeconds now (:delay-in-seconds notification))))

(defn assign-id [m]
  (assoc m :id (str (UUID/randomUUID))))

(defn alarm-db-entry [id notifications now]
  (alarm/make-alarm id (->> notifications
                            (map (partial assign-absolute-time now))
                            (map assign-id))))

(s/fdef alarm-db-entry
  :args (s/cat :id :alarm/id
               :notifications (s/coll-of :notification/notification :min-count 1)
               :now :common/zoned-date-time)
  :ret :alarm/alarm
  :fn (fn [m] (= (count (-> m :args :notifications))
                (count (-> m :ret :notifications)))))

(defn valid? [request]
  (and (s/valid? :imesc/request request)
       (if (= :start (:action request))
         (seq (:notifications request))
         true)))

(defn next-action [request process-already-exists?]
  (cond
    (and (= :start (:action request))
         (not process-already-exists?))
    :create-new-process

    (= :stop (:action request))
    :cancel-process

    :else
    :ignore-request))

(s/fdef next-action
  :args (s/cat :request :imesc/request :process-exists? boolean?)
  :ret #{:create-new-process :cancel-process :ignore-request})

(defn process-request [r request]
  (let [pid (:process-id request)
        process-already-exists? (boolean (alarm/exists? r pid))
        now (java.time.ZonedDateTime/now)]
    (case (next-action request process-already-exists?)
      :create-new-process
      (alarm/set-alarm r (alarm-db-entry (:process-id request) (:notifications request) now))

      :cancel-process
      (alarm/delete r pid)

      :ignore-request
      (logger/debug "ignoring request for process" pid "because it does not change state")

      :else
      (logger/error "BUG: unknown scenario"))))

(defn make-main-input-loop
  "Construct the main input loop based on polling.

  The function request-polling-fn is called without arguments and is expected
  to return a sequence of requests which need to be processed.

  The function request-processing-fn is called with single argument begin
  request and its return value is ignored.

  The function exit-condition-fn is called without arguments and is expected to
  return true or false. When true, the loop will finish."
  [exit-condition-fn request-polling-fn request-processing-fn]
  (fn []
    (logger/info "Entering main input loop...")
    (loop []
      (ignoring-exceptions-but-with-sleep
       1000
       (doseq [request (request-polling-fn)]
         (logger/debug "processing" (pr-str request))
         (ignoring-exceptions
          (if-not (valid? request)
            (logger/warn "ignoring invalid request" request)
            (request-processing-fn request)))))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Main input loop finished.")))

