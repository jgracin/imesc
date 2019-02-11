(ns imesc.integration-test
  (:require [clojure.test :refer :all]
            [imesc.core :as core]
            [imesc.initiator :as initiator]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [environ.core :refer [env]]
            [kinsky.client :as client]
            [integrant.core :as integrant]
            [clojure.tools.logging :as logger]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st])
  (:import [java.time Duration]))

(defn with-instrumentation [f]
  (st/instrument)
  (f))

(defn with-initialized-system [f]
  (reset! config/system (core/initialize!))
  (f)
  (integrant/halt! @config/system))

(use-fixtures :once with-instrumentation with-initialized-system)

(defn dummy-start-request [process-id]
  {:action :start
   :process-id process-id
   :notifications [{:delay-in-seconds 10
                    :channel :console
                    :params {:message "First dummy notification to console."}}
                   {:delay-in-seconds 15
                    :channel :console
                    :params {:message "Second dummy notification to console."}}
                   {:delay-in-seconds 300
                    :channel :email
                    :params {:to ["orders@example.com"]
                             :subject "You have unconfirmed new orders in RoomOrders."
                             :body "Visit https://roomorders.com."}}
                   {:delay-in-seconds 600
                    :channel :phone
                    :params {:phone-number "38599000001"
                             :message "new-order-unconfirmed"}}]})

(def input-topic "imesc.requests")

(defn polling-wait
  ([success-condition-fn]
   (polling-wait success-condition-fn 60000))
  ([success-condition-fn timeout-ms]
   (polling-wait success-condition-fn timeout-ms 100))
  ([success-condition-fn timeout-ms polling-period-ms]
   (loop [cnt 0]
     (if (success-condition-fn)
       :success
       (if (< cnt (/ timeout-ms polling-period-ms))
         (do (Thread/sleep polling-period-ms) (recur (inc cnt)))
         :failure)))))

(defn create-producer []
  (client/producer {:bootstrap.servers "localhost:9092"
                    :batch.size 0
                    :acks "all"
                    :max.block.ms 9000
                    :request.timeout.ms 9000}
                   (client/string-serializer)
                   (client/edn-serializer)))

(deftest ^:integration basic-system-test
  (testing "successfully creating a new escalation process"
    (let [should-exit? (atom false)
          main-loop (core/make-kafka-based-main-input-loop (fn [] @should-exit?))
          process-id (str (java.util.UUID/randomUUID))
          producer (create-producer)]
      (client/send! producer input-topic process-id (dummy-start-request process-id))
      (client/flush! producer)
      (let [t (future (main-loop))]
        (try
          (logger/info "waiting for process id to appear in db" process-id)
          (is (= :success (polling-wait
                           #(alarm/exists? (:alarm/repository @config/system) process-id))))
          (finally (reset! should-exit? true)))
        (polling-wait #(future-done? t))))))


(comment
  (def p (create-producer))
  (let [process-id (str (java.util.UUID/randomUUID))]
    (client/send! p input-topic process-id (dummy-start-request process-id)))
  

  )
