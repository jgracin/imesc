(ns imesc.activator.email
  (:require [clojure.tools.logging :as logger]
            [imesc.config :as config]
            [imesc.activator :as activator]))

(defmethod activator/activate :email [request]
  (logger/info "Activating email notifier" request)
  (:kafka/producer (config/system)))

