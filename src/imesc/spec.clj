(ns imesc.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as string])
  (:import (java.time ZonedDateTime ZoneId Instant)))

(s/def :common/zoned-date-time
  (let [l #(.getEpochSecond (Instant/parse %))]
    (s/with-gen (partial instance? ZonedDateTime)
      (fn [] (gen/fmap #(.atZone (Instant/ofEpochSecond %)
                                (ZoneId/systemDefault))
                      (s/gen (s/int-in (- (l "1980-01-01T00:00:00.00Z"))
                                       (l "2070-01-01T00:00:00.00Z"))))))))

(s/def :common/non-empty-string
  (s/with-gen
    (s/and string? (complement string/blank?))
    #(gen/not-empty (gen/string-alphanumeric))))

(s/def :common/phone-number :common/non-empty-string)

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
(s/def :notification/notification         (s/keys :req-un [:notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notifier/params]))
(s/def :notification/notifications      (s/coll-of :notification/notification))
(s/def :imesc/process-id                :common/non-empty-string)
(s/def :imesc/action                    #{:start :stop})
(s/def :imesc/request                   (s/keys :req-un [:imesc/process-id
                                                         :imesc/action]
                                                :opt-un [:notification/notifications]))
(s/def :alarm/id                        :common/non-empty-string)
(s/def :alarm/at                        :common/zoned-date-time)
(s/def :alarm/notification              (s/keys :req-un [:notification/id
                                                         :alarm/at
                                                         :notification/delay-in-seconds
                                                         :notification/channel]
                                                :opt-un [:notifier/params]))
(s/def :alarm/notifications             (s/coll-of :alarm/notification))
(s/def :imesc/alarm-db-entry            (s/keys :req-un [:alarm/id
                                                         :alarm/at
                                                         :alarm/notifications]))

(s/def :activator/notifier-request      (s/or :console :notifier/console-request
                                              :phone :notifier/phone-request
                                              :email :notifier/email-request))
