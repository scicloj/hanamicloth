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
            [scicloj.hanamicloth.v1.dag :as dag]))

(defn nonrmv? [v]
  (not= v hc/RMV))

(defn dataset->csv [dataset]
  (when dataset
    (let [{:keys [path _]}
          (tempfiles/tempfile! ".csv")]
      (-> dataset
          (ds/write! path))
      (slurp path))))

(def submap->csv
  (dag/fn-with-deps-keys
   [:hanami/dataset :hanami/stat]
   (fn [{:as submap
         :keys [hanami/dataset hanami/stat]}]
     (dataset->csv
      (if stat
        (@stat submap)
        @dataset)))))

(defn submap->field-type [colname-key]
  (dag/fn-with-deps-keys
   [colname-key :hanami/dataset]
   (fn [{:as submap
         :keys [hanami/dataset]}]
     (if-let [colname (submap colname-key)]
       (let [column (@dataset colname)]
         (cond (tcc/typeof? column :numerical) :quantitative
               (tcc/typeof? column :datetime) :temporal
               :else :nominal))
       hc/RMV))))

(dag/defn-with-deps submap->group [color color-type size size-type]
  (concat (when (= color-type :nominal)
            [color])
          (when (= size-type :nominal)
            [size])))

(def encoding-base
  {:color {:field :hanami/color
           :type :hanami/color-type}
   :size {:field :hanami/size
          :type :hanami/size-type}})

(def xy-encoding
  (assoc encoding-base
         :x {:field :hanami/x
             :type :hanami/x-type
             :title :hanami/x-title
             :bin :hanami/x-bin}
         :y {:field :hanami/y
             :type :hanami/y-type
             :title :hanami/y-title
             :bin :hanami/y-bin}
         :x2 :hanami/x2-encoding
         :y2 :hanami/y2-encoding))

(def standard-defaults
  {;; defaults for original Hanami templates
   :VALDATA :hanami/csv-data
   :DFMT {:type "csv"}
   ;; defaults for hanamicloth templates
   :hanami/csv-data submap->csv
   :hanami/data {:values :hanami/csv-data
                 :format {:type "csv"}}
   :hanami/opacity hc/RMV
   :hanami/row hc/RMV
   :hanami/column hc/RMV
   :hanami/x :x
   :hanami/y :y
   :hanami/x2 hc/RMV
   :hanami/y2 hc/RMV
   :hanami/color hc/RMV
   :hanami/size hc/RMV
   :hanami/x-type (submap->field-type :hanami/x)
   :hanami/y-type (submap->field-type :hanami/y)
   :hanami/x2-type (dag/fn-with-deps [x-type x2]
                     (when x2 x-type))
   :hanami/y2-type (dag/fn-with-deps [y-type y2]
                     (when y2 y-type))
   :hanami/x-title hc/RMV
   :hanami/y-title hc/RMV
   :hanami/x-bin hc/RMV
   :hanami/y-bin hc/RMV
   :hanami/x2-encoding (dag/fn-with-deps [x2 x2-type]
                         (if x2
                           (-> xy-encoding
                               :x
                               (assoc :field x2
                                      :type x2-type))
                           hc/RMV))
   :hanami/y2-encoding (dag/fn-with-deps [y2 y2-type]
                         (if y2
                           (-> xy-encoding
                               :y
                               (assoc :field y2
                                      :type y2-type))
                           hc/RMV))
   :hanami/color-type (submap->field-type :hanami/color)
   :hanami/size-type (submap->field-type :hanami/size)
   :hanami/renderer :svg
   :hanami/usermeta {:embedOptions {:renderer :hanami/renderer}}
   :hanami/title hc/RMV
   :hanami/encoding xy-encoding
   :hanami/height 300
   :hanami/width 400
   :hanami/background "floralwhite"
   :hanami/mark "circle"
   :hanami/mark-color hc/RMV
   :hanami/mark-size hc/RMV
   :hanami/mark-opacity hc/RMV
   :hanami/mark-tooltip true
   :hanami/layer []
   :hanami/group submap->group
   :hanami/stat hc/RMV
   :hanami/predictors [:hanami/x]
   :hanami/histogram-nbins 30})


(def view-base
  {:usermeta :hanami/usermeta
   :title :hanami/title
   :height :hanami/height
   :width :hanami/width
   :background :hanami/background
   :data :hanami/data
   :encoding :hanami/encoding
   :layer :hanami/layer})

(def mark-base
  {:type :hanami/mark,
   :color :hanami/mark-color
   :size :hanami/mark-size
   :opacity :hanami/mark-opacity
   :tooltip :hanami/mark-tooltip})

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

(deftype WrappedValue [value]
  clojure.lang.IDeref
  (deref [this] value))


(defn dataset->defaults [dataset]
  {:hanami/dataset (->WrappedValue dataset)})

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
                       (update :hanami/layer
                               (comp vec conj)
                               (assoc template
                                      :data :hanami/data
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
             :encoding :hanami/encoding}
            (merge {:hanami/mark mark}
                   submap)))))

(def layer-point (mark-based-layer "circle"))
(def layer-line (mark-based-layer "line"))
(def layer-bar (mark-based-layer "bar"))
(def layer-area (mark-based-layer "area"))

(dag/defn-with-deps smooth-stat
  [dataset y predictors group]
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
           :encoding :hanami/encoding}
          (merge {:hanami/stat (->WrappedValue smooth-stat)
                  :hanami/mark :line}
                 submap))))



(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :hanami/dataset]
                 (fn [wrapped-data]
                   (->WrappedValue
                    (apply dataset-fn
                           @wrapped-data
                           submap))))))


;; (dag/defn-with-deps histogram-stat
;;   [dataset x histogram-nbins]
;;   (let [{:keys [bins max step]} (-> @dataset
;;                                     (get x)
;;                                     (fastmath.stats/histogram
;;                                      histogram-nbins))
;;         left (map first bins)]
;;     (-> {:x (map first bins)
;;          :right (concat (rest left)
;;                         [max])
;;          :count (map second bins)}
;;         tc/dataset)))

;; (defn layer-histogram
;;   ([context]
;;    (layer-histogram context {}))
;;   ([context submap]
;;    (layer context
;;           {:mark mark-base
;;            :encoding :hanami/encoding}
;;           (merge {:hanami/stat (->WrappedValue histogram-stat)
;;                   :hanami/mark :bar
;;                   :hanami/x :left
;;                   :hanami/x2 :right
;;                   :hanami/y :count
;;                   :hanami/y-type :quantitative
;;                   :hanami/x-title :hanami/x
;;                   :hanami/x-bin {:binned true}}
;;                  submap))))