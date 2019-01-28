(ns imesc.core-test
  (:require [imesc.core :as sut]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]))

(def num-tests 100)

(deftest test-properties
  (testing "test function properties"
    (let [results (stest/check [`sut/alarm-entry
                                `sut/valid?
                                `sut/decide-next-action]
                               {:clojure.spec.test.check/opts {:num-tests num-tests}})]
      (is (not-any? #(contains? % :failure) results)))))
