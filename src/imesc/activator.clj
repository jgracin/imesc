(ns imesc.activator
  "Activator periodically scans alarm repository and activates notifiers by
  sending them activation messages."
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.initiator :as initiator]
            [imesc.spec]
            [imesc.alarm :as alarm]
            [imesc.util :as util :refer [ignoring-exceptions-but-with-sleep
                                         ignoring-exceptions]]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest])
  (:import (java.time ZonedDateTime)
           (imesc.alarm AlarmRepository)))

(s/def :notifier/console-request
  (s/keys :req-un [:notification/id :notification/at]
          :opt-un [:notification/message]))

(s/def :notifier/email-request
  (s/keys :req-un [:notification/id :notification/at
                   :email/to :email/subject :email/body]))

(s/def :notifier/phone-request
  (s/keys :req-un [:notification/id :notification/at
                   :common/phone-number]))

(defmulti activate
  "Activates notifications through the channel specified in params."
  (keyword :channel))

(defmethod activate :default [notification]
  (logger/error "Unknown notification channel, not activating! notification=" notification))

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

(defn- update-db-alarm [repository alarm]
  (alarm/delete repository (:id alarm))
  (alarm/set-alarm repository alarm))

(s/fdef update-db-alarm
  :args (s/cat :repository (partial satisfies? imesc.alarm/AlarmRepository)
               :alarm :imesc/alarm-db-entry))

(defn ->requests [notifications now]
  (->> (filter (partial due? now) notifications)
       (map ->notifier-request)))

(s/fdef ->requests
  :args (s/cat :notifications (s/coll-of :alarm/notification)
               :now :common/zoned-date-time)
  :ret (s/coll-of :activator/notifier-request))

(defn earliest [times]
  (reduce (fn [min-so-far t]
            (if (.isBefore t min-so-far)
              t
              min-so-far))
          (first times)
          times))

(s/fdef earliest
  :args (s/cat :times (s/coll-of :common/zoned-date-time))
  :fn (fn [{:keys [args ret]}]
        (if (not (empty? (:times args)))
          (= (first (sort (:times args)))
             ret)
          true))
  :ret (s/nilable :common/zoned-date-time))

(defn process [alarm repository now]
  (let [due-notifs (filter (partial due? now) (:notifications alarm))
        non-due-notifs (filter (complement (partial due? now)) (:notifications alarm))
        requests (map ->notifier-request due-notifs)]
    (doseq [r requests] (activate r))
    (when (seq non-due-notifs)
      (update-db-alarm repository (assoc alarm
                                         :notifications non-due-notifs
                                         :at (->> non-due-notifs (map :at) earliest))))))

(s/fdef process
  :args (s/cat :alarm :imesc/alarm-db-entry
               :repository (partial satisfies? imesc.alarm/AlarmRepository)
               :now :common/zoned-date-time)
  :ret any?)

(def default-repository-polling-fn
  (fn []
    (alarm/overdue-alarms (:alarm/repository @config/system)
                          (ZonedDateTime/now))))

(def default-processing-fn
  (fn [alarm]
    (process alarm
             (:alarm/repository @config/system)
             (ZonedDateTime/now))))

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

(comment
  (gen/sample (s/gen :alarm/notification))

  (def oda (alarm/overdue-alarms (:alarm/repository @config/system)
                                 (ZonedDateTime/now)))
  (:params (first oda))

  (let [activator-loop (make-activator-loop)]
    (future (activator-loop)))
  )
