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
           imesc.alarm.AlarmRepository
           java.util.UUID))

(defn assign-absolute-time [now notification]
  (assoc notification :at (.plusSeconds now (:delay-in-seconds notification))))

(defn assign-id [m]
  (assoc m :id (str (UUID/randomUUID))))

(defn alarm-db-entry [id notifications now]
  (let [sorted-notifications (->> notifications
                                  (map (partial assign-absolute-time now))
                                  (map assign-id)
                                  (sort-by :at))]
    {:id id
     :at (-> sorted-notifications first :at)
     :notifications sorted-notifications}))

(s/fdef alarm-db-entry
  :args (s/cat :id :alarm/id
               :notifications (s/coll-of :notification/notification :min-count 1)
               :now :common/zoned-date-time)
  :ret :imesc/alarm-db-entry
  :fn (fn [m] (= (count (-> m :args :notifications))
                (count (-> m :ret :notifications)))))

(defn valid? [request]
  (s/valid? :imesc/request request))

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

(defn process-request [^AlarmRepository r request]
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
            (logger/warn "ignoring invalid request with process-id" (:process-id request))
            (request-processing-fn request)))))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Main input loop finished.")))

