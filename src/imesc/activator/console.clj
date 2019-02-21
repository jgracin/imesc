(ns imesc.activator.console
  (:require [imesc.activator :as activator]
            [clojure.tools.logging :as logger]))

(defmethod activator/activate :console [request]
  (logger/info "Activated CONSOLE notifier:" request))

