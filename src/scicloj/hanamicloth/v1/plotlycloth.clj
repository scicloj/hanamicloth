(ns scicloj.hanamicloth.v1.plotlycloth
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as ds]
            [fastmath.stats]
            [fastmath.ml.regression :as regression]
            [scicloj.hanamicloth.v1.dag :as dag]
            [clojure.string :as str]
            [scicloj.hanamicloth.v1.util :as util]
            [scicloj.hanamicloth.v1.cache :as cache]))

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
                 :else :nominal))
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
               (cond (tcc/typeof? column :numerical) :quantitative
                     (tcc/typeof? column :datetime) :temporal
                     :else :nominal)))
         hc/RMV)))))


(defn select-column [dataset column-selector]
  (-> dataset
      (tc/select-columns column-selector)
      vals
      first))


(defn submap->data [column-selector-key]
  (dag/fn-with-deps-keys
   [column-selector-key :=dataset]
   (fn [submap]
     (if-let [column-selector (submap
                               column-selector-key)]
       (do (-> submap
               (get :=dataset)
               deref)
           (or (-> submap
                   (get :=dataset)
                   deref
                   (select-column column-selector)
                   vec)
               hc/RMV))))))


(dag/defn-with-deps submap->group [=color =color-type =size =size-type]
  (concat (when (= =color-type :nominal)
            [=color])
          (when (= =size-type :nominal)
            [=size])))

(defn mark->mode [mark]
  (case mark
    :point :markers
    :line :lines))

(dag/defn-with-deps submap->mode [=mark]
  (mark->mode =mark))


(def colors-palette
  ;; In R:
  ;; library(RColorBrewer)
  ;; brewer.pal(n = 8, name = "Dark2")
  ["#1B9E77" "#D95F02" "#7570B3" "#E7298A" "#66A61E" "#E6AB02" "#A6761D"
   "#666666"])

(dag/defn-with-deps submap->marker-color [=color-type =data-color]
  (when =data-color
    (case =color-type
      :nominal (mapv #(cache/cached-assignment % colors-palette ::color)
                     =data-color))))

(def sizes-palette
  (->> 1
       (iterate (partial * 2))
       (take 8)
       vec))

(dag/defn-with-deps submap->marker-size [=size-type =data-size]
  (when =data-size
    (case =size-type
      :nominal (mapv #(cache/cached-assignment % sizes-palette ::size)
                     =data-size))))

(def standard-defaults
  {:=stat hc/RMV
   :=dataset hc/RMV
   :=dataset-after-stat submap->dataset-after-stat
   :=x :x
   :=x-after-stat :=x
   :=y :y
   :=y-after-stat :=y
   :=color hc/RMV
   :=size hc/RMV
   :=x-type (submap->field-type :=x)
   :=x-type-after-stat (submap->field-type-after-stat :=x-after-stat)
   :=y-type (submap->field-type :=y)
   :=y-type-after-stat (submap->field-type-after-stat :=y-after-stat)
   :=color-type (submap->field-type :=color)
   :=size-type (submap->field-type :=size)
   :=data-x-after-stat (submap->data :=x-after-stat)
   :=data-y-after-stat (submap->data :=y-after-stat)
   :=data-color (submap->data :=color)
   :=data-size (submap->data :=size)
   :=marker-color submap->marker-color
   :=marker-size submap->marker-size
   :=background "#ebebeb"
   :=type :scatter
   :=mark :point
   :=mode submap->mode
   :=name hc/RMV
   :=traces []
   :=group submap->group
   :=predictors [:=x]
   :=histogram-nbins 10
   :=height 300
   :=width 400
   :=title hc/RMV
   :=plot-bgcolor "rgb(229,229,229)"
   :=xaxis-gridcolor "rgb(255,255,255)"
   :=yaxis-gridcolor "rgb(255,255,255)"})



(def view-base
  {:data :=traces
   :layout {:width :=width
            :plot_bgcolor :=plot-bgcolor
            :xaxis {:gridcolor :=xaxis-gridcolor}
            :yaxis {:gridcolor :=yaxis-gridcolor}
            :title :=title}})

(def layer-base
  {:x :=data-x-after-stat
   :y :=data-y-after-stat
   :marker {:color :=marker-color
            :size :=marker-size}
   :mode :=mode
   :name :=name
   :type :=type})

(defn plotly-xform [template]
  (cache/with-clean-cache
    (-> template
        hc/xform
        kind/plotly
        (dissoc :kindly/f))))

(defn base
  ;;
  ([dataset-or-template]
   (base dataset-or-template {}))
  ;;
  ([dataset-or-template submap]
   (if (tc/dataset? dataset-or-template)
     ;; a dataest
     (base dataset-or-template
           view-base
           submap)
     ;; a template
     (-> dataset-or-template
         (update ::ht/defaults merge submap)
         (assoc :kindly/f #'plotly-xform)
         kind/fn)))
  ;;
  ([dataset template submap]
   (-> template
       (update ::ht/defaults merge
               standard-defaults
               {:=dataset (util/->WrappedValue dataset)})
       (base submap))))


(defn plot [& template]
  (->> template
       (apply base)
       plotly-xform))


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
                       (update :=traces
                               util/conjv
                               (assoc template
                                      ::ht/defaults (merge
                                                     standard-defaults
                                                     defaults
                                                     submap))))))))))

(defn mark-based-layer [mark]
  (fn f
    ([context]
     (f context {}))
    ([context submap]
     (layer context
            layer-base
            (merge {:=mark mark}
                   submap)))))

(def layer-point (mark-based-layer :point))
(def layer-line (mark-based-layer :line))



(-> {:ABCD (range 1 11)
     :EFGH [5 2.5 5 7.5 5 2.5 7.5 4.5 5.5 5]
     :IJKL (concat (repeat 5 :A)
                   (repeat 5 :B))}
    tc/dataset
    (layer-point {:=title "IJKLMNOP"
                  :=x :ABCD
                  :=y :EFGH
                  :=color :IJKL
                  :=size :IJKL
                  :=name "QRST1"})
    ;; (layer-line
    ;;  {:=title "IJKL MNOP"
    ;;   :=x :ABCD
    ;;   :=y :ABCD
    ;;   :=name "QRST2"})
    plot)
