(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]
            [imesc.spec]))

(defprotocol AlarmRepository
  (-overdue-alarms [_ now])
  (-upsert [_ alarm-db-entry])
  (-delete [_ alarm-id])
  (-exists? [_ alarm-id]))

(s/def :imesc/repository #(satisfies? AlarmRepository %))

(defn overdue-alarms
  "Returns overdue alarms relative to current time provided in `now`."
  [repository now]
  (-overdue-alarms repository now))

(s/fdef overdue-alarms
  :args (s/cat :repository :imesc/repository
               :now :common/zoned-date-time)
  :ret (s/coll-of :alarm/alarm))

(defn set-alarm
  "Sets a new alarm."
  [repository alarm-db-entry]
  (logger/debug "setting alarm" alarm-db-entry)
  (when-let [report (s/explain-data :alarm/alarm alarm-db-entry)]
    (throw (ex-info "Failed to set alarm!" report)))
  (-upsert repository alarm-db-entry))

(defn delete
  "Deletes an alarm."
  [repository alarm-id]
  (logger/debug "deleting alarm" alarm-id)
  (-delete repository alarm-id))

(defn exists?
  "Returns non-nil if alarm exists."
  [repository alarm-id]
  (-exists? repository alarm-id))
