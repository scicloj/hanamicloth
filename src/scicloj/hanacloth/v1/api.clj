(ns scicloj.hanacloth.v1.api
  (:require [aerial.hanami.common :as hc]
            [aerial.hanami.templates :as ht]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.tempfiles.api :as tempfiles]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.dataset :as tmd]
            [tech.v3.dataset :as ds]
            [tech.v3.datatype.functional :as fun]
            [scicloj.metamorph.ml :as ml]
            [tech.v3.dataset.modelling :as modelling]
            [fastmath.stats]
            [scicloj.hanacloth.v1.api :as hana]))


(defn dataset->csv [dataset]
  (when dataset
    (let [{:keys [path _]}
          (tempfiles/tempfile! ".csv")]
      (-> dataset
          (ds/write! path))
      (slurp path))))

(defn submap->csv [{:as submap
                    :keys [hana/dataset
                           hana/stat]}]
  (dataset->csv
   (if stat
     (stat submap)
     @dataset)))

(defn submap->vega-lite-type [colname-key]
  (fn [{:as submap
        :keys [hana/dataset]}]
    (let [colname (submap colname-key)]
      (if (= colname hc/RMV)
        hc/RMV
        (let [column (@dataset colname)]
          (cond (tcc/typeof? column :numerical) :quantitative
                (tcc/typeof? column :datetime) :temporal
                :else :nominal))))))

(def encoding-base
  {:color {:field :hana/color
           :type :hana/color-type}
   :size {:field :hana/size
          :type :hana/size-type}})

(def xy-encoding
  (assoc encoding-base
         :x {:field :hana/x
             :type :hana/x-type}
         :y {:field :hana/y
             :type :hana/y-type}))

(def default-extenstions
  {;; defaults for original Hanami templates
   :VALDATA :hana/csv-data
   :DFMT {:type "csv"}
   ;; defaults for hanamicloth templates
   :hana/csv-data submap->csv
   :hana/data {:values :hana/csv-data
               :format {:type "csv"}}
   :hana/opacity hc/RMV
   :hana/row hc/RMV
   :hana/column hc/RMV
   :hana/x :x
   :hana/y :y
   :hana/color hc/RMV
   :hana/size hc/RMV
   :hana/x-type (submap->vega-lite-type :hana/x)
   :hana/y-type (submap->vega-lite-type :hana/y)
   :hana/color-type (submap->vega-lite-type :hana/color)
   :hana/size-type (submap->vega-lite-type :hana/size)
   :hana/renderer :svg
   :hana/usermeta {:embedOptions {:renderer :hana/renderer}}
   :hana/title hc/RMV
   :hana/encoding xy-encoding
   :hana/height 300
   :hana/width 400
   :hana/background "floralwhite"
   :hana/mark "circle"
   :hana/mark-color hc/RMV
   :hana/mark-size hc/RMV
   :hana/mark-tooltip true
   :hana/layer []})


(def view-base
  {:usermeta :hana/usermeta
   :title :hana/title
   :height :hana/height
   :width :hana/width
   :background :hana/background
   :data :hana/data
   :encoding :hana/encoding
   :layer :hana/layer})

(def mark-base
  {:type :hana/mark,
   :color :hana/mark-color
   :size :hana/mark-size
   :tooltip :hana/mark-tooltip})

(defn mark-based-chart [mark]
  (assoc view-base
         :mark (merge mark-base {:type mark})))

(def bar-chart (mark-based-chart "bar"))
(def line-chart (mark-based-chart "line"))
(def point-chart (mark-based-chart "circle"))
(def area-chart (mark-based-chart "area"))

(deftype WrappedValue [value]
  clojure.lang.IDeref
  (deref [this] value))


(defn dataset->defaults [dataset]
  {:hana/dataset (->WrappedValue dataset)})

(defn vega-lite-xform [template]
  (-> template
      hc/xform
      kind/vega-lite
      (dissoc :kindly/f)))

(defn base
  ([dataset-or-template]
   (base dataset-or-template {}))

  ([dataset-or-template submap]
   (if (tc/dataset? dataset-or-template)
     ;; a dataest
     (base dataset-or-template
           ht/view-base
           submap)
     ;; a template
     (-> dataset-or-template
         (update ::ht/defaults merge submap)
         (assoc :kindly/f #'vega-lite-xform)
         kind/fn)))

  ([dataset template submap]
   (-> template
       (update ::ht/defaults merge
               default-extenstions
               (dataset->defaults dataset))
       (base submap))))

(defn plot [& template]
  (->> template
       (apply base)
       vega-lite-xform))

(defn layer
  ([context template subs]
   (if (tc/dataset? context)
     (layer (base context {})
            template
            subs)
     ;; else - the context is already a template
     (-> context
         (update-in [::ht/defaults :hana/layer]
                    (comp vec conj)
                    template
                    (assoc template
                           :data :hana/data
                           ::ht/defaults (merge default-extenstions
                                                subs)))))))

(defn mark-based-layer [mark]
  (fn
    ([context]
     (layer-point context {}))
    ([context submap]
     (layer context
            {:mark mark-base
             :encoding :hana/encoding}
            (merge {:hana/mark mark}
                   submap)))))

(def layer-point (mark-based-layer "circle"))
(def layer-line (mark-based-layer "line"))
(def layer-bar (mark-based-layer "bar"))
(def layer-area (mark-based-layer "area"))

(def smooth-stat
  (fn [{:as submap
        :keys [hana/dataset]}]
    (let [[Y X predictors group] (map submap [:Y :X :predictors :hana/group])
          predictors (or predictors [X])
          predictions-fn (fn [dataset]
                           (let [nonmissing-Y (-> dataset
                                                  (tc/drop-missing [Y]))]
                             (if (-> predictors count (= 1))
                               ;; simple linear regression
                               (let [model (fun/linear-regressor (-> predictors first nonmissing-Y)
                                                                 (nonmissing-Y Y))]
                                 (->> predictors
                                      first
                                      dataset
                                      (map model)))
                               ;; multiple linear regression
                               (let [_ (require 'scicloj.ml.smile.regression)
                                     model (-> nonmissing-Y
                                               (modelling/set-inference-target Y)
                                               (tc/select-columns (cons Y predictors))
                                               (ml/train {:model-type
                                                          :smile.regression/ordinary-least-square}))]
                                 (-> dataset
                                     (tc/drop-columns [Y])
                                     (ml/predict model)
                                     (get Y))))))
          update-data-fn (fn [dataset]
                           (if group
                             (-> dataset
                                 (tc/group-by group)
                                 (tc/add-or-replace-column Y predictions-fn)
                                 tc/ungroup)
                             (-> dataset
                                 (tc/add-or-replace-column Y predictions-fn))))
          new-data (update-data-fn @dataset)]
      new-data)))



(defn layer-smooth
  ([context]
   (layer-smooth context {}))
  ([context submap]
   (layer context
          ht/line-layer
          (assoc submap
                 :hana/stat smooth-stat))))


;; (defn layer-histogram
;;   ([context]
;;    (layer-smooth context {}))
;;   ([context submap]
;;    (layer context
;;           ht/bar-layer
;;           (assoc submap
;;                  :hana/stat histogram-stat))))


;; (defn histogram [dataset column-name {:keys [nbins]}]
;;   (let [{:keys [bins max step]} (-> column-name
;;                                     dataset
;;                                     (fastmath.stats/histogram nbins))
;;         left (map first bins)]
;;     (-> {:left (map first bins)
;;          :right (concat (rest left)
;;                         [max])
;;          :count (map second bins)}
;;         tc/dataset
;;         (hanami/plot ht/bar-chart
;;                      {:X :left
;;                       :X2 :right
;;                       :Y :count})
;;         (assoc-in [:encoding :x :bin] {:binned true
;;                                        :step step})
;;         (assoc-in [:encoding :x :title] column-name))))


(defn update-data [template dataset-fn & submap]
  (-> template
      (update-in [::ht/defaults :hana/dataset]
                 (fn [wrapped-data]
                   (->WrappedValue
                    (apply dataset-fn
                           @wrapped-data
                           submap))))))
