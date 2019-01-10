(ns imesc.case-repository.inmemory
  (:require [imesc.case-repository :as case-repository]
            [integrant.core :as ig]))

(defrecord InMemoryCaseRepository [store])

(defn make-repository []
  (InMemoryCaseRepository. (atom #{})))

(extend-type InMemoryCaseRepository
  case-repository/CaseRepository
  (overdue-cases [this now]
    @(:store this))
  (save-case [this c]
    (swap! (:store this) conj c))
  (delete-case [this c]
    (swap! (:store this) (partial remove #{c}))))

(comment
  (let [r (make-repository)]
    (case-repository/save-case r 1)
    (case-repository/overdue-cases r nil))
  )
