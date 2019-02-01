(ns imesc.activator
  "Activator periodically scans alarm repository and activates notifiers by
  sending them activation messages."
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.initiator :as initiator]
            [imesc.alarm :as alarm]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen])
  (:import java.time.ZonedDateTime
           imesc.alarm.AlarmRepository))

(s/def :notifier/request
  (s/keys :req-un [:notification/id :notification/at]
          :opt-un [:email/to :email/subject :email/body
                   :notification/message
                   :common/phone-number]))

(defmulti activate
  "Activates notifications through the channel specified in params."
  :channel)

(defmethod activate :console [descriptor]
  (logger/info "Activated console notifier:" descriptor))

(defmethod activate :default [descriptor]
  (logger/error "Invalid notifier channel, not activating! params=" descriptor))

(defmethod activate :phone [descriptor]
  @config/system)

(defn- descriptor->request
  [descriptor]
  (merge (select-keys descriptor [:id :at])
         (:params descriptor)))

(s/fdef descriptor->request
  :args (s/cat :descriptor :alarm/descriptor)
  :ret :notifier/request)

(comment
  (require '[clojure.spec.test.alpha :as stest])
  (stest/check `descriptor->request)
  (gen/sample (s/gen :alarm/descriptor))

  (descriptor->request {:params { :message "HEPs4" },
                        :id "E7OvMBE", :at "1970-01-01T01:00+01:00[Europe/Zagreb]",
                        :delay-in-seconds 21, :channel :console})

  (polling {:period-in-seconds 10}
           #(-> (overdue-alarms)
                (activate (descriptor->request alarm))))

  (alarm/overdue-alarms (:alarm/repository @config/system)
                        (ZonedDateTime/now))
  )
