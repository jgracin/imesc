(ns imesc.alarm.mongodb
  (:require [imesc.alarm :as alarm]
            [monger.core :as mg]
            [monger.collection :as mc]
            [integrant.core :as integrant]
            [environ.core :refer [env]]
            [clojure.tools.logging :as logger]
            [monger.conversion]
            [clojure.spec.alpha :as s])
  (:import (com.mongodb MongoOptions ServerAddress WriteConcern)
           (org.bson.types ObjectId)
           (java.time ZonedDateTime)
           (java.util Date)))

(defrecord MongoDbAlarmRepository [connection db])

(def db-name "alarm")
(def alarm-coll "alarm")

(defn- keywordize-channel [notification]
  (update-in notification [:channel] keyword))

(extend-type MongoDbAlarmRepository
  alarm/AlarmRepository
  (-overdue-alarms [repository now]
    (let [result (mc/find-maps (:db repository) alarm-coll {:at {"$lt" now}})]
      (->> result
           (map #(assoc % :notifications
                        (map keywordize-channel (:notifications result)))))))
  (-upsert [repository alarm-db-entry]
    (logger/debug "upserting alarm-db-entry" alarm-db-entry)
    (mc/remove (:db repository) alarm-coll {:id alarm-db-entry})
    (mc/insert (:db repository) alarm-coll (merge alarm-db-entry {:_id (ObjectId.)})))
  (-delete [repository id]
    (logger/debug "deleting an alarm" id)
    (mc/remove (:db repository) alarm-coll {:id id}))
  (-exists? [repository id]
    (mc/find-one (:db repository) alarm-coll {:id id})))

(derive :imesc.alarm.mongodb/repository :imesc.alarm/repository)

(defmethod integrant/init-key :imesc.alarm.mongodb/repository [_ opts]
  (let [connection (mg/connect opts)
        db (mg/get-db connection db-name)]
    (MongoDbAlarmRepository. connection db)))

(defmethod integrant/halt-key! :imesc.alarm.mongodb/repository [_ repository]
  (mg/disconnect (:connection repository)))

(extend-protocol monger.conversion/ConvertToDBObject
  java.time.ZonedDateTime
  (to-db-object [^ZonedDateTime input]
    (monger.conversion/to-db-object (Date/from (.toInstant input)))))

(extend-protocol monger.conversion/ConvertFromDBObject
  java.util.Date
  (from-db-object [^java.util.Date input keywordize]
    (ZonedDateTime/ofInstant (.toInstant input) (java.time.ZoneId/systemDefault))))

