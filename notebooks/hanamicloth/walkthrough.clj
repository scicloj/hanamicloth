;; # Walkthrough

;; In this walkthrough, we will demonstrate its main functionality.

;; ## Setup

;; Here we require Hanamicloth's main API namepace
;; as well as those of [Hanami](https://github.com/jsa-aerial/hanami)
;; and [Tablecloth](https://scicloj.github.io/tablecloth/),
;; and also [Kindly](https://scicloj.github.io/kindly-noted/)
;; (which allows us to specify how values should be visualized).

(ns hanamicloth.walkthrough
  (:require [scicloj.hanamicloth.v1.api :as hanami]
            [aerial.hanami.templates :as ht]
            [tablecloth.api :as tc]
            [scicloj.kindly.v4.kind :as kind]))


;; ## Some datasets

;; In this walkthrough, we will used the following datasets from [RDatasets](https://vincentarelbundock.github.io/Rdatasets/articles/data.html):

;; ### Edgar Anderson's Iris Data

(defonce iris
  (-> "https://vincentarelbundock.github.io/Rdatasets/csv/datasets/iris.csv"
      (tc/dataset {:key-fn keyword})
      (tc/rename-columns {:Sepal.Length :sepal-length
                          :Sepal.Width :sepal-width
                          :Petal.Length :petal-length
                          :Petal.Width :petal-width
                          :Species :species})))

iris

;; ### Motor Trend Car Road Tests

(defonce mtcars
  (-> "https://vincentarelbundock.github.io/Rdatasets/csv/datasets/mtcars.csv"
      (tc/dataset {:key-fn keyword})))

mtcars


;; ### US economic time series

(defonce economics-long
  (-> "https://vincentarelbundock.github.io/Rdatasets/csv/ggplot2/economics_long.csv"
      (tc/dataset {:key-fn keyword})))

economics-long

;; ## Basic usage

;; Let us create a scatter plot from the Iris dataset.
;; We pass a Tablecloth dataset to a Hanamicloth function
;; with a Hanami template.
;; Here we use Hanami's original templates (`ht/chart`)
;; and substitution keys (`:X`, `:Y`, `:MSIZE`).

(-> iris
    (hanami/plot ht/point-chart
                 {:X :sepal-width
                  :Y :sepal-length
                  :MSIZE 200}))

;; The resulting plot is displayed correctly,
;; as it is annotated by Kindly.

(-> iris
    (hanami/plot ht/point-chart
                 {:X :sepal-width
                  :Y :sepal-length
                  :MSIZE 200})
    meta)

;; The value returned by a `hanami/plot` function
;; is a [Vega-Lite](https://vega.github.io/vega-lite/) spec:

(-> iris
    (hanami/plot ht/point-chart
                 {:X :sepal-width
                  :Y :sepal-length
                  :MSIZE 200})
    kind/pprint)

;; By looking at the `:values` key above,
;; you can see that the dataset was implicitly represented as CSV.

;; You can also see that

;; ## Using Hanamicloth templates & defaults

;; Hanamicloth offers its own set of templates and substitution keys.
;; Compared to Hanami's original, it is similar but less sophisticated.
;; Also, it supports the layered grammar which is demonstrated
;; later in this document.

(-> iris
    (hanami/plot hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200}))

(-> iris
    (hanami/plot hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200})
    kind/pprint)

;; You see a slight differnece in the resulting spec:
;; it is defined to be rendered as `:svg` by default.

;; ## Inferring and overriding field types

;; Field [types](https://vega.github.io/vega-lite/docs/type.html) are inferred from the Column type.
;; Here, for example, `:hanami/x` and `:hanami/y` are `:quantitative`, and
;; `:hanami/color` is `:nominal`
;; (and is thus coloured with distinct colours rather than a gradient).

(-> iris
    (hanami/plot hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :color :species
                          :mark-size 200}))

;; On the other hand, in the following example,
;; `:color` is `:quantitative`:

(-> mtcars
    (hanami/plot hanami/point-chart
                 #:hanami{:x :mpg
                          :y :disp
                          :color :cyl
                          :mark-size 200}))

;; This can be overridden:

(-> mtcars
    (hanami/plot hanami/point-chart
                 #:hanami{:x :mpg
                          :y :disp
                          :color :cyl
                          :color-type :nominal
                          :mark-size 200}))

;; ## More examples

(-> mtcars
    (hanami/plot hanami/boxplot-chart
                 #:hanami{:x :cyl
                          :x-type :nominal
                          :y :disp}))

(-> iris
    (hanami/plot hanami/rule-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :x2 :petal-width
                          :y2 :petal-length
                          :mark-opacity 0.5
                          :mark-size 3
                          :color :species}))

;; ## Delayed transformation

;; Instead of the `hanami/plot` function, it is possible to used
;; `hanami/base`:

(-> iris
    (hanami/base hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200}))

;; The result is displayed the same way, but the internal representation
;; delays the Hanami transformation of templates.

;; Let us compare the two:

(-> iris
    (hanami/plot hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :color :species
                          :mark-size 200})
    kind/pprint)

(-> iris
    (hanami/base hanami/point-chart
                 #:hanami{:x :sepal-width
                          :y :sepal-length
                          :color :species
                          :mark-size 200})
    kind/pprint)

;; The structure returned by `hanami/base` is a Hanami template
;; (with [local defaults](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#template-local-defaults)).
;; When it is displayed, it goes through the Hanami transform
;; to recieve the Vega-Lite spec.

;; When we use base, we can keep processing the template in a pipeline
;; of transformations. We will use it soon with layers.

;; ## Adding layers

;; A base plot does not need to have a specified chart.
;; Instead, we may add layers:

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length
                          :mark-size 200})
    hanami/layer-point)

;; The substitution keys can also be specified on the layer level:

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length})
    (hanami/layer-point #:hanami{:mark-size 200}))

;; We can also skip the base and have everything in the layer:
(-> iris
    (hanami/layer-point #:hanami{:x :sepal-width
                                 :y :sepal-length
                                 :mark-size 200}))

;; This allows us to create aesthetic differences between layers:

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/layer-point #:hanami{:mark-size 200}))

;; ## Updating data

;; Using `hanami/update-data`, we may process the dataset
;; during the pipeline, affecting only the layers added further down the pipeline.

;; This functionality is inspired by [ggbuilder](https://github.com/mjskay/ggbuilder)
;; and [metamorph](https://github.com/scicloj/metamorph).

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/update-data tc/random 5)
    (hanami/layer-point #:hanami{:mark-size 200}))

;; You see, we have lots of data for the lines,
;; but only five random points.

;; ## Processing raw vega-lite

;; During a pipeline, we may call `hanami/plot`
;; to apply the Hanami transform and realize the
;; `Vega-Lite` spec.

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/update-data tc/random 5)
    (hanami/layer-point #:hanami{:mark-size 200})
    hanami/plot
    kind/pprint)

;; While this in itself does not affect the display of the plot,
;; it allows us to keep editing it as a Vega-Lite spec.
;; For example, let us change the backgound colour this way:

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    (hanami/layer-line #:hanami{:mark-size 4
                                :mark-color "brown"})
    (hanami/update-data tc/random 5)
    (hanami/layer-point #:hanami{:mark-size 200})
    hanami/plot
    (assoc :background "lightgrey"))

;; ## Smoothing

;; `hanami/layer-smooth` is a layer that applies some statistical
;; processing to the dataset to model it as a smooth shape.
;; It is inspired by ggplot's [geom_smooth](https://ggplot2.tidyverse.org/reference/geom_smooth.html).

;; At the moment, it can only be used to model `:hanami/y` by linear regression.
;; Soon we will add more ways of modelling the data.

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :mark-color "green"
                          :x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:mark-color "orange"}))

;; By default, the regression is computed with only one predictor variable,
;; which is `:hanami/x`.
;; But this can be overriden using the `:predictors` key.
;; We may compute a regression with more than one predictor.

(-> iris
    (hanami/base #:hanami{:x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    (hanami/layer-smooth #:hanami{:predictors [:petal-width
                                               :petal-length]}))

;; ## Grouping

;; The regression computed by `hanami/layer-smooth`
;; is affected by the inferred grouping of the data.

;; For example, here we recieve three regression lines,
;; each for every species.

(-> iris
    (hanami/base #:hanami{:title "dummy"
                          :color :species
                          :x :sepal-width
                          :y :sepal-length})
    hanami/layer-point
    hanami/layer-smooth)

;; This happened because the `:color` field was `:species`,
;; which is of `:nominal` type.

;; But we may override this using the `:group` key.
;; For example, let us avoid grouping:

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

;; Here is a slighly more elaborate example
;; inpired by the London Clojurians [talk](https://www.youtube.com/watch?v=eUFf3-og_-Y)
;; mentioned in the preface.
;; We use the same regression line for the
;; `Past` and `Future` groups.
;; The line is affected only by the past,
;; since in the Future, `:y` is missing.

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

;; Histograms can also be represented as layers
;; with statistical processing:

(-> iris
    (hanami/layer-histogram #:hanami{:x :sepal-width}))

(-> iris
    (hanami/layer-histogram #:hanami{:x :sepal-width
                                     :histogram-nbins 30}))

;; ## Time series

;; Let us plot a time series:

(-> economics-long
    (tc/select-rows #(-> % :variable (= "pop")))
    (hanami/layer-line #:hanami{:x :date
                                :y :value}))

;; You see, the `:date` field was correctly inferred to be
;; of the `:temporal` kind.

(-> economics-long
    (tc/select-rows #(-> % :variable (= "pop")))
    (hanami/layer-line #:hanami{:x :date
                                :y :value})
    hanami/plot
    kind/pprint)
