(ns imesc.activator.email
  (:require [imesc.config :as config]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as kafka]
            [clojure.tools.logging :as logger]))

(def partitioning-key :id)

(defmethod activator/activate :email [_ request]
  (logger/info "Activating email notifier" (pr-str request))
  )

