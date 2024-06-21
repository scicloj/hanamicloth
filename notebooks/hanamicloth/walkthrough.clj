;; # Walkthrough

(ns hanamicloth.walkthrough
  (:require [scicloj.hanamicloth.v1.api :as hanami]
            [aerial.hanami.templates :as ht]
            [aerial.hanami.common :as hc]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [scicloj.kindly.v4.kind :as kind]
            [scicloj.kindly.v4.api :as kindly]))

:kindly/hide-code
(def md (comp kindly/hide-code kind/md))

;; ## Some datasets

(defonce iris
  (-> "https://vincentarelbundock.github.io/Rdatasets/csv/datasets/iris.csv"
      (tc/dataset {:key-fn keyword})
      (tc/rename-columns {:Sepal.Length :sepal-length
                          :Sepal.Width :sepal-width
                          :Petal.Length :petal-length
                          :Petal.Width :petal-width
                          :Species :species})))

iris

(defonce mtcars
  (-> "https://vincentarelbundock.github.io/Rdatasets/csv/datasets/mtcars.csv"
      (tc/dataset {:key-fn keyword})))

mtcars

;; ## Using the original Hanami templates & defaults

(-> iris
    (hanami/base ht/point-chart
                 {:X :sepal-width
                  :Y :sepal-length
                  :MSIZE 200}))

;; ## Using Hanamicloth templates & defaults

(-> iris
    (hanami/base hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200}))

(-> iris
    (hanami/base hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :color :species
                          :mark-size 200}))

(-> mtcars
    (hanami/plot hanami/point-chart
                 #:hanami{:x :mpg
                          :y :disp
                          :color :cyl
                          :mark-size 200}))

(-> mtcars
    (hanami/plot hanami/point-chart
                 #:hanami{:x :mpg
                          :y :disp
                          :color :cyl
                          :color-type :nominal
                          :mark-size 200}))

(-> mtcars
    (hanami/plot hanami/boxplot-chart
                 #:hanami{:x :cyl
                          :x-type :nominal
                          :y :disp}))

(-> iris
    (hanami/base hanami/rule-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :x2 :petal-width
                          :y2 :petal-length
                          :mark-opacity 0.5
                          :mark-size 3
                          :color :species}))

;; ## Adding layers

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200})
    hanami/layer-point)

(-> iris
    (hanami/layer-point #:hanami{:x :sepal-width
                                 :y :sepal-length
                                 :mark-size 200}))

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length})
    (hanami/layer-point #:hanami{:mark-size 200}))

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/layer-point #:hanami{:mark-size 200}))

;; ## Updating data

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/update-data tc/random 5)
    (hanami/layer-point #:hanami{:mark-size 200}))

;; ## Processing raw vega-lite

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/layer-line {:MSIZE 4
                        :MCOLOR "brown"})
    (hanami/update-data tc/random 20)
    (hanami/layer-point #:hanami{:mark-size 200})
    hanami/plot
    (assoc :background "lightgrey"))

;; ## Smoothing

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:mark-color "orange"}))

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:predictors [:petal-width
                                               :petal-length]}))

;; ## Grouping

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :color :species
                          :x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    hanami/layer-smooth)

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :color :species
                          :group []
                          :x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    hanami/layer-smooth)

;; ## Example: out-of-sample predictions

(-> iris
    (tc/concat (tc/dataset {:sepal-width (range 4 10)
                            :sepal-length (repeat 6 nil)}))
    (tc/map-columns :relative-time
                    [:sepal-length]
                    #(if % "Past" "Future"))
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length
                          :color "relative-time"
                          :group []})
    hanami/layer-point
    hanami/layer-smooth)

;; ## Histograms

#_(-> iris
      (hanami/layer-histogram #:hanami{:x :sepal-width})
      hanami/plot
      (dissoc :encoding))
