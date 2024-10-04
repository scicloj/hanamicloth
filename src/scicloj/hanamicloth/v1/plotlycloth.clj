(ns scicloj.hanamicloth.v1.plotlycloth
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as ds]
            [tech.v3.dataset.modelling :as dsmod]
            [fastmath.stats]
            [scicloj.metamorph.ml :as ml]
            [scicloj.metamorph.ml.regression]
            [scicloj.metamorph.ml.design-matrix :as design-matrix]
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
    :text :text
    :line :lines
    :box nil
    :bar nil
    :segment :lines))

(dag/defn-with-deps submap->mode [=mark]
  (mark->mode =mark))

(dag/defn-with-deps submap->type [=mark =coordinates]
  (str (case =mark
         :box "box"
         :bar "bar"
         ;; else
         "scatter")
       (case =coordinates
         :polar "polar"
         :geo "geo"
         ;; else
         nil)))


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
   :text :=text
   :inferred-group :=inferred-group
   :group :=group :marker-override {:color :=mark-color
                                    :=marker-size-key :=mark-size}
   :trace-base {:mode :=mode
                :type :=type
                :opacity :=mark-opacity
                :textfont :=textfont}
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
                 text
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
                             {:r (some-> r group-dataset vec)
                              :theta (some-> theta group-dataset vec)}
                             {:text (some-> text group-dataset vec)}
                             ;; else
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
                               (let [marker-key (if (or (-> trace-base :mode (= :lines))
                                                        (-> trace-base :type (= :line)))
                                                  :line
                                                  :marker)]
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
   :=text hc/RMV
   :=textfont hc/RMV
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
   :=model-options {:model-type :fastmath/ols}
   :=histogram-nbins 10
   :=coordinates hc/RMV
   :=height 400
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
(def layer-text (mark-based-layer :text))


(dag/defn-with-deps smooth-stat
  [=dataset =x =y =predictors =group =model-options]
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
  (let [design-matrix-spec [[=y]
                            (->> =predictors
                                 (mapv (fn [k]
                                         [k (list
                                             'identity
                                             (-> k name symbol))])))]
        predictions-fn (fn [ds]
                         (let [model (-> ds
                                         (tc/drop-missing [=y])
                                         (#(apply design-matrix/create-design-matrix
                                                  %
                                                  design-matrix-spec))
                                         (tc/select-columns (cons =y =predictors))
                                         (ml/train =model-options))]
                           (-> ds
                               (#(apply design-matrix/create-design-matrix
                                        %
                                        design-matrix-spec))
                               (ml/predict model)
                               =y)))]
    (if =group
      (-> @=dataset
          (tc/group-by =group)
          (tc/add-column =y predictions-fn)
          tc/ungroup)
      (-> @=dataset
          (tc/add-column =y predictions-fn)))))


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

(defn dataset [dataset]
  (-> dataset
      tc/dataset
      util/->WrappedValue))

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
        tc/dataset
        (tc/add-column :middle #(tcc/*
                                 0.5
                                 (tcc/+ (:left %)
                                        (:right %)))))))

(defn layer-histogram
  ([context]
   (layer-histogram context {}))
  ([context submap]
   (layer context
          layer-base
          (merge {:=stat (util/->WrappedValue histogram-stat)
                  :=mark :bar
                  :=x-after-stat :middle
                  :=y-after-stat :count
                  :=x-title :=x
                  :=y-title "count"
                  :=x-bin {:binned true}}
                 submap))))


(defn dag [template]
  (let [edges (->> template
                   ::ht/defaults
                   (mapcat (fn [[k v]]
                             (if (fn? v)
                               (->> v
                                    meta
                                    :scicloj.hanamicloth.v1.dag/dep-ks
                                    (map #(vector % k)))))))
        nodes (flatten edges)]
    (kind/cytoscape
     {:elements {:nodes (->> nodes
                             (map (fn [k]
                                    {:data {:id k}})))
                 :edges (->> edges
                             (map (fn [[k0 k1]]
                                    {:data {:id (str k0 k1)
                                            :source k0
                                            :target k1}})))}
      :style [{:selector "node"
               :css {:content "data(id)"
                     :text-valign "center"
                     :text-halign "center"}}
              {:selector "parent"
               :css {:text-valign "top"
                     :text-halign "center"}}
              {:selector "edge"
               :css {:curve-style "bezier"
                     :target-arrow-shape "triangle"}}]
      :layout {:name "breadthfirst"
               :padding 5}})))

(defn debug [template k]
  (-> template
      (assoc ::debug k)
      plot
      ::debug))
