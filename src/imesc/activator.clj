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
  (merge (dissoc notification :params)
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

(defn- due [now notifications]
  (filter (partial due? now) notifications))

(defn- decide [now notifications]
  (let [remaining-notifs (filter #(not (due? now %)) notifications)]
    (cond
      (empty? notifications)
      {:decision :ignore}

      (empty? remaining-notifs)
      {:decision :finish-the-process}

      (not= remaining-notifs notifications)
      {:decision :update-state :notifications remaining-notifs}

      :else
      {:decision :ignore})))

(s/def :activator/decision #{:finish-the-process :ignore :update-state})

(s/fdef decide
  :args (s/cat :now :common/zoned-date-time
               :notifications :alarm/notifications)
  :fn (fn [{:keys [args ret]}]
        (if (empty? (:notifications args))
          (= :ignore (:decision ret))
          true))
  :ret (s/keys :req-un [:activator/decision]))

(defn- find-handler [adapter-registry activator-channel]
  (get adapter-registry activator-channel))

(defn process [repository adapter-registry alarm now]
  (doseq [request (->> (:notifications alarm)
                       (due now)
                       (map ->notifier-request))]
    (let [handler (find-handler adapter-registry (:channel request))]
      (when (nil? handler)
        (throw (ex-info "BUG: unable to find activator handler for channel" (:channel request))))
      (ignoring-exceptions (handler request))))
  (let [result (decide now (:notifications alarm))]
    (case (:decision result)
      :finish-the-process
      (alarm/delete repository (:id alarm))

      :update-state
      (alarm/set-alarm repository (alarm/make-alarm (:id alarm) (:notifications result)))

      :ignore
      nil

      (logger/error "BUG: unrecognized result" result))))

(s/fdef process
  :args (s/cat :repository :imesc/repository
               :adapters :activator/adapter-registry
               :alarm :alarm/alarm
               :now :common/zoned-date-time))

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
