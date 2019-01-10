(ns imesc.alarm.mongodb
  (:require [imesc.alarm :as alarm]
            [monger.core :as mg]
            [monger.collection :as mc]
            [integrant.core :as integrant]
            [environ.core :refer [env]])
  (:import (com.mongodb MongoOptions ServerAddress WriteConcern)
           (org.bson.types ObjectId)))

(defrecord MongoDbAlarmRepository [connection db])

(def db-name "alarm")
(def alarm-coll "alarm")

(extend-type MongoDbAlarmRepository
  alarm/AlarmRepository
  (overdue-alarms [repository now]
    (mc/find-maps (:db repository) alarm-coll {:time {"$lt" now}}))
  (insert [repository alarm-specification]
    (mc/insert (:db repository) alarm-coll (merge alarm-specification {:_id (ObjectId.)})))
  (delete [repository id])
  (exists? [repository id]))

(defmethod integrant/init-key :alarm/repository [_ opts]
  (let [connection (mg/connect opts)
        db (mg/get-db connection db-name)]
    (MongoDbAlarmRepository. connection db)))

(defmethod integrant/halt-key! :alarm/repository [_ repository]
  (mg/disconnect (:connection repository)))

(comment
  (mc/find-maps (db (:alarm/repository @imesc.config/system)) alarm-coll)
  (mc/insert (db (:alarm/repository @imesc.config/system)) alarm-coll "abc")
  )
