(ns scicloj.tableplot.v1.echartscloth
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as ds]
            [fastmath.stats]
            [fastmath.ml.regression :as regression]
            [scicloj.tableplot.v1.dag :as dag]
            [clojure.string :as str]
            [scicloj.tableplot.v1.util :as util]))

(def submap->dataset-after-stat
  (dag/fn-with-deps-keys
   [:=dataset :=stat]
   (fn [{:as submap
         :keys [=dataset =stat]}]
     (when-not (tc/dataset? @=dataset)
       (throw (ex-info "missing :=dataset"
                       submap)))
     (if =stat
       (util/->WrappedValue
        (@=stat submap))
       =dataset))))

(defn dataset->echarts-dataset [dataset]
  {:source
   (vec
    (cons (vec (keys dataset))
          (tc/rows dataset)))})

(dag/defn-with-deps submap->echarts-dataset [=dataset-after-stat]
  (dataset->echarts-dataset @=dataset-after-stat))

(defn submap->field-type [colname-key]
  (let [dataset-key :=dataset]
    (dag/fn-with-deps-keys
     [colname-key dataset-key]
     (fn [submap]
       (if-let [colname (submap colname-key)]
         (let [column (-> submap
                          (get dataset-key)
                          deref
                          (get colname))]
           (cond (tcc/typeof? column :numerical) :value
                 (tcc/typeof? column :datetime) :time
                 :else :category))
         hc/RMV)))))

(defn submap->field-type-after-stat [colname-key]
  (let [dataset-key :=dataset-after-stat
        colname-key-before-stat (-> colname-key
                                    name
                                    (str/replace #"-after-stat" "")
                                    keyword)
        colname-key-type-before-stat (-> colname-key-before-stat
                                         name
                                         (str "-type")
                                         keyword)]
    (dag/fn-with-deps-keys
     [colname-key
      colname-key-before-stat
      colname-key-type-before-stat
      dataset-key]
     (fn [submap]
       (if-let [colname (submap colname-key)]
         (let [column (-> submap
                          (get dataset-key)
                          deref
                          (get colname))
               colname-before-stat (submap
                                    colname-key-before-stat)]
           (or (when (= colname colname-before-stat)
                 (submap colname-key-type-before-stat))
               (cond (tcc/typeof? column :numerical) :value
                     (tcc/typeof? column :datetime) :time
                     :else :category)))
         hc/RMV)))))

(defn submap->data [colname-key]
  (let [dataset-key :=dataset-after-stat]
    (dag/fn-with-deps-keys
     [colname-key
      dataset-key]
     (fn [submap]
       (if-let [colname (submap colname-key)]
         (or (-> submap
                 (get dataset-key)
                 deref
                 (get colname)
                 vec)
             hc/RMV))))))

(dag/defn-with-deps submap->group [=color =color-type =size =size-type]
  (concat (when (= =color-type :category)
            [=color])
          (when (= =size-type :category)
            [=size])))

(def encoding-base
  {})

(def xy-encoding
  (assoc encoding-base
         :x :=x-after-stat
         :y :=y-after-stat))

(def standard-defaults
  {:=stat hc/RMV
   :=base-dataset hc/RMV
   :=layer-dataset hc/RMV
   :=layer? hc/RMV
   :=dataset hc/RMV
   :=dataset-after-stat submap->dataset-after-stat
   :=x :x
   :=x-after-stat :=x
   :=y :y
   :=y-after-stat :=y
   :=x-type (submap->field-type :=x)
   :=x-type-after-stat (submap->field-type-after-stat :=x-after-stat)
   :=y-type (submap->field-type :=y)
   :=y-type-after-stat (submap->field-type-after-stat :=y-after-stat)
   :=x-data-after-stat (submap->data :=x-after-stat)
   :=y-data-after-stat (submap->data :=y-after-stat)
   :=encoding xy-encoding
   :=background "floralwhite"
   :=mark "scatter"
   :=x-axis []
   :=y-axis []
   :=echarts-dataset []
   :=series []})

(def view-base
  {:xAxis :=x-axis
   :yAxis :=y-axis
   :dataset :=echarts-dataset
   :series :=series})

(defn echarts-xform [template]
  (dag/with-clean-cache
    (-> template
        hc/xform
        kind/echarts
        (dissoc :kindly/f))))

(defn base
  ([dataset-or-template]
   (base dataset-or-template {}))

  ([dataset-or-template submap]
   (if (tc/dataset? dataset-or-template)
     ;; a dataest
     (base dataset-or-template
           view-base
           submap)
     ;; a template
     (-> dataset-or-template
         (update ::ht/defaults merge submap)
         (assoc :kindly/f #'echarts-xform)
         kind/fn)))

  ([dataset template submap]
   (-> template
       (update ::ht/defaults merge
               standard-defaults)
       (base submap))))

(defn plot [& template]
  (->> template
       (apply base)
       echarts-xform))

(defn layer
  ([context template submap]
   (if (tc/dataset? context)
     (layer (base context {})
            template
            submap)
     ;; else - the context is already a template
     (-> context
         (update ::ht/defaults
                 (fn [defaults]
                   (-> defaults
                       (update :=echarts-dataset
                               util/conjv
                               :=dataset-after-stat)
                       (update :=x-axis
                               util/conjv
                               {:type :=x-type-after-stat})
                       (update :=y-axis
                               util/conjv
                               {:type :=y-type-after-stat})
                       (update :=series
                               util/conjv
                               (assoc template
                                      :dataset :=echarts-dataset
                                      ::ht/defaults (merge
                                                     standard-defaults
                                                     defaults
                                                     {:=layer? true}
                                                     submap))))))))))

(defn mark-based-layer [mark]
  (fn f
    ([context]
     (f context {}))
    ([context submap]
     (layer context
            {:type :=mark
             :encode :=encoding}
            (merge {:=mark mark}
                   submap)))))

(def layer-point (mark-based-layer "scatter"))
(def layer-line (mark-based-layer "line"))
(def layer-bar (mark-based-layer "bar"))

(comment
  (-> {:x ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
       :y [150, 230, 224, 218, 135, 147, 260]}
      tc/dataset
      (base {})
      (layer-point {})
      plot
      (update-in [:series 0]
                 dissoc :dataset)))




(kind/echarts
 {:dataset [{:source [[:a :b]
                      [0 0]
                      [1 1]
                      [2 4]
                      [3 9]
                      [4 16]]}]
  :xAxis [{:type :category}
          {:type :value}]
  :yAxis {}
  :series [{:type :line
            :datasetIndex 0
            :xAxisIndex 0
            :encode {:x :b
                     :y :a}}
           {:type :line
            :datasetIndex 0
            :xAxisIndex 1
            :encode {:x :b
                     :y :a}}]})
