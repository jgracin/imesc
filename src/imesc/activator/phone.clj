(ns imesc.activator.phone
  (:require [imesc.config :as config]
            [imesc.activator :as activator]
            [imesc.activator.kafka :as kafka]
            [clojure.tools.logging :as logger]))

(def partitioning-key :id)

(defmethod activator/activate :phone [_ request]
  (logger/info "Activating phone notifier" (pr-str request))
  )

