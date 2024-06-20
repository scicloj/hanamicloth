(ns scicloj.hanacloth.v1.dag
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]))

;; TODO: Make this concurrency safe.
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

(defn xform-k
  "Apply Hanami xform
  to a fetch specific key
  given a substitution map.

  For example:
(xform-k :B
         {:A 9
          :B (fn [{:keys [A]}] (inc A))
          :C (fn [{:keys [B]}] (inc B))})
=> 10
"
  [k submap]
  (-> {:result k}
      (hc/xform submap)
      :result))

(defn cached-xform-k
  "Apply Hanami xform
  to fetch a specific key
  given a substitution map,
  using the cache.

  For example:

  ```clj
  (with-clean-cache
    (dotimes [i 2]
      (prn
       (cached-xform-k :B
                       {:A 9
                        :B (fn [{:keys [A]}]
                             (prn \"computing ...\")
                             (inc A))}))))
  ;; printed output:

  \"computing ...\"
  10
  10
  ```
  "
  [k submap-with-cache]
  (if-let [result (@*cache k)]
    result
    (let [computed-result (xform-k k submap-with-cache)]
      (swap! *cache
             assoc k computed-result)
      computed-result)))

(defn fn-with-deps-keys
  "Given a set of dependency keys and a submap function,
  create a submap function that first makes sure
  that the xform results for these keys are available.

  For example:

  ```clj
  (with-clean-cache
    (-> {:b :B
         :c :C
         ::ht/defaults {:B (fn-with-deps-keys
                            [:A]
                            (fn [{:keys [A]}] (inc A)))
                        :C (fn-with-deps-keys
                            [:B]
                            (fn [{:keys [B]}] (inc B)))}}
        (hc/xform :A 9)))

  => {:b 10 :c 11}
  ```
  "
  [dep-ks f]
  (fn [submap]
    (->> dep-ks
         (map (fn [k]
                [k (cached-xform-k k submap)]))
         (into submap)
         f)))

(defmacro fn-with-deps
  "Shorthand notation for fn-with-deps-impl.

  For example:

  ```clj
  (with-clean-cache
    (-> {:b :B
         :c :C
         ::ht/defaults {:B (fn-with-deps [A] (inc A))
                        :C (fn-with-deps [B] (inc B))}}
        (hc/xform :A 9)))

    => {:b 10 :c 11}
  ```
  "
  [dep-symbols & forms]
  `(fn-with-deps-keys
    ~(mapv keyword dep-symbols)
    (fn [{:keys ~(vec dep-symbols)}]
      ~@forms)))

(defmacro defn-with-deps
  "Defining a function using fn-with-deps-impl.

  For example:

  ```clj
  (defn-with-deps B->C [B] (inc B))
  (defn-with-deps A->B [A] (inc A))

  (with-clean-cache
    (-> {:b :B
         :c :C
         ::ht/defaults {:B A->B
                        :C B->C}}
        (hc/xform :A 9)))

    => {:b 10 :c 11}
  ```
  "
  [fsymbol dep-symbols & forms]
  `(def ~fsymbol
     (fn-with-deps ~dep-symbols
       ~@forms)))
