(ns imesc.alarm
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :as logger]
            [imesc.spec])
  (:import java.util.UUID))

(defprotocol AlarmRepository
  (-overdue-alarms [_ now])
  (-upsert [_ alarm-db-entry])
  (-delete [_ alarm-id])
  (-exists? [_ alarm-id]))

(s/def :imesc/repository #(satisfies? AlarmRepository %))

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

(defn make-alarm [id notifications]
  {:id id
   :at (->> notifications (map :at) earliest)
   :notifications notifications})

(s/fdef make-alarm
  :args (s/cat :id :alarm/id
               :notifications (s/coll-of :alarm/notification :min-count 1))
  :ret :alarm/alarm)

(defn overdue-alarms
  "Returns overdue alarms relative to current time provided in `now`."
  [repository now]
  (-overdue-alarms repository now))

(s/fdef overdue-alarms
  :args (s/cat :repository :imesc/repository
               :now :common/zoned-date-time)
  :ret (s/coll-of :alarm/alarm))

(defn assign-absolute-time [now notification]
  (assoc notification :at (.plusSeconds now (:delay-in-seconds notification))))

(defn assign-id [m]
  (assoc m :id (str (UUID/randomUUID))))

(defn alarm-db-entry [id notifications now]
  (make-alarm id (->> notifications
                      (map (partial assign-absolute-time now))
                      (map assign-id))))

(s/fdef alarm-db-entry
  :args (s/cat :id :alarm/id
               :notifications (s/coll-of :notification/notification :min-count 1)
               :now :common/zoned-date-time)
  :ret :alarm/alarm
  :fn (fn [m] (= (count (-> m :args :notifications))
                (count (-> m :ret :notifications)))))

(defn set-alarm
  "Sets a new alarm."
  [repository process-id notifications current-time]
  (let [entry (alarm-db-entry process-id notifications current-time)]
    (logger/debug "setting alarm" entry)
    (when-let [report (s/explain-data :alarm/alarm entry)]
      (throw (ex-info "Failed to set alarm!" report)))
    (-upsert repository entry)))

(defn delete
  "Deletes an alarm."
  [repository alarm-id]
  (logger/debug "deleting alarm" alarm-id)
  (-delete repository alarm-id))

(defn exists?
  "Returns non-nil if alarm exists."
  [repository alarm-id]
  (-exists? repository alarm-id))
