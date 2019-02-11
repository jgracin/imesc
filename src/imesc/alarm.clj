(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]
            [imesc.spec]))

(defprotocol AlarmRepository
  (-overdue-alarms [_ now])
  (-insert [_ alarm-db-entry])
  (-delete [_ alarm-id])
  (-exists? [_ alarm-id]))

(defn overdue-alarms
  "Returns overdue alarms relative to current time provided in `now`."
  [repository now]
  (-overdue-alarms repository now))

(s/fdef overdue-alarms
  :args (s/cat :repository (partial instance? AlarmRepository)
               :now :common/zoned-date-time)
  :ret (s/coll-of :imesc/alarm-db-entry))

(defn set-alarm
  "Sets a new alarm."
  [repository alarm-db-entry]
  (-insert repository alarm-db-entry))

(defn delete
  "Deletes an alarm."
  [repository alarm-id]
  (-delete repository alarm-id))

(defn exists?
  "Returns non-nil if alarm exists."
  [repository alarm-id]
  (-exists? repository alarm-id))
