;; # Walkthrough

(ns hanamicloth.walkthrough
  (:require [scicloj.hanamicloth.v1.api :as hanami]
            [scicloj.metamorph.ml.toydata :as toydata]
            [scicloj.metamorph.ml.toydata.ggplot :as toydata.ggplot]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.common :as hc]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.kindly.v4.api :as kindly]))

:kindly/hide-code
(def md (comp kindly/hide-code kind/md))

;; ## Using the original Hanami templates & defaults

(-> (toydata/iris-ds)
    (hanami/base ht/point-chart
                 {:X :sepal_width
                  :Y :sepal_length
                  :MSIZE 200}))

;; ## Using Hanamicloth templates & defaults

(-> (toydata/iris-ds)
    (hanami/base hanami/point-chart
                 #:hanami{:x :sepal_width
                          :y :sepal_length
                          :mark-size 200}))

(-> toydata.ggplot/mpg
    (hanami/plot hanami/boxplot-chart
                 #:hanami{:x :cyl
                          :y :displ}))

;; ## Adding layers

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:x :sepal_width
                          :y :sepal_length
                          :mark-size 200})
    hanami/layer-point)

(-> (toydata/iris-ds)
    (hanami/layer-point #:hanami{:x :sepal_width
                                 :y :sepal_length
                                 :mark-size 200}))

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:x :sepal_width
                          :y :sepal_length})
    (hanami/layer-point #:hanami{:mark-size 200}))

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal_width
                          :y :sepal_length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/layer-point #:hanami{:mark-size 200}))

;; ## Updating data

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal_width
                          :y :sepal_length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/update-data tc/random 5)
    (hanami/layer-point #:hanami{:mark-size 200}))

;; ## Processing raw vega-lite

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal_width
                          :y :sepal_length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/layer-line {:MSIZE 4
                        :MCOLOR "brown"})
    (hanami/update-data tc/random 20)
    (hanami/layer-point #:hanami{:mark-size 200})
    hanami/plot
    (assoc :background "lightgrey"))

;; ## Smoothing

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal_width
                          :y :sepal_length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:mark-color "orange"}))

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:x :sepal_width
                          :y :sepal_length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:predictors [:petal_width
                                               :petal_length]}))

;; ## Grouping

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :color :species
                          :x :sepal_width
                          :y :sepal_length})
    hanami/layer-point
    hanami/layer-smooth)

(-> (toydata/iris-ds)
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :color :species
                          :group []
                          :x :sepal_width
                          :y :sepal_length})
    hanami/layer-point
    hanami/layer-smooth)

;; ## Example: out-of-sample predictions

(-> (toydata/iris-ds)
    (tc/concat (tc/dataset {:sepal_width (range 4 10)
                            :sepal_length (repeat 6 nil)}))
    (tc/map-columns :relative-time
                    [:sepal_length]
                    #(if % "Past" "Future"))
    (hanami/base #:hanami{:x :sepal_width
                          :y :sepal_length
                          :color "relative-time"
                          :group []})
    hanami/layer-point
    hanami/layer-smooth)



;; (-> (toydata/iris-is)
;;     (hanami/plot ht/rule-chart
;;                {:X :sepal-width
;;                 :Y :sepal-length
;;                 :X2 :petal-width
;;                 :Y2 :petal-length
;;                 :OPACITY 0.2
;;                 :SIZE 3
;;                 :COLOR "species"}))
