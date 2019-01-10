(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]))

(s/def :alarm/at   (partial instance? java.time.ZonedDateTime))
(s/def :alarm/id   string?)
(s/def :alarm/data any?)

(defprotocol AlarmRepository
  (overdue-alarms [_ now] "Returns overdue alarms.")
  (insert [_ alarm-specification] "Sets a new alarm.")
  (delete [_ alarm-id] "Deletes an alarm.")
  (exists? [_ alarm-id] "Returns non-nil if alarm exists."))
