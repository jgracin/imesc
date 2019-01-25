(ns imesc.core-test
  (:require [imesc.core :as sut]
            [clojure.test :refer :all]
            [clojure.spec.test.alpha :as stest]))

(def num-tests 20)

(deftest transformation
  (testing "must be able to transform request into alarm entry"
    (let [results (stest/check [`imesc.core/alarm-entry
                                `imesc.core/valid?
                                `imesc.core/decide-next-action]
                               {:clojure.spec.test.check/opts {:num-tests num-tests}})]
      (is (not-any? #(contains? % :failure) results)))))
