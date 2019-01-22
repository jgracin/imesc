(ns imesc.alarm.mongodb
  (:require [imesc.alarm :as alarm]
            [monger.core :as mg]
            [monger.collection :as mc]
            [integrant.core :as integrant]
            [environ.core :refer [env]]
            [clojure.tools.logging :as logger]
            [monger.conversion])
  (:import (com.mongodb MongoOptions ServerAddress WriteConcern)
           (org.bson.types ObjectId)
           (java.time ZonedDateTime)
           (java.util Date)))

(defrecord MongoDbAlarmRepository [connection db])

(def db-name "alarm")
(def alarm-coll "alarm")

(extend-type MongoDbAlarmRepository
  alarm/AlarmRepository
  (overdue-alarms [repository now]
    (mc/find-maps (:db repository) alarm-coll {:time {"$lt" now}}))
  (insert [repository alarm-specification]
    (logger/debug "inserting alarm-specification" alarm-specification)
    (mc/insert (:db repository) alarm-coll (merge alarm-specification {:_id (ObjectId.)}))
    (logger/debug "after insert"))
  (delete [repository id])
  (exists? [repository id]
    (mc/find-one (:db repository) alarm-coll {:id id})))

(defmethod integrant/init-key :alarm/repository [_ opts]
  (let [connection (mg/connect opts)
        db (mg/get-db connection db-name)]
    (MongoDbAlarmRepository. connection db)))

(defmethod integrant/halt-key! :alarm/repository [_ repository]
  (mg/disconnect (:connection repository)))

(extend-protocol monger.conversion/ConvertToDBObject
  java.time.ZonedDateTime
  (to-db-object [^ZonedDateTime input]
    (monger.conversion/to-db-object (Date/from (.toInstant input)))))

(extend-protocol monger.conversion/ConvertFromDBObject
  java.util.Date
  (from-db-object [^java.util.Date input keywordize]
    (ZonedDateTime/ofInstant (.toInstant input) (java.time.ZoneId/systemDefault))))

(comment
  (mc/find-maps (:db (:alarm/repository @imesc.config/system)) alarm-coll)
  (mc/insert (:db (:alarm/repository @imesc.config/system)) alarm-coll "abc")
  (mc/find-one (:db (:alarm/repository @imesc.config/system)) alarm-coll {:id "2580f5b5-db89-49e4-b762-66a0144de9d9"})
  )
