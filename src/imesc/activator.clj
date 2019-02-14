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

(defn- notification->notifier-request
  [notification]
  (merge (select-keys notification [:id :at :channel])
         (:params notification)))
(s/fdef notification->notifier-request
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

(defn ->requests [notifications now]
  (->> (filter (partial due? now) notifications)
       (map notification->notifier-request)))
(s/fdef ->requests
  :args (s/cat :notifications (s/coll-of :alarm/notification)
               :now :common/zoned-date-time)
  :ret (s/coll-of :activator/notifier-request))

(defn earliest [times]
  (-> times sort first))

(defn ->updated-alarm [alarm now]
  (let [notifications (filter (complement (partial due? now)) (:notifications alarm))]
    (assoc alarm
           :notifications notifications
           :at (or (->> notifications (map :at) earliest)
                   (:at alarm)))))
(s/fdef ->updated-alarm
  :args (s/cat :alarm :imesc/alarm-db-entry :now :common/zoned-date-time)
  :ret :imesc/alarm-db-entry)

(defn process [alarm repository now]
  (let [[due-notifs non-due-notifs] (split-with (partial due? now) (:notifications alarm))
        requests (map notification->notifier-request due-notifs)]
    (doseq [r requests] (activate r))
    (update-db-alarm repository (assoc alarm :notifications non-due-notifs))))
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
  (require '[clojure.spec.test.alpha :as stest]
           '[clojure.spec.gen.alpha :as gen])
  (defn replcheck
    "Run a test.check check on a sym."
    ([sym]
     (replcheck sym 20))
    ([sym num-tests]
     (stest/instrument)
     (let [opts {:clojure.spec.test.check/opts {:num-tests num-tests}
                 :assert-checkable true}
           result (-> (stest/check sym opts)
                      first
                      :clojure.spec.test.check/ret
                      :result)]
       (if (= true result)
         :success
         #_(throw (ex-info "Failed check." (ex-data result) (:cause result)))
         result))))
  (replcheck `->requests)

  (stest/instrument `->requests)
  (stest/check `->requests opts)
  ;; if a check fails, this is the way to get more readable output
  (-> (stest/check `->requests opts)
      first
      :clojure.spec.test.check/ret
      :result
      throw)

  (gen/sample (s/gen :alarm/notification))

  (def oda (alarm/overdue-alarms (:alarm/repository @config/system)
                                 (ZonedDateTime/now)))
  (:params (first oda))

  (let [activator-loop (make-activator-loop)]
    (future (activator-loop)))
  )
