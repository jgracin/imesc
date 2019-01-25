(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]))

(defprotocol AlarmRepository
  (overdue-alarms [_ now] "Returns overdue alarms.")
  (insert [_ alarm-entry] "Sets a new alarm.")
  (delete [_ alarm-id] "Deletes an alarm.")
  (exists? [_ alarm-id] "Returns non-nil if alarm exists."))
