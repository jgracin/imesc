(ns imesc.integration-test
  (:require [clojure.test :refer :all]
            [imesc.core :as core]
            [imesc.initiator :as initiator]
            [imesc.activator :as activator]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [environ.core :refer [env]]
            [kinsky.client :as client]
            [integrant.core :as integrant]
            [clojure.tools.logging :as logger]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [orchestra.spec.test])
  (:import [java.time Duration ZonedDateTime]))

(defn with-instrumentation [f]
  (orchestra.spec.test/instrument)
  (f))

(defn system []
  (deref (deref (var config/-system))))

(defn polling-wait
  ([success-condition-fn]
   (polling-wait success-condition-fn 10000))
  ([success-condition-fn timeout-ms]
   (polling-wait success-condition-fn timeout-ms 100))
  ([success-condition-fn timeout-ms polling-period-ms]
   (loop [cnt 0]
     (if (success-condition-fn)
       :success
       (if (< cnt (/ timeout-ms polling-period-ms))
         (do (Thread/sleep polling-period-ms) (recur (inc cnt)))
         :failure)))))

(defn shutdown-threads []
  (reset! (:imesc.core/exit-flag (system)) true)
  (polling-wait #(and (realized? (:imesc/activator (system)))
                      (realized? (:imesc/initiator (system))))))

(defn- fast-activator-polling-configuration [config]
  (update-in config [:imesc/activator :poll-millis] (fn [_] 100)))

(defn with-initialized-system [f]
  (config/initialize! (fast-activator-polling-configuration config/config))
  (try (f) (finally (shutdown-threads)))
  (config/halt!))

(use-fixtures :each with-instrumentation with-initialized-system)

(def input-topic "imesc.requests")

(defn dummy-start-request [process-id]
  {:action :start
   :process-id process-id
   :notifications [{:delay-in-seconds 10
                    :channel :console
                    :params {:message "First dummy notification to console."}}
                   {:delay-in-seconds 300
                    :channel :email
                    :params {:to ["orders@example.com"]
                             :subject "You have unconfirmed new orders in RoomOrders."
                             :body "Visit https://roomorders.com."}}
                   {:delay-in-seconds 600
                    :channel :phone
                    :params {:phone-number "38599000001"
                             :message "new-order-unconfirmed"}}]})

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
    (let [process-id (str (java.util.UUID/randomUUID))
          producer (:kafka/producer (system))]
      (logger/info "initiating process id" process-id)
      (client/send! producer input-topic process-id (dummy-start-request process-id))
      (client/flush! producer)
      (logger/info "waiting for process id to appear in db" process-id)
      (is (= :success (polling-wait
                       #(alarm/exists? (:imesc.alarm.mongodb/repository (system)) process-id)))))))

(deftest ^:integration activator
  (testing "successfully processing activations"
    (let [now (ZonedDateTime/now)
          process-id (str (java.util.UUID/randomUUID))
          repo (:imesc.alarm.mongodb/repository (system))
          alarm {:id process-id :at now
                 :notifications [{:id "0" :at now
                                  :channel :console
                                  :params {:message "0"}
                                  :delay-in-seconds 1}
                                 {:id "1" :at (.plusSeconds now 1)
                                  :channel :console
                                  :params {:message "1"}
                                  :delay-in-seconds 1}]}]
      (is (nil? (alarm/exists? repo process-id)))
      (alarm/set-alarm repo alarm)
      (polling-wait
       #(nil? (alarm/exists? repo process-id))))))

(comment
  (reset! (:imesc.core/exit-flag (system)) true)
  (config/halt!)
  (config/initialize!)

  (def p (create-producer))
  (let [process-id (str (java.util.UUID/randomUUID))]
    (client/send! p input-topic process-id (dummy-start-request process-id)))
  (:imesc.core/exit-flag (system))
  (reset! (:imesc.core/exit-flag (system)) true)
  (integrant/halt! @config/-system)
  )
