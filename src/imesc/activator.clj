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
            [clojure.spec.alpha :as s])
  (:import java.time.ZonedDateTime
           imesc.alarm.AlarmRepository))

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
  (merge (select-keys notification [:id :at])
         (:params notification)))

(s/fdef notification->notifier-request
  :args (s/cat :notification :alarm/notification)
  :ret (s/or :console :notifier/console-request
             :phone :notifier/phone-request
             :email :notifier/email-request))

(defn due?
  "Returns true if notification is due to be delivered."
  [notification now]
  (.isAfter now (:at notification)))

(s/fdef due?
  :args (s/cat :notification :alarm/notification
               :now :common/zoned-date-time)
  :ret boolean?)

(defn process [alarm repository now]
  )

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
  (stest/check `notification->notifier-request)
  (gen/sample (s/gen :alarm/notification))

  (def oda (alarm/overdue-alarms (:alarm/repository @config/system)
                                 (ZonedDateTime/now)))
  (:params (first oda))

  (let [activator-loop (make-activator-loop)]
    (future (activator-loop)))
  )
