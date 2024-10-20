(ns scicloj.tableplot.v1.util
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as ds]
            [fastmath.stats]
            [fastmath.ml.regression :as regression]
            [scicloj.tableplot.v1.dag :as dag]
            [clojure.string :as str]))


;; We wrap certain values with this datatype
;; in order to prevent Hanami from trying to walk throug them.
(deftype WrappedValue [value]
  clojure.lang.IDeref
  (deref [this] value)

  Object
  (hashCode [this]
    (hash value))
  (equals [this other]
    (and (-> other class (= WrappedValue))
         (= value @other))))


(defn dataset->csv
  "Represent a dataset as a CSV string."
  [dataset]
  (when dataset
    (let [w (java.io.StringWriter.)]
      (-> dataset
          (tc/write! w {:file-type :csv}))
      (.toString w))))

(def conjv
  (comp vec conj))
