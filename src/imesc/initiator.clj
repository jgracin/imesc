(ns imesc.initiator
  "Initiator handles incoming requests and initiates escalation processes."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.tools.logging :as logger]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [clojure.set :refer [subset?]]
            [imesc.util :refer [ignoring-exceptions ignoring-exceptions-but-with-sleep]]
            [imesc.config :as config]
            [imesc.alarm :as alarm]
            [imesc.alarm.mongodb]
            [environ.core :refer [env]])
  (:import imesc.alarm.AlarmRepository
           (java.time ZonedDateTime ZoneId Instant)))

(s/def :email/address
  (s/with-gen
    (s/and :common/non-empty-string #(re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$" %))
    (fn [] (gen/fmap (fn [[username subdomain]]
                      (str username "@" subdomain ".com"))
                    (s/gen (s/tuple :common/non-empty-string
                                    :common/non-empty-string))))))
(s/def :email/to                        (s/coll-of :email/address))
(s/def :email/subject                   :common/non-empty-string)
(s/def :email/body                      :common/non-empty-string)
(s/def :notification/message            :common/non-empty-string)
(s/def :notification/channel            #{:console :email :phone})
(s/def :notification/delay-in-seconds   (s/int-in 1 (* 3600 24)))
(s/def :notification/id                 :common/non-empty-string)
(s/def :notifier/params                 (s/or :console-params (s/keys :req-un [:notification/message])
                                              :email-params (s/keys :req-un [:email/to
                                                                             :email/subject
                                                                             :email/body])
                                              :phone-params (s/keys :req-un [:common/phone-number
                                                                             :notification/message])))
(s/def :notification/descriptor         (s/keys :req-un [:notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notifier/params]))
(s/def :notification/descriptors        (s/coll-of :notification/descriptor))
(s/def :imesc/process-id                :common/non-empty-string)
(s/def :imesc/action                    #{:start :stop})
(s/def :imesc/request                   (s/keys :req-un [:imesc/process-id
                                                         :imesc/action]
                                                :opt-un [:notification/descriptors]))
(s/def :alarm/id                        :common/non-empty-string)
(s/def :alarm/at                        :common/zoned-date-time)
(s/def :alarm/descriptor                (s/keys :req-un [:notification/id
                                                         :alarm/at
                                                         :notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notifier/params]))
(s/def :alarm/descriptors               (s/coll-of :alarm/descriptor))
(s/def :imesc/alarm-entry               (s/keys :req-un [:alarm/id
                                                         :alarm/at
                                                         :alarm/descriptors]))

(defn assign-absolute-time [now descriptor]
  (assoc descriptor :at (.plusSeconds now (:delay-in-seconds descriptor))))

(defn assign-id [m]
  (assoc m :id (str (java.util.UUID/randomUUID))))

(defn alarm-entry [id descriptors now]
  (let [alarm-descriptors (->> descriptors
                               (map (partial assign-absolute-time now))
                               (map assign-id)
                               (sort-by :at))]
    {:id id
     :at (-> alarm-descriptors  first :at)
     :descriptors alarm-descriptors}))

(s/fdef alarm-entry
  :args (s/cat :id :alarm/id
               :descriptors (s/coll-of :notification/descriptor :min-count 1)
               :now :common/zoned-date-time)
  :ret :imesc/alarm-entry
  :fn (fn [m] (= (count (-> m :args :descriptors))
                (count (-> m :ret :descriptors)))))

(defn valid? [request]
  (s/valid? :imesc/request request))

(defn next-action [request process-already-exists?]
  (cond
    (and (= :start (:action request))
         (not process-already-exists?))
    :create-new-process

    (= :stop (:action request))
    :cancel-process

    :else
    :ignore-request))

(s/fdef next-action
  :args (s/cat :request :imesc/request :process-exists? boolean?)
  :ret #{:create-new-process :cancel-process :ignore-request})

(defn process-request [^AlarmRepository r request]
  (let [pid (:process-id request)
        process-already-exists? (boolean (alarm/exists? r pid))
        now (java.time.ZonedDateTime/now)]
    (case (next-action request process-already-exists?)
      :create-new-process
      (alarm/insert r (alarm-entry (:process-id request) (:descriptors request) now))

      :cancel-process
      (alarm/delete r pid)

      :ignore-request
      (logger/debug "ignoring request for process" pid "because it does not change state")

      :else
      (logger/error "BUG: unknown scenario"))))

(defn make-main-input-loop
  "Construct the main input loop based on polling.

  The function request-polling-fn is called without arguments and is expected
  to return a sequence of requests which need to be processed.

  The function request-processing-fn is called with single argument begin
  request and its return value is ignored.

  The function exit-condition-fn is called without arguments and is expected to
  return true or false. When true, the loop will finish."
  [exit-condition-fn request-polling-fn request-processing-fn]
  (fn []
    (logger/info "Entering main input loop...")
    (loop []
      (ignoring-exceptions-but-with-sleep
       1000
       (doseq [request (request-polling-fn)]
         (logger/debug "processing" (pr-str request))
         (ignoring-exceptions
          (if-not (valid? request)
            (logger/warn "ignoring invalid request with process-id" (:process-id request))
            (request-processing-fn request)))))
      (when-not (exit-condition-fn) (recur)))
    (logger/info "Main input loop finished.")))
