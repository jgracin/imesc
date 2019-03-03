(ns imesc.activator.console
  (:require [imesc.activator :as activator]
            [clojure.tools.logging :as logger]))

(defmethod activator/activate :console [_ request]
  (logger/info "Activated CONSOLE notifier:" (pr-str request)))

