(ns imesc.case-repository.mongodb
  (:require [imesc.case-repository :as case-repository]
            [monger.core :as mg]
            [monger.collection :as mc]
            [integrant.core :as integrant])
  (:import (com.mongodb MongoOptions ServerAddress WriteConcern)
           (org.bson.types ObjectId)))

(defrecord MongoDbRepository [connection])

(def db-name "alarm")

(defn db [repository]
  (mg/get-db (:connection repository) db-name))

(extend-type MongoDbRepository
  case-repository/CaseRepository
  (overdue-cases [repository now]
    (mc/find-maps (db repository) db-name {:time {"$lt" now}}))
  (save-case [repository c]
    (mc/insert (db repository) db-name c))
  (delete-case [repository id]))

(defn make-repository [{:keys [host port]}]
  (MongoDbRepository. (mg/connect {:host host :port port})))

(defmethod integrant/init-key :imesc.case-repository/case-repository
  [_ config]
  (make-repository config))

(comment
  "Trying repository implementation"
  (def r (make-repository {:host "localhost" :port 27017}))
  (case-repository/save-case r {:_id (ObjectId.) :time (java.util.Date.) :data {:process-id "finpoint"}})
  (case-repository/overdue-cases r (java.util.Date.))
  )
(comment
  "Trying out MongoDb"
  (mg/set-default-write-concern! WriteConcern/ACKNOWLEDGED)
  (def conn (mg/connect))
  (def db   (mg/get-db conn "monger-test"))
  (defn now-plus [seconds]
    (java.util.Date/from (-> (java.time.ZonedDateTime/now) (.plusSeconds seconds) .toInstant)))
  (defn alarm-entry []
    {:_id (ObjectId.)
     :time (now-plus 11)
     :data {:process-id "finpoint"
            :notifications [{:after-seconds 10
                             :time (now-plus 10)
                             :channel :debug-console
                             :params {:message "First dummy notification to console."}}
                            {:after-seconds 15
                             :time (now-plus 15)
                             :channel :debug-console
                             :params {:message "Second dummy notification to console."}}
                            {:after-seconds 300
                             :time (now-plus 300)
                             :channel :email
                             :params {:to ["orders@example.com"]
                                      :subject "You have unconfirmed new orders in RoomOrders service."
                                      :body "Visit https://roomorders.com."}}
                            {:after-seconds 600
                             :time (now-plus 600)
                             :channel :phone
                             :params {:number "38599000001"
                                      :voice-message "new-order-unconfirmed"}}]}})

  (mc/insert db "alarms" (alarm-entry))
  (count (mc/find-maps db "alarms" {:time {"$lt" (java.util.Date.) }}))

  (println (mc/find-one-as-map db "alarms" {:time {"$lt" (java.util.Date.) }}))
  (mc/count db "alarms" )
  (mc/remove-by-id db "alarms" (ObjectId. "5bfff2f62e53d9744b857ec4"))
  (mc/remove db "alarms" {:time {"$lt" (java.util.Date.) }})

  (dotimes [cnt 10000]
    (mc/insert db "alarms" (merge (alarm-entry) {:_id (ObjectId.) })))

  (mc/upsert db "documents" {:_id 1 :a 300 :b 2})
  (mc/count db "documents")
  (mc/find-by-id db "documents" 1)
  )
