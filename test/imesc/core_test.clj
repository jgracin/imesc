(ns imesc.core-test
  (:require [imesc.spec-patch]
            [imesc.initiator :as initiator]
            [imesc.activator :as activator]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.alpha :as s]))

(def num-tests 100)

(deftest test-properties
  (testing "test function properties"
    (let [c (fn [sym]
              (let [result (first (stest/check
                                   sym
                                   {:clojure.spec.test.check/opts {:num-tests num-tests}
                                    :assert-checkable true}))]
                (if (:failure result)
                  (throw (:failure result))
                  true)))]
      (is (c `initiator/alarm-entry))
      (is (c `initiator/next-action))
      (is (c `activator/descriptor->request)))))


