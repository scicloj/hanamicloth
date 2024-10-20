(ns scicloj.tableplot.v1.cache
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

(defn cached-assignment [k values assignment-name]
  (let [assignments (get-in @*cache
                            [::assignments assignment-name])]
    (or (when assignments
          (assignments k))
        (let [v (-> assignments
                    count
                    (rem (count values))
                    values)]
          (swap! *cache
                 assoc-in
                 [::assignments assignment-name k]
                 v)
          v))))

(comment
  (with-clean-cache
    [(cached-assignment :A [1 2 3] :dummy)
     (cached-assignment :B [1 2 3] :dummy)]))
