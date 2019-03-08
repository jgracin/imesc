(ns imesc.activator
  "Activator periodically scans alarm repository and activates notifiers."
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.initiator :as initiator]
            [imesc.spec]
            [imesc.alarm :as alarm]
            [imesc.util :as util :refer [ignoring-exceptions-but-with-sleep
                                         ignoring-exceptions]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest])
  (:import java.time.ZonedDateTime))

(s/def :notifier/console-request
  (s/keys :req-un [:notification/id :notification/at :notification/channel]
          :opt-un [:notification/message]))

(s/def :notifier/email-request
  (s/keys :req-un [:notification/id :notification/at :notification/channel
                   :email/to :email/subject :email/body]))

(s/def :notifier/phone-request
  (s/keys :req-un [:notification/id :notification/at :notification/channel
                   :common/phone-number]))

(defn- ->notifier-request
  [notification]
  (merge (select-keys notification [:id :at :channel])
         (:params notification)))

(s/fdef ->notifier-request
  :args (s/cat :notification :alarm/notification)
  :ret :activator/notifier-request)

(defn due?
  "Returns true if notification is due to be delivered."
  [now notification]
  (.isAfter now (:at notification)))

(s/fdef due?
  :args (s/cat :now :common/zoned-date-time
               :notification :alarm/notification)
  :ret boolean?)

(defn requests-to-send
  "Returns notifier requests which are due to be sent with respect to the current
  time in `now`."
  [notifications now]
  (->> notifications
       (filter (partial due? now))
       (map ->notifier-request)))

(defn remaining-notifications [notifications now]
  (filter (complement (partial due? now)) notifications))

(defn up-to-date-alarm
  "Returns alarm with pruned notifications based on time `now`."
  [alarm now]
  (let [notifs (remaining-notifications (:notifications alarm) now)]
    (when (seq notifs)
      (alarm/make-alarm (:id alarm) notifs))))

(defn activate [adapter-registry request]
  (let [handler (get adapter-registry (:channel request))]
    (logger/info "in activate, handling request")
    (handler request)))

(s/fdef activate
  :args (s/cat :adapters :activator/adapter-registry
               :request :activator/notifier-request))

(defn process [repository adapter-registry alarm now]
  (let [requests (requests-to-send (:notifications alarm) now)]
    (doseq [request requests]
      (ignoring-exceptions
       (activate adapter-registry request)))
    (let [alarm' (up-to-date-alarm alarm now)]
      (cond
        (empty? (:notifications alarm'))
        (alarm/delete repository (:id alarm))

        (not= (:notifications alarm) (:notifications alarm'))
        (alarm/set-alarm repository alarm')))))

(s/fdef process
  :args (s/cat :repository :imesc/repository
               :adapters :activator/adapter-registry
               :alarm :alarm/alarm
               :now :common/zoned-date-time)
  :ret any?)

(defn default-repository-polling-fn [repository]
  (fn []
    (alarm/overdue-alarms repository (ZonedDateTime/now))))

(defn default-processing-fn [repository producer adapter-registry]
  (fn [alarm]
    (process repository adapter-registry alarm (ZonedDateTime/now))))

;; FIXME We're not locking the repository and we should. Use
;; core/repository-lock.
(defn make-activator-loop
  [sleep-millis exit-condition-fn polling-fn processing-fn]
  (fn []
    (logger/info "Starting activator polling...")
    (loop []
      (ignoring-exceptions-but-with-sleep
       1000
       (doseq [alarm (polling-fn)]
         (ignoring-exceptions
          (logger/debug "processing" (pr-str alarm))
          (processing-fn alarm)))
       (Thread/sleep sleep-millis))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Activator polling finished.")))
