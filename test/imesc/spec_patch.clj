(ns imesc.spec-patch
  "Monkey-patching clojure.spec.test.alpha/check with :assert-checkable option."
  (:require [clojure.spec.test.alpha]))

(in-ns 'clojure.spec.test.alpha)

(defn- assert-all-checkable [syms opts]
  (let [uncheckable-syms (filter (complement (checkable-syms opts)) syms)]
    (assert (empty? uncheckable-syms)
            (str "check expected checkable syms but these were not: "
                 (str/join ", " uncheckable-syms)))))

(defn check
  "This is a version of check patched by IMESC project.

  Run generative tests for spec conformance on vars named by
sym-or-syms, a symbol or collection of symbols. If sym-or-syms
is not specified, check all checkable vars.

The opts map includes the following optional keys, where stc
aliases clojure.spec.test.check: 

::stc/opts         opts to flow through test.check/quick-check
:gen               map from spec names to generator overrides
:assert-checkable  if true, asserts that syms are checkable

The ::stc/opts include :num-tests in addition to the keys
documented by test.check. Generator overrides are passed to
spec/gen when generating function args.

Returns a lazy sequence of check result maps with the following
keys

:spec       the spec tested
:sym        optional symbol naming the var tested
:failure    optional test failure
::stc/ret   optional value returned by test.check/quick-check

The value for :failure can be any exception. Exceptions thrown by
spec itself will have an ::s/failure value in ex-data:

:check-failed   at least one checked return did not conform
:no-args-spec   no :args spec provided
:no-fn          no fn provided
:no-fspec       no fspec provided
:no-gen         unable to generate :args
:instrument     invalid args detected by instrument
"
  ([] (check (checkable-syms)))
  ([sym-or-syms] (check sym-or-syms nil))
  ([sym-or-syms opts]
     (let [syms (collectionize sym-or-syms)]
       (when (:assert-checkable opts)
         (assert-all-checkable syms opts))
       (->> syms
            (filter (checkable-syms opts))
            (pmap
             #(check-1 (sym->check-map %) opts))))))

