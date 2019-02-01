(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]))

(defprotocol AlarmRepository
  (-overdue-alarms [_ now] "Returns overdue alarms.")
  (-insert [_ alarm-entry] "Sets a new alarm.")
  (-delete [_ alarm-id] "Deletes an alarm.")
  (-exists? [_ alarm-id] "Returns non-nil if alarm exists."))

(defn overdue-alarms [repository now]
  (-overdue-alarms repository now))

(defn insert [repository alarm-entry]
  (-insert repository alarm-entry))

(defn delete [repository alarm-id]
  (-delete repository alarm-id))

(defn exists? [repository alarm-id]
  (-exists? repository alarm-id))
