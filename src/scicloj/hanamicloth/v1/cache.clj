(ns scicloj.hanamicloth.v1.cache
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]))

(def *cache (atom {}))

(defmacro with-clean-cache
  "Evaluate a form,
  resetting the cache before and after the eval."
  [form]
  `(do
     (reset! *cache {})
     (let [result# ~form]
       (reset! *cache {})
       result#)))
