(ns scicloj.hanamicloth.v1.api
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
            [scicloj.hanamicloth.v1.util :as util]))

(dag/defn-with-deps submap->dataset [=base-dataset =layer-dataset =layer?]
  (if =layer?
    =layer-dataset
    =base-dataset))

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

(dag/defn-with-deps submap->csv [=dataset-after-stat]
  (util/dataset->csv @=dataset-after-stat))

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

(dag/defn-with-deps submap->group [=color =color-type =size =size-type]
  (concat (when (= =color-type :nominal)
            [=color])
          (when (= =size-type :nominal)
            [=size])))

(def encoding-base
  {:color {:field :=color
           :type :=color-type}
   :size {:field :=size
          :type :=size-type}})

(def xy-encoding
  (assoc encoding-base
         :x {:field :=x-after-stat
             :type :=x-type-after-stat
             :title :=x-title
             :bin :=x-bin}
         :y {:field :=y-after-stat
             :type :=y-type-after-stat
             :title :=y-title
             :bin :=y-bin}
         :x2 :=x2-encoding
         :y2 :=y2-encoding))

(def standard-defaults
  {;; defaults for original Hanami templates
   :VALDATA :=csv-data
   :DFMT {:type "csv"}

   ;; defaults for hanamicloth templates
   :=stat hc/RMV
   :=base-dataset hc/RMV
   :=layer-dataset hc/RMV
   :=layer? hc/RMV
   :=dataset submap->dataset
   :=dataset-after-stat submap->dataset-after-stat
   :=csv-data submap->csv
   :=data {:values :=csv-data
           :format {:type "csv"}}
   :=opacity hc/RMV
   :=x :x
   :=x-after-stat :=x
   :=y :y
   :=y-after-stat :=y
   :=x2 hc/RMV
   :=x2-after-stat :=x2
   :=y2 hc/RMV
   :=y2-after-stat :=y2
   :=color hc/RMV
   :=size hc/RMV
   :=x-type (submap->field-type :=x)
   :=x-type-after-stat (submap->field-type-after-stat :=x-after-stat)
   :=y-type (submap->field-type :=y)
   :=y-type-after-stat (submap->field-type-after-stat :=y-after-stat)
   :=x-title hc/RMV
   :=y-title hc/RMV
   :=x-bin hc/RMV
   :=y-bin hc/RMV
   :=x2-encoding (dag/fn-with-deps [=x2-after-stat
                                    =x-type-after-stat]
                   (if =x2-after-stat
                     (-> xy-encoding
                         :x
                         (assoc :field =x2-after-stat
                                :type =x-type-after-stat))
                     hc/RMV))
   :=y2-encoding (dag/fn-with-deps [=y2-after-stat
                                    =y-type-after-stat]
                   (if =y2-after-stat
                     (-> xy-encoding
                         :y
                         (assoc :field =y2-after-stat
                                :type =y-type-after-stat))
                     hc/RMV))
   :=color-type (submap->field-type :=color)
   :=size-type (submap->field-type :=size)
   :=renderer :svg
   :=usermeta {:embedOptions {:renderer :=renderer}}
   :=title hc/RMV
   :=encoding xy-encoding
   :=height 300
   :=width 400
   :=background "floralwhite"
   :=mark "circle"
   :=mark-color hc/RMV
   :=mark-size hc/RMV
   :=mark-opacity hc/RMV
   :=mark-tooltip true
   :=layer []
   :=group submap->group
   :=predictors [:=x]
   :=histogram-nbins 10})


(def view-base
  {:usermeta :=usermeta
   :title :=title
   :height :=height
   :width :=width
   :background :=background
   :data :=data
   :encoding :=encoding
   :layer :=layer})

(def mark-base
  {:type :=mark,
   :color :=mark-color
   :size :=mark-size
   :opacity :=mark-opacity
   :tooltip :=mark-tooltip})

(defn mark-based-chart [mark]
  (assoc view-base
         :mark (merge mark-base {:type mark})))

(def bar-chart (mark-based-chart "bar"))
(def line-chart (mark-based-chart "line"))
(def point-chart (mark-based-chart "circle"))
(def area-chart (mark-based-chart "area"))
(def boxplot-chart (mark-based-chart "boxplot"))
(def rect-chart (mark-based-chart "rect"))
(def rule-chart (mark-based-chart "rule"))


(defn dataset->defaults [dataset]
  (let [w (util/->WrappedValue dataset)]
    {:=base-dataset w
     :=layer-dataset w}))

(defn vega-lite-xform [template]
  (dag/with-clean-cache
    (-> template
        hc/xform
        kind/vega-lite
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
         (assoc :kindly/f #'vega-lite-xform)
         kind/fn)))

  ([dataset template submap]
   (-> template
       (update ::ht/defaults merge
               standard-defaults
               (dataset->defaults dataset))
       (base submap))))

(defn plot [& template]
  (->> template
       (apply base)
       vega-lite-xform))

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
                       (update :=layer
                               util/conjv
                               (assoc template
                                      :data (if (and (= @(:=layer-dataset defaults)
                                                        @(:=base-dataset defaults))
                                                     (not (:=stat defaults)))
                                              hc/RMV
                                              :=data)
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
            {:mark mark-base
             :encoding :=encoding}
            (merge {:=mark mark}
                   submap)))))

(def layer-point (mark-based-layer "circle"))
(def layer-line (mark-based-layer "line"))
(def layer-bar (mark-based-layer "bar"))
(def layer-area (mark-based-layer "area"))

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

(defn layer-smooth
  ([context]
   (layer-smooth context {}))
  ([context submap]
   (layer context
          {:mark mark-base
           :encoding :=encoding}
          (merge {:=stat (util/->WrappedValue smooth-stat)
                  :=mark :line}
                 submap))))



(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :=layer-dataset]
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
          {:mark mark-base
           :encoding :=encoding}
          (merge {:=stat (util/->WrappedValue histogram-stat)
                  :=mark :bar
                  :=x-after-stat :left
                  :=x2-after-stat :right
                  :=y-after-stat :count
                  :=x-title :=x
                  :=x-bin {:binned true}}
                 submap))))

(defn facet [context facet-config]
  (-> context
      (dissoc :encoding :layer)
      (assoc :spec (-> context
                       (select-keys [:encoding :layer]))
             :facet facet-config)))
