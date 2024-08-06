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

(dag/defn-with-deps submap->type [=mark =coordinates]
  (case =mark
    :box :box
    :bar :bar
    :segment :line
    ;; else
    (if (= =coordinates :polar)
      :scatterpolar
      :scatter)))


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
   :layout :=layout})

(dag/defn-with-deps submap->marker-size-key [=mode =type]
  (if (or (= =mode :lines)
          (= =type :line)) :width
      :size))

(def layer-base
  {:dataset :=dataset-after-stat
   :mark :=mark
   :x :=x-after-stat
   :y :=y-after-stat
   :x0 :=x0-after-stat
   :y0 :=y0-after-stat
   :x1 :=x1-after-stat
   :y1 :=y1-after-stat
   :r :=r
   :theta :=theta
   :coordinates :=coordinates
   :x-title :=x-title
   :y-title :=y-title
   :color :=color
   :color-type :=color-type
   :size :=size
   :size-type :=size-type
   :inferred-group :=inferred-group
   :group :=group
   :marker-override {:color :=mark-color
                     :=marker-size-key :=mark-size}
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
                 r theta
                 coordinates
                 color color-type
                 size size-type
                 marker-override
                 inferred-group
                 trace-base]}]
      (let [group-kvs (if inferred-group
                        (-> @dataset
                            (tc/group-by inferred-group {:result-type :as-map}))
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
                             (if (= coordinate))
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


(dag/defn-with-deps submap->layout
  [=width =height =background =title
   =xaxis-gridcolor =yaxis-gridcolor
   =x-after-stat =y-after-stat
   =x-title =y-title
   =layers]
  (let [final-x-title (or (->> =layers
                               (map :x-title)
                               (cons =x-title)
                               (remove nil?)
                               last)
                          (->> =layers
                               (map :x)
                               (cons =x-after-stat)
                               (remove nil?)
                               last))
        final-y-title (or (->> =layers
                               (map :y-title)
                               (cons =y-title)
                               (remove nil?)
                               last)
                          (->> =layers
                               (map :y)
                               (cons =y-after-stat)
                               (remove nil?)
                               last))]
    {:width =width
     :height =height
     :plot_bgcolor =background
     :xaxis {:gridcolor =xaxis-gridcolor
             :title final-x-title}
     :yaxis {:gridcolor =yaxis-gridcolor
             :title final-y-title}
     :title =title}))



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
   :=r hc/RMV
   :=theta hc/RMV
   :=color-type (submap->field-type :=color)
   :=size-type (submap->field-type :=size)
   :=mark-color hc/RMV
   :=mark-size hc/RMV
   :=marker-size-key submap->marker-size-key
   :=mark-opacity hc/RMV
   :=mark :point
   :=mode submap->mode
   :=type submap->type
   :=name hc/RMV
   :=layers []
   :=traces submap->traces
   :=layout submap->layout
   :=inferred-group submap->group
   :=group :=inferred-group
   :=predictors [:=x]
   :=histogram-nbins 10
   :=coordinates hc/RMV
   :=height 500
   :=width 500
   :=x-title hc/RMV
   :=y-title hc/RMV
   :=title hc/RMV
   :=background "rgb(235,235,235)"
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


(dag/defn-with-deps smooth-stat
  [=dataset =y =predictors =group]
  (when-not (@=dataset =y)
    (throw (ex-info "missing =y column"
                    {:missing-column-name =y})))
  (->> =predictors
       (run! (fn [p]
               (when-not (@=dataset p)
                 (throw (ex-info "missing predictor column"
                                 {:predictors =predictors
                                  :missing-column-name p}))))))
  (->> =group
       (run! (fn [g]
               (when-not (@=dataset g)
                 (throw (ex-info "missing =group column"
                                 {:group =group
                                  :missing-column-name g}))))))
  (let [predictions-fn (fn [ds]
                         (let [nonmissing-y (-> ds
                                                (tc/drop-missing [=y]))
                               model (regression/glm (-> nonmissing-y
                                                         (get =y))
                                                     (-> nonmissing-y
                                                         (tc/select-columns =predictors)
                                                         tc/rows))]
                           (-> ds
                               (tc/select-columns =predictors)
                               tc/rows
                               (->> (map (partial regression/predict model))))))]
    (if =group
      (-> @=dataset
          (tc/group-by =group)
          (tc/add-or-replace-column =y predictions-fn)
          tc/ungroup)
      (-> @=dataset
          (tc/add-or-replace-column =y predictions-fn)))))


(defn mark-based-layer [mark]
  (fn f
    ([context]
     (f context {}))
    ([context submap]
     (layer context
            layer-base
            (merge {:=mark mark}
                   submap)))))

(defn layer-smooth
  ([context]
   (layer-smooth context {}))
  ([context submap]
   (layer context
          layer-base
          (merge {:=stat (util/->WrappedValue smooth-stat)
                  :=mark :line}
                 submap))))


(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :=dataset]
                 (fn [wrapped-data]
                   (util/->WrappedValue
                    (apply dataset-fn
                           @wrapped-data
                           submap))))))



(dag/defn-with-deps histogram-stat
  [=dataset =x =histogram-nbins]
  (when-not (@=dataset =x)
    (throw (ex-info "missing =x column"
                    {:missing-column-name =x})))
  (let [{:keys [bins max step]} (-> @=dataset
                                    (get =x)
                                    (fastmath.stats/histogram
                                     =histogram-nbins))
        left (map first bins)]
    (-> {:left left
         :right (concat (rest left)
                        [max])
         :count (map second bins)}
        tc/dataset)))

(defn layer-histogram
  ([context]
   (layer-histogram context {}))
  ([context submap]
   (layer context
          layer-base
          (merge {:=stat (util/->WrappedValue histogram-stat)
                  :=mark :bar
                  :=x0-after-stat :left
                  :=x1-after-stat :right
                  :=y-after-stat :count
                  :=x-title :=x
                  :=y-title "count"
                  :=x-bin {:binned true}}
                 submap))))
