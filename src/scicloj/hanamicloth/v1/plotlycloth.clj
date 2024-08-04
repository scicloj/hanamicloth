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
           (cond (tcc/typeof? column :numerical) :quantitative
                 (tcc/typeof? column :datetime) :temporal
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
    :line :lines
    :box nil
    :bar :nil
    :segment nil))

(dag/defn-with-deps submap->mode [=mark]
  (mark->mode =mark))

(defn mark->type [mark]
  (case mark
    :box :box
    :bar :bar
    :segment :line
    :scatter))

(dag/defn-with-deps submap->type [=mark]
  (mark->type =mark))


(def colors-palette
  ;; In R:
  ;; library(RColorBrewer)
  ;; brewer.pal(n = 8, name = "Dark2")
  ["#1B9E77" "#D95F02" "#7570B3" "#E7298A" "#66A61E" "#E6AB02" "#A6761D"
   "#666666"])

(def sizes-palette
  (->> 5
       (iterate (partial * 1.4))
       (take 8)
       (mapv int)))

(def view-base
  {:data :=traces
   :layout {:width :=width
            :height :=height
            :plot_bgcolor :=plot-bgcolor
            :xaxis {:gridcolor :=xaxis-gridcolor}
            :yaxis {:gridcolor :=yaxis-gridcolor}
            :title :=title}})

(def layer-base
  {:dataset :=dataset-after-stat
   :mark :=mark
   :x :=x
   :y :=y
   :x0 :=x0
   :y0 :=y0
   :x1 :=x1
   :y1 :=y1
   :color :=color
   :color-type :=color-type
   :size :=size
   :size-type :=size-type
   :group :=group
   :marker-override {:color :=mark-color
                     :size :=mark-size
                     :width :=mark-width}
   :trace-base {:mode :=mode
                :type :=type
                :opacity :=mark-opacity}
   :name :=name})


(dag/defn-with-deps submap->traces [=layers]
  (->>
   =layers
   (mapcat
    (fn [{:as layer
          :keys [dataset
                 mark
                 x y
                 x0 y0 x1 y1
                 color color-type
                 size size-type
                 marker-override
                 group
                 trace-base]}]
      (let [group-kvs (if group
                        (-> @dataset
                            (tc/group-by group {:result-type :as-map}))
                        {nil @dataset})]
        (-> group-kvs
            (->> (map
                  (fn [[group-key group-dataset]]
                    (let [marker (merge
                                  (when color
                                    (case color-type
                                      :nominal {:color (cache/cached-assignment (color group-key)
                                                                                colors-palette
                                                                                ::color)}
                                      :quantitative {:color (-> group-dataset color vec)}))
                                  (when size
                                    (case size-type
                                      :nominal {:size (cache/cached-assignment (size group-key)
                                                                               sizes-palette
                                                                               ::size)}
                                      :quantitative {:size (-> group-dataset size vec)}))
                                  marker-override)]
                      (merge trace-base
                             {:name (->> [(:name layer)
                                          (some->> group-key
                                                   vals
                                                   (str/join " "))]
                                         (remove nil?)
                                         (str/join " "))}
                             (if (= mark :segment)
                               {:x (vec
                                    (interleave (group-dataset x0)
                                                (group-dataset x1)
                                                (repeat nil)))
                                :y (vec
                                    (interleave (group-dataset y0)
                                                (group-dataset y1)
                                                (repeat nil)))}
                               ;; else
                               {:x (-> x group-dataset vec)
                                :y (-> y group-dataset vec)})
                             (when marker
                               (let [marker-key (case (:mode trace-base)
                                                  :markers :marker
                                                  :lines :line
                                                  nil (case (:type trace-base)
                                                        :box :marker
                                                        :bar :marker
                                                        :line :line))]
                                 {marker-key marker})))))))))))
   vec))





(def standard-defaults
  {:=stat hc/RMV
   :=dataset hc/RMV
   :=dataset-after-stat submap->dataset-after-stat
   :=x :x
   :=x-after-stat :=x
   :=y :y
   :=y-after-stat :=y
   :=x0 hc/RMV
   :=x0-after-stat :=x0
   :=y0 hc/RMV
   :=y0-after-stat :=y0
   :=x1 hc/RMV
   :=x1-after-stat :=x1
   :=y1 hc/RMV
   :=y1-after-stat :=y1
   :=color hc/RMV
   :=size hc/RMV
   :=x-type (submap->field-type :=x)
   :=x-type-after-stat (submap->field-type-after-stat :=x-after-stat)
   :=y-type (submap->field-type :=y)
   :=y-type-after-stat (submap->field-type-after-stat :=y-after-stat)
   :=color-type (submap->field-type :=color)
   :=size-type (submap->field-type :=size)
   :=mark-color hc/RMV
   :=mark-size hc/RMV
   :=mark-width hc/RMV
   :=mark-opacity hc/RMV
   :=background "#ebebeb"
   :=mark :point
   :=mode submap->mode
   :=type submap->type
   :=name hc/RMV
   :=layers []
   :=traces submap->traces
   :=group submap->group
   :=predictors [:=x]
   :=histogram-nbins 10
   :=height hc/RMV
   :=width hc/RMV
   :=title hc/RMV
   :=plot-bgcolor "rgb(229,229,229)"
   :=xaxis-gridcolor "rgb(255,255,255)"
   :=yaxis-gridcolor "rgb(255,255,255)"})




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
                       (update :=layers
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
(def layer-bar (mark-based-layer :bar))
(def layer-boxplot (mark-based-layer :box))
(def layer-segment (mark-based-layer :segment))



(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :=dataset]
                 (fn [wrapped-data]
                   (util/->WrappedValue
                    (apply dataset-fn
                           @wrapped-data
                           submap))))))



(-> {:ABCD (range 1 11)
     :EFGH [5 2.5 5 7.5 5 2.5 7.5 4.5 5.5 5]
     :IJKL [:A :A :A :A :A :B :B :B :B :B]
     :MNOP [:C :D :C :D :C :D :C :D :C :D]}
    tc/dataset
    (base {:=title "IJKLMNOP"})
    (layer-point {:=x :ABCD
                  :=y :EFGH
                  :=color :IJKL
                  :=size :MNOP
                  :=name "QRST1"})
    (layer-line
     {:=title "IJKL MNOP"
      :=x :ABCD
      :=y :ABCD
      :=name "QRST2"
      :=mark-color "magenta"
      :=mark-width 20
      :=mark-opacity 0.2})
    plot)
