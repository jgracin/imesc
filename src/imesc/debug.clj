(ns imesc.debug
  (:require [clojure.core.async :as async]
            [imesc.case-repository :as case-repository]
            [imesc.config :as config]
            [integrant.core :as ig]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as resp]
            [clojurewerkz.quartzite.scheduler :as scheduler]
            [clojurewerkz.quartzite.jobs :as jobs :refer [defjob]]
            [clojurewerkz.quartzite.triggers :as triggers]
            [clojurewerkz.quartzite.schedule.simple :as simple]
            [manifold.deferred]
            [manifold.stream]
            [imesc.config :as config]))

(comment
  "General logic"
  (let [repository (:case-repository @config/system)
        request-source (:request-source @config/system)]
    (doseq [request (request-stream request-source)]
      (if (valid? request)
        (when-not (alarm-repository/save repository (activator/make-alarm request))
          (logger/error "unable to save"))
        (logger/error "invalid request"))))
  )

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
  "Manifold"
  (def s (manifold.stream/stream))
  (manifold.stream/put! s (str (java.util.UUID/randomUUID)))
  (manifold.stream/take! s)
  (def s2 (manifold.stream/periodically 3000 (fn [] (println "Tick") (str  (java.util.UUID/randomUUID)))))
  (def result (manifold.stream/take! s2))
  (manifold.deferred/realized? result)
  (println @result)
  (manifold.stream/close! s2)
  (def running? (atom true))
  (reset! running? false)
  (reset! running? true)
  (def input-stream (manifold.stream/stream 2))
  (manifold.stream/map (fn [v] (println v) v) input-stream)
  (future
    (loop []
      (let [msg (manifold.stream/take! input-stream)]
        (println "taking...")
        (println @msg)
        (Thread/sleep 1000)
        (println "finished"))
      (when @running?
        (recur))))
  (manifold.stream/put! input-stream (str (java.util.UUID/randomUUID)))
  )

(comment
  "core.async"

  (def stdout-lock (Object.))
  (def c1 (async/chan (async/dropping-buffer 2)))
  (def running? (atom true))
  (reset! running? false)
  (reset! running? true)
  (let [lock (Object.)]
    (defn output [& args]
      (locking lock (apply println args))))
  (async/go-loop []
    (output "taking...")
    (output "v:" (async/<! c1))
    (output "finished")
    (Thread/sleep 1000)
    (when @running?
      (recur)))
  (def f1 (future
            (let [start-time (System/currentTimeMillis)]
              (output "IN F")
              (async/>!! c1 (java.util.UUID/randomUUID))
              (output "FINISHED F in" (str (- (System/currentTimeMillis) start-time)) "ms"))))
  (realized? f1)
)


(comment
  "integrant configuration testing"
  (def test-system (-> config/config
                       (remove-components [:imesc/case-repository])
                       (merge {:imesc/in-memory-case-repository {:max-size 5}})
                       ig/prep
                       ig/init))

  (ig/halt! test-system)
  (let [s (make-in-memory-case-repository)]
    (save-case s {:t 1})
    (save-case s {:t 2})
    #_(delete-case s {:t 1})
    s)

  (def c1 (async/chan 10))
  (def c (atom 0))
  (def ec (atom true))
  (async/go-loop [cnt 0]
    (try
      (async/>! c1 cnt)
      (Thread/sleep 1000)
      (println (async/<! c1))
      (reset! c cnt)
      (catch Exception e (println "Exception:" (.getMessage e))))
    (when @ec (recur (inc cnt))))
  (async/go
    (println (async/<! c1)))
  (async/>!! c1 1000)
  (reset! ec false)

  (defn index-handler [{:keys [session] :as request}]
    (let [new-count (inc (:count session 0))
          new-session (assoc session :count new-count)]
      (-> (resp/response (str "Homepage: count=" new-count))
          (assoc :session new-session))))

  (defn case-information-handler [{:keys [route-params]}]
    (resp/response (str "You're getting case: " (:id route-params))))
  )

(comment
  "Services, messages, APIs"
  ;;
  ;; RoomOrders Escalation Adapter services
  ;;
  ["PUT /restaurant/finpoint/configuration"
   {:notifications [{:delay 10
                     :channel :debug-console
                     :params {:message "First dummy notification to console."}}
                    {:delay 15
                     :channel :debug-console
                     :params {:message "Second dummy notification to console."}}
                    {:delay 300
                     :channel :email
                     :params {:to ["orders@example.com"]
                              :subject "You have unconfirmed new orders in RoomOrders."
                              :body "Visit https://roomorders.com."}}
                    {:delay 600
                     :channel :phone
                     :params {:number "38599000001"
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
    :notifications [{:delay-in-seconds 10
                     :channel :debug-console
                     :params {:message "First dummy notification to console."}}
                    {:delay-in-seconds 15
                     :channel :debug-console
                     :params {:message "Second dummy notification to console."}}
                    {:delay-in-seconds 300
                     :channel :email
                     :params {:to ["orders@example.com"]
                              :subject "You have unconfirmed new orders in RoomOrders."
                              :body "Visit https://roomorders.com."}}
                    {:delay-in-seconds 600
                     :channel :phone
                     :params {:number "38599000001"
                              :message "new-order-unconfirmed"}}]}
   {:action :stop
    :process-id "finpoint"}]

  ;;
  ;; Alarm repository entry (in MongoDB)
  ;;
  {:id "finpoint"
   :at "2018-11-28T12:10:00Z"
   :data [:notifications [{:id "ec42d337-97bf-4956-acf4-3e2b67934b9e"
                           :due-by "2018-11-28T12:10:00Z"
                           :channel :debug-console
                           :params {:message "First dummy notification to console."}}
                          {:id "aafeef27-d5b4-441e-bb09-c7c2930c449f"
                           :due-by "2018-11-28T12:10:00Z"
                           :channel :debug-console
                           :params {:message "Second dummy notification to console."}}
                          {:id "97e2e924-fb41-4365-9b78-67b4ff29cca3"
                           :due-by "2018-11-28T12:10:00Z"
                           :channel :email
                           :params {:to ["orders@example.com"]
                                    :subject "You have unconfirmed new orders in RoomOrders."
                                    :body "Visit https://roomorders.com."}}
                          {:id "bfd2bfa2-e7b1-4896-b909-fcabb22c62dc"
                           :due-by "2018-11-28T12:10:00Z"
                           :channel :phone
                           :params {:number "38599000001"
                                    :message "new-order-unconfirmed"}}]]}

  ;;
  ;; Console Notifier
  ;;
  {:request-id "ec42d337-97bf-4956-acf4-3e2b67934b9e"
   :time "2018-11-28T12:00:12Z"
   :message "First dummy notification to console."}

  ;;
  ;; Email Notifier Kafka topic imesc.email-requests
  ;;
  {:request-id "1cc83662-cfcc-4e3f-a8a8-7a1ee06a6f34"
   :time "2018-11-28T12:10:00Z"
   :to ["orders@example.com"]
   :subject "You have unconfirmed new orders in RoomOrders."
   :body "Visit https://roomorders.com."}

  ;;
  ;; Email Notifier Kafka topic imesc.email-results
  ;;
  {:request-id "1cc83662-cfcc-4e3f-a8a8-7a1ee06a6f34"
   :status "SUCCESS"}

  ;;
  ;; Phone Call Notifier topic imesc.phone-call-requests
  ;;
  {:request-id "a84dbb4e-2e32-4776-8cf0-110436d2f9e5"
   :time"2018-11-28T12:10:00Z"
   :number "38599000001"
   :message "new-order-unconfirmed"}

  ;;
  ;; Phone Call Notifier topic imesc.phone-call-results
  ;;
  {:request-id "a84dbb4e-2e32-4776-8cf0-110436d2f9e5"
   :status "FAILED"                     ; SUCCESS or FAILED
   :reason "No answer."}

  )
