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

(defmulti activate
  "Activates notifications through the channel specified in params."
  (fn [system request] (keyword (:channel request))))

(defmethod activate :default [system request]
  (logger/error "Unknown notification channel, NOT activating! request=" request))

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

(defn requests-to-send
  "Returns notifier requests which are due to be sent based on `alarm` and current
  time in `now`."
  [alarm now]
  (->> (:notifications alarm)
       (filter (partial due? now))
       (map ->notifier-request)))

(defn remaining-notifications [notifications now]
  (filter (complement (partial due? now)) notifications))

(defn up-to-date-alarm
  "Returns alarm with pruned notifications based on time `now`."
  [alarm now]
  (let [notifs (remaining-notifications (:notifications alarm) now)]
    (when notifs
      (assoc alarm
             :notifications notifs
             :at (->> notifs (map :at) earliest)))))

(defn process [repository alarm now]
  (let [requests (requests-to-send alarm now)]
    (doseq [request requests]
      (ignoring-exceptions
       (activate nil request))) ;; FIXME
    (let [alarm' (up-to-date-alarm alarm now)]
      (if (empty? (:notifications alarm'))
        (alarm/delete repository (:id alarm))
        (alarm/set-alarm repository alarm')))))

(s/fdef process
  :args (s/cat :repository :imesc/repository
               :alarm :alarm/alarm
               :now :common/zoned-date-time)
  :ret any?)

(defn default-repository-polling-fn [repository]
  (fn []
    (alarm/overdue-alarms repository (ZonedDateTime/now))))

(defn default-processing-fn [repository producer]
  (fn [alarm]
    (process repository alarm (ZonedDateTime/now))))

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


