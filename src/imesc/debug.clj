(ns imesc.debug
  (:require [imesc.config :as config]
            [integrant.core :as ig]
            [clojurewerkz.quartzite.scheduler :as scheduler]
            [clojurewerkz.quartzite.jobs :as jobs :refer [defjob]]
            [clojurewerkz.quartzite.triggers :as triggers]
            [clojurewerkz.quartzite.schedule.simple :as simple]
            [imesc.config :as config]))

(comment
  "Quartzite scheduler"
  (def s (-> (scheduler/initialize) scheduler/start))
  (defjob NoOpJob
    [ctx]
    (println "Hello!"))
  (def job1 (jobs/build (jobs/of-type NoOpJob) (jobs/with-identity (jobs/key "job1"))))
  (def trigger (triggers/build
                (triggers/with-identity (qt/key "triggers.1"))
                (triggers/start-now)
                (triggers/with-schedule (simple/schedule
                                         (simple/with-repeat-count 10)
                                         (simple/with-interval-in-milliseconds 1000)))))
  (scheduler/schedule s job1 trigger)
  )

(comment
  "Services, messages, APIs"
  ;;
  ;; RoomOrders Escalation Adapter services
  ;;
  ["PUT /restaurant/finpoint/configuration"
   {:descriptors [{:delay 10
                     :channel :console
                     :params {:message "First dummy notification to console."}}
                    {:delay 15
                     :channel :console
                     :params {:message "Second dummy notification to console."}}
                    {:delay 300
                     :channel :email
                     :params {:to ["orders@example.com"]
                              :subject "You have unconfirmed new orders in RoomOrders."
                              :body "Visit https://roomorders.com."}}
                    {:delay 600
                     :channel :phone
                     :params {:phone-number "38599000001"
                              :message "new-order-unconfirmed"}}]}

   "GET /restaurant/finpoint/configuration" {}

   "POST /events" {:name "OrderCreated"
                   :time "2018-11-28T12:00:00Z"
                   :order-id "R384729374"
                   :restaurant-id "finpoint"
                   :user-id "238472"}
   "POST /events" {:name "OrderConfirmed" ;; accepted? confirmed? completed? canceled?
                   :time "2018-11-28T12:03:00Z"
                   :order-id "R384729374"
                   :restaurant-id "finpoint"
                   :user-id "238472"}]

  ;;
  ;; Core Escalation System - Kafka topic input requests
  ;;
  [{:action :start
    :process-id "finpoint"
    :descriptors [{:delay-in-seconds 10
                     :channel :console
                     :params {:message "First dummy notification to console."}}
                    {:delay-in-seconds 15
                     :channel :console
                     :params {:message "Second dummy notification to console."}}
                    {:delay-in-seconds 300
                     :channel "email"
                     :params {:to ["orders@example.com"]
                              :subject "You have unconfirmed new orders in RoomOrders."
                              :body "Visit https://roomorders.com."}}
                    {:delay-in-seconds 600
                     :channel "phone"
                     :params {:phone-number "38599000001"
                              :message "new-order-unconfirmed"}}]}
   {:action :stop
    :process-id "finpoint"}]

  ;;
  ;; Alarm repository entry (in MongoDB)
  ;;
  {:id "finpoint"
   :at "2018-11-28T12:10:00Z"
   :data [:descriptors [{:id "ec42d337-97bf-4956-acf4-3e2b67934b9e"
                         :at "2018-11-28T12:10:00Z"
                         :channel :console
                         :params {:message "First dummy notification to console."}}
                        {:id "aafeef27-d5b4-441e-bb09-c7c2930c449f"
                         :at "2018-11-28T12:10:00Z"
                         :channel :console
                         :params {:message "Second dummy notification to console."}}
                        {:id "97e2e924-fb41-4365-9b78-67b4ff29cca3"
                         :at "2018-11-28T12:10:00Z"
                         :channel :email
                         :params {:to ["orders@example.com"]
                                  :subject "You have unconfirmed new orders in RoomOrders."
                                  :body "Visit https://roomorders.com."}}
                        {:id "bfd2bfa2-e7b1-4896-b909-fcabb22c62dc"
                         :at "2018-11-28T12:10:00Z"
                         :channel :phone
                         :params {:phone-number "38599000001"
                                  :message "new-order-unconfirmed"}}]]}

  ;;
  ;; Console Notifier
  ;;
  {:id "ec42d337-97bf-4956-acf4-3e2b67934b9e"
   :at "2018-11-28T12:00:12Z"
   :message "First dummy notification to console."}

  ;;
  ;; Email Notifier Kafka topic imesc.email-requests
  ;;
  {:id "1cc83662-cfcc-4e3f-a8a8-7a1ee06a6f34"
   :at "2018-11-28T12:10:00Z"
   :to ["orders@example.com"]
   :subject "You have unconfirmed new orders in RoomOrders."
   :body "Visit https://roomorders.com."}

  ;;
  ;; Email Notifier Kafka topic imesc.email-results
  ;;
  {:id "1cc83662-cfcc-4e3f-a8a8-7a1ee06a6f34"
   :status :success}

  ;;
  ;; Phone Call Notifier topic imesc.phone-call-requests
  ;;
  {:id "a84dbb4e-2e32-4776-8cf0-110436d2f9e5"
   :at "2018-11-28T12:10:00Z"
   :phone-number "38599000001"
   :message "new-order-unconfirmed"}

  ;;
  ;; Phone Call Notifier topic imesc.phone-call-results
  ;;
  {:id "a84dbb4e-2e32-4776-8cf0-110436d2f9e5"
   :status :failed
   :reason "No answer."}

  )
