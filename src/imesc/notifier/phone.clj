(ns imesc.notifier.phone
  (:require [clojure.tools.logging :as logger]
            [imesc.activator :as activator]))

(defmethod activator/activate :phone [request]
  (logger/info "Activating phone notifier" request))

