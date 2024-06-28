(ns scicloj.hanamicloth.v1.api
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tempfiles.api :as tempfiles]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as ds]
            [fastmath.stats]
            [fastmath.ml.regression :as regression]
            [scicloj.hanamicloth.v1.dag :as dag]
            [clojure.string :as str]))


;; We wrap certain values with this datatype
;; in order to prevent Hanami from trying to walk throug them.
(deftype WrappedValue [value]
  clojure.lang.IDeref
  (deref [this] value))

(defn dataset->csv [dataset]
  (when dataset
    (let [{:keys [path _]}
          (tempfiles/tempfile! ".csv")]
      (-> dataset
          (ds/write! path))
      (slurp path))))

(def submap->dataset-after-stat
  (dag/fn-with-deps-keys
   [:haclo/dataset :haclo/stat]
   (fn [{:as submap
         :keys [haclo/dataset haclo/stat]}]
     (when-not (tc/dataset? @dataset)
       (throw (ex-info "missing :haclo/dataset"
                       submap)))
     (if stat
       (->WrappedValue
        (@stat submap))
       dataset))))

(dag/defn-with-deps submap->csv [dataset-after-stat]
  (dataset->csv @dataset-after-stat))


(defn submap->field-type [colname-key]
  (let [dataset-key :haclo/dataset]
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
  (let [dataset-key :haclo/dataset-after-stat
        colname-key-before-stat (-> colname-key
                                    name
                                    (str/replace #"-after-stat" "")
                                    (->> (keyword "haclo")))
        colname-key-type-before-stat (-> colname-key-before-stat
                                         name
                                         (str "-type")
                                         (->> (keyword "haclo")))]
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

(dag/defn-with-deps submap->group [color color-type size size-type]
  (concat (when (= color-type :nominal)
            [color])
          (when (= size-type :nominal)
            [size])))

(dag/defn-with-deps submap->group [color color-type size size-type]
  (concat (when (= color-type :nominal)
            [color])
          (when (= size-type :nominal)
            [size])))

(def encoding-base
  {:color {:field :haclo/color
           :type :haclo/color-type}
   :size {:field :haclo/size
          :type :haclo/size-type}})

(def xy-encoding
  (assoc encoding-base
         :x {:field :haclo/x-after-stat
             :type :haclo/x-type-after-stat
             :title :haclo/x-title
             :bin :haclo/x-bin}
         :y {:field :haclo/y-after-stat
             :type :haclo/y-type-after-stat
             :title :haclo/y-title
             :bin :haclo/y-bin}
         :x2 :haclo/x2-encoding
         :y2 :haclo/y2-encoding))

(def standard-defaults
  {;; defaults for original Hanami templates
   :VALDATA :haclo/csv-data
   :DFMT {:type "csv"}

   ;; defaults for hanamicloth templates
   :haclo/stat hc/RMV
   :haclo/dataset hc/RMV
   :haclo/dataset-after-stat submap->dataset-after-stat
   :haclo/csv-data submap->csv
   :haclo/data {:values :haclo/csv-data
                :format {:type "csv"}}
   :haclo/opacity hc/RMV
   :haclo/row hc/RMV
   :haclo/column hc/RMV
   :haclo/x :x
   :haclo/x-after-stat :haclo/x
   :haclo/y :y
   :haclo/y-after-stat :haclo/y
   :haclo/x2 hc/RMV
   :haclo/x2-after-stat :haclo/x2
   :haclo/y2 hc/RMV
   :haclo/y2-after-stat :haclo/y2
   :haclo/color hc/RMV
   :haclo/size hc/RMV
   :haclo/x-type (submap->field-type :haclo/x)
   :haclo/x-type-after-stat (submap->field-type-after-stat :haclo/x-after-stat)
   :haclo/y-type (submap->field-type :haclo/y)
   :haclo/y-type-after-stat (submap->field-type-after-stat :haclo/y-after-stat)
   :haclo/x-title hc/RMV
   :haclo/y-title hc/RMV
   :haclo/x-bin hc/RMV
   :haclo/y-bin hc/RMV
   :haclo/x2-encoding (dag/fn-with-deps [x2-after-stat
                                         x-type-after-stat]
                        (if x2-after-stat
                          (-> xy-encoding
                              :x
                              (assoc :field x2-after-stat
                                     :type x-type-after-stat))
                          hc/RMV))
   :haclo/y2-encoding (dag/fn-with-deps [y2-after-stat
                                         y-type-after-stat]
                        (if y2-after-stat
                          (-> xy-encoding
                              :y
                              (assoc :field y2-after-stat
                                     :type y-type-after-stat))
                          hc/RMV))
   :haclo/color-type (submap->field-type :haclo/color)
   :haclo/size-type (submap->field-type :haclo/size)
   :haclo/renderer :svg
   :haclo/usermeta {:embedOptions {:renderer :haclo/renderer}}
   :haclo/title hc/RMV
   :haclo/encoding xy-encoding
   :haclo/height 300
   :haclo/width 400
   :haclo/background "floralwhite"
   :haclo/mark "circle"
   :haclo/mark-color hc/RMV
   :haclo/mark-size hc/RMV
   :haclo/mark-opacity hc/RMV
   :haclo/mark-tooltip true
   :haclo/layer []
   :haclo/group submap->group
   :haclo/predictors [:haclo/x]
   :haclo/histogram-nbins 10})


(def view-base
  {:usermeta :haclo/usermeta
   :title :haclo/title
   :height :haclo/height
   :width :haclo/width
   :background :haclo/background
   :data :haclo/data
   :encoding :haclo/encoding
   :layer :haclo/layer})

(def mark-base
  {:type :haclo/mark,
   :color :haclo/mark-color
   :size :haclo/mark-size
   :opacity :haclo/mark-opacity
   :tooltip :haclo/mark-tooltip})

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
  {:haclo/dataset (->WrappedValue dataset)})

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
                       (update :haclo/layer
                               (comp vec conj)
                               (assoc template
                                      :data :haclo/data
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
            {:mark mark-base
             :encoding :haclo/encoding}
            (merge {:haclo/mark mark}
                   submap)))))

(def layer-point (mark-based-layer "circle"))
(def layer-line (mark-based-layer "line"))
(def layer-bar (mark-based-layer "bar"))
(def layer-area (mark-based-layer "area"))

(dag/defn-with-deps smooth-stat
  [dataset y predictors group]
  (when-not (@dataset y)
    (throw (ex-info "missing y column"
                    {:missing-column-name y})))
  (->> predictors
       (run! (fn [p]
               (when-not (@dataset p)
                 (throw (ex-info "missing predictor column"
                                 {:predictors predictors
                                  :missing-column-name p}))))))
  (->> group
       (run! (fn [g]
               (when-not (@dataset g)
                 (throw (ex-info "missing group column"
                                 {:group group
                                  :missing-column-name g}))))))
  (let [predictions-fn (fn [ds]
                         (let [nonmissing-y (-> ds
                                                (tc/drop-missing [y]))
                               model (regression/glm (-> nonmissing-y
                                                         (get y))
                                                     (-> nonmissing-y
                                                         (tc/select-columns predictors)
                                                         tc/rows))]
                           (-> ds
                               (tc/select-columns predictors)
                               tc/rows
                               (->> (map (partial regression/predict model))))))]
    (if group
      (-> @dataset
          (tc/group-by group)
          (tc/add-or-replace-column y predictions-fn)
          tc/ungroup)
      (-> @dataset
          (tc/add-or-replace-column y predictions-fn)))))

(defn layer-smooth
  ([context]
   (layer-smooth context {}))
  ([context submap]
   (layer context
          {:mark mark-base
           :encoding :haclo/encoding}
          (merge {:haclo/stat (->WrappedValue smooth-stat)
                  :haclo/mark :line}
                 submap))))



(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :haclo/dataset]
                 (fn [wrapped-data]
                   (->WrappedValue
                    (apply dataset-fn
                           @wrapped-data
                           submap))))))


(dag/defn-with-deps histogram-stat
  [dataset x histogram-nbins]
  (when-not (@dataset x)
    (throw (ex-info "missing x column"
                    {:missing-column-name x})))
  (let [{:keys [bins max step]} (-> @dataset
                                    (get x)
                                    (fastmath.stats/histogram
                                     histogram-nbins))
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
           :encoding :haclo/encoding}
          (merge {:haclo/stat (->WrappedValue histogram-stat)
                  :haclo/mark :bar
                  :haclo/x-after-stat :left
                  :haclo/x2-after-stat :right
                  :haclo/y-after-stat :count
                  :haclo/x-title :haclo/x
                  :haclo/x-bin {:binned true}}
                 submap))))
