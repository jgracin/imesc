(ns imesc.activator.spec
  (:require [clojure.spec.alpha :as s]
           [clojure.spec.gen.alpha :as gen]))

(s/def :activator/notifier-request      (s/or :console :notifier/console-request
                                              :phone :notifier/phone-request
                                              :email :notifier/email-request))
(s/def :activator/adapter-registry (s/map-of :notification/channel ifn?))
