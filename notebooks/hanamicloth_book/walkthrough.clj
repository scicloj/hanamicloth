;; # Walkthrough

;; In this walkthrough, we will demonstrate its main functionality.

;; ## Setup

;; Here we require:

;; * Hanamicloth's main API namepace

;; * [Hanami](https://github.com/jsa-aerial/hanami)

;; * [Tablecloth](https://scicloj.github.io/tablecloth/)

;; * the [datetime namespace](https://cnuernber.github.io/dtype-next/tech.v3.datatype.datetime.html) of [dtype-next](https://github.com/cnuernber/dtype-next),

;; * the [print namespace](https://techascent.github.io/tech.ml.dataset/tech.v3.dataset.print.html) of [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) for customized dataset printing,

;; * [Kindly](https://scicloj.github.io/kindly-noted/) (which allows us to specify how values should be visualized).

;; * the datasets defined in the [Datasets chapter](./hanamicloth.datasets.html).

(ns hanamicloth-book.walkthrough
  (:require [scicloj.hanamicloth.v1.api :as haclo]
            [aerial.hanami.templates :as ht]
            [tablecloth.api :as tc]
            [tech.v3.datatype.datetime :as datetime]
            [tech.v3.dataset.print :as print]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            [scicloj.kindly.v4.api :as kindly]
            [hanamicloth-book.datasets :as datasets]
            [aerial.hanami.common :as hc])
  (:import java.time.LocalDate))

;; ## Basic usage

;; Let us create a scatter plot from the Iris dataset.
;; We pass a Tablecloth dataset to a Hanamicloth function
;; with a Hanamicloth template.

(-> datasets/iris
    (haclo/plot haclo/point-chart
                #:haclo{:x :sepal-width
                        :y :sepal-length
                        :color :species
                        :mark-size 200}))

;; Soon, the Hanamicloth will offer some introductions to the use of
;; Hanami templates and substitution keys.
;; For now, please see the [Hanami documentation](https://github.com/jsa-aerial/hanami).

;; While Hanamicloth allows using the classic Hanami templates and substitution keys,
;; it also offers its own sets of templates, that we just used here.

;; Compared to Hanami's original templates, the ones of Hanamicloth are similar but less sophisticated.
;; Also, they supports a layered grammar which is demonstrated later in this document.

;; (Here is how we can express the same plot with the layered grammar:)

(-> datasets/iris
    (haclo/layer-point
     #:haclo{:x :sepal-width
             :y :sepal-length
             :color :species
             :mark-size 200}))

;; The value returned by a `haclo/plot` function
;; is a [Vega-Lite](https://vega.github.io/vega-lite/) spec:

(-> datasets/iris
    (haclo/plot haclo/point-chart
                #:haclo{:x :sepal-width
                        :y :sepal-length
                        :color :species
                        :mark-size 200})
    kind/pprint)

;; By looking at the `:values` key above,
;; you can see that the dataset was implicitly represented as CSV,
;; and that it is defined to be rendered as `:svg` by default.

;; The resulting plot is displayed correctly,
;; as it is annotated by Kindly:

(-> datasets/iris
    (haclo/plot haclo/point-chart
                #:haclo{:x :sepal-width
                        :y :sepal-length
                        :color :species
                        :mark-size 200})
    meta)

;; ## Using classic Hanami templates and defaults

;; We can also use Hanami's original templates (`ht/chart`)
;; and substitution keys (`:X`, `:Y`, `:MSIZE`).

(-> datasets/iris
    (haclo/plot ht/point-chart
                {:X :sepal-width
                 :Y :sepal-length
                 :MSIZE 200
                 :COLOR "species"}))

(-> datasets/iris
    (haclo/plot ht/point-chart
                {:X :sepal-width
                 :Y :sepal-length
                 :MSIZE 200
                 :COLOR "species"})
    kind/pprint)

;; ## Inferring and overriding field types

;; Field [types](https://vega.github.io/vega-lite/docs/type.html) are inferred from the Column type.
;; Here, for example, `:haclo/x` and `:haclo/y` are `:quantitative`, and
;; `:haclo/color` is `:nominal`
;; (and is thus coloured with distinct colours rather than a gradient).

(-> datasets/iris
    (haclo/plot haclo/point-chart
                #:haclo{:x :sepal-width
                        :y :sepal-length
                        :color :species
                        :mark-size 200}))

;; On the other hand, in the following example,
;; `:color` is `:quantitative`:

(-> datasets/mtcars
    (haclo/plot haclo/point-chart
                #:haclo{:x :mpg
                        :y :disp
                        :color :cyl
                        :mark-size 200}))

;; This can be overridden:

(-> datasets/mtcars
    (haclo/plot haclo/point-chart
                #:haclo{:x :mpg
                        :y :disp
                        :color :cyl
                        :color-type :nominal
                        :mark-size 200}))

;; ## More examples

(-> datasets/mtcars
    (haclo/plot haclo/boxplot-chart
                #:haclo{:x :cyl
                        :x-type :nominal
                        :y :disp}))

(-> datasets/mtcars
    (haclo/plot ht/boxplot-chart
                {:X :cyl
                 :XTYPE :nominal
                 :Y :disp}))

(-> datasets/iris
    (haclo/plot haclo/rule-chart
                #:haclo{:x :sepal-width
                        :y :sepal-length
                        :x2 :petal-width
                        :y2 :petal-length
                        :mark-opacity 0.5
                        :mark-size 3
                        :color :species}))

(-> datasets/iris
    (haclo/plot ht/rule-chart
                {:X :sepal-width
                 :Y :sepal-length
                 :X2 :petal-width
                 :Y2 :petal-length
                 :OPACITY 0.5
                 :SIZE 3
                 :COLOR "species"}))

;; ## Time series

;; Let us plot a time series:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/plot haclo/line-chart
                #:haclo{:x :date
                        :y :value
                        :mark-color "purple"}))

;; You see, the `:date` field was correctly inferred to be
;; of the `:temporal` kind.

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/plot haclo/line-chart
                #:haclo{:x :date
                        :y :value
                        :mark-color "purple"})
    kind/pprint)

;; ## Delayed transformation

;; Instead of the `haclo/plot` function, it is possible to used
;; `haclo/base`:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base haclo/line-chart
                #:haclo{:x :date
                        :y :value
                        :mark-color "purple"}))

;; The result is displayed the same way, but the internal representation
;; delays the Hanami transformation of templates.

;; Let us compare the two:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/plot haclo/line-chart
                #:haclo{:x :date
                        :y :value
                        :mark-color "purple"})
    kind/pprint)

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base haclo/line-chart
                #:haclo{:x :date
                        :y :value
                        :mark-color "purple"})
    kind/pprint)

;; The structure returned by `haclo/base` is a Hanami template
;; (with [local defaults](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#template-local-defaults)).
;; When it is displayed, it goes through the Hanami transform
;; to recieve the Vega-Lite spec.

;; When we use base, we can keep processing the template in a pipeline
;; of transformations. We will use it soon with layers.

;; ## Adding layers

;; A base plot does not need to have a specified chart.
;; Instead, we may add layers:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value
                        :mark-color "purple"})
    haclo/layer-line)

;; The substitution keys can also be specified on the layer level:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value})
    (haclo/layer-line #:haclo{:mark-color "purple"}))

;; This allows us to create, e.g., aesthetic differences between layers:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value})
    (haclo/layer-point #:haclo{:mark-color "green"
                               :mark-size 200
                               :mark-opacity 0.1})
    (haclo/layer-line #:haclo{:mark-color "purple"}))

;; We can also skip the base and have everything in the layer:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/layer-line #:haclo{:x :date
                              :y :value
                              :mark-color "purple"}))

;; ## Updating data

;; Using `haclo/update-data`, we may process the dataset
;; during the pipeline, affecting only the layers added further down the pipeline.

;; This functionality is inspired by [ggbuilder](https://github.com/mjskay/ggbuilder)
;; and [metamorph](https://github.com/scicloj/metamorph).

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value})

    (haclo/layer-line #:haclo{:mark-color "purple"})
    (haclo/update-data tc/random 5)
    (haclo/layer-point #:haclo{:mark-color "green"
                               :mark-size 200}))

;; You see, we have lots of data for the lines,
;; but only five random points.

;; ## Processing raw vega-lite

;; During a pipeline, we may call `haclo/plot`
;; to apply the Hanami transform and realize the
;; `Vega-Lite` spec.

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value})

    (haclo/layer-line #:haclo{:mark-color "purple"})
    (haclo/update-data tc/random 5)
    (haclo/layer-point #:haclo{:mark-color "green"
                               :mark-size 200})
    haclo/plot
    kind/pprint)

;; While this in itself does not affect the display of the plot,
;; it allows us to keep editing it as a Vega-Lite spec.
;; For example, let us change the backgound colour this way:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (haclo/base #:haclo{:x :date
                        :y :value})

    (haclo/layer-line #:haclo{:mark-color "purple"})
    (haclo/update-data tc/random 5)
    (haclo/layer-point #:haclo{:mark-color "green"
                               :mark-size 200})
    haclo/plot
    (assoc :background "lightgrey"))

;; ## Smoothing

;; `haclo/layer-smooth` is a layer that applies some statistical
;; processing to the dataset to model it as a smooth shape.
;; It is inspired by ggplot's [geom_smooth](https://ggplot2.tidyverse.org/reference/geom_smooth.html).

;; At the moment, it can only be used to model `:haclo/y` by linear regression.
;; Soon we will add more ways of modelling the data.

(-> datasets/iris
    (haclo/base #:haclo{:title "dummy"
                        :mark-color "green"
                        :x :sepal-width
                        :y :sepal-length})
    haclo/layer-point
    (haclo/layer-smooth #:haclo{:mark-color "orange"}))

;; By default, the regression is computed with only one predictor variable,
;; which is `:haclo/x`.
;; But this can be overriden using the `:predictors` key.
;; We may compute a regression with more than one predictor.

(-> datasets/iris
    (haclo/base #:haclo{:x :sepal-width
                        :y :sepal-length})
    haclo/layer-point
    (haclo/layer-smooth #:haclo{:predictors [:petal-width
                                             :petal-length]}))

;; ## Grouping

;; The regression computed by `haclo/layer-smooth`
;; is affected by the inferred grouping of the data.

;; For example, here we recieve three regression lines,
;; each for every species.

(-> datasets/iris
    (haclo/base #:haclo{:title "dummy"
                        :color :species
                        :x :sepal-width
                        :y :sepal-length})
    haclo/layer-point
    haclo/layer-smooth)

;; This happened because the `:color` field was `:species`,
;; which is of `:nominal` type.

;; But we may override this using the `:group` key.
;; For example, let us avoid grouping:

(-> datasets/iris
    (haclo/base #:haclo{:title "dummy"
                        :mark-color "green"
                        :color :species
                        :group []
                        :x :sepal-width
                        :y :sepal-length})
    haclo/layer-point
    haclo/layer-smooth)

;; ## Example: out-of-sample predictions

;; Here is a slighly more elaborate example
;; inpired by the London Clojurians [talk](https://www.youtube.com/watch?v=eUFf3-og_-Y)
;; mentioned in the preface.

;; Assume we wish to predict the unemployment rate for 96 months.
;; Let us add those months to our dataset,
;; and mark them as `Future` (considering the original data as `Past`):

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (tc/add-column :relative-time "Past")
    (tc/concat (tc/dataset {:date (-> datasets/economics-long
                                      :date
                                      last
                                      (datetime/plus-temporal-amount (range 96) :days))
                            :relative-time "Future"}))
    (print/print-range 6))

;; Let us represent our dates as numbers, so that we can use them in linear regression:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (tc/add-column :relative-time "Past")
    (tc/concat (tc/dataset {:date (-> datasets/economics-long
                                      :date
                                      last
                                      (datetime/plus-temporal-amount (range 96) :months))
                            :relative-time "Future"}))
    (tc/add-column :year #(datetime/long-temporal-field :years (:date %)))
    (tc/add-column :month #(datetime/long-temporal-field :months (:date %)))
    (tc/map-columns :yearmonth [:year :month] (fn [y m] (+ m (* 12 y))))
    (print/print-range 6))

;; Let us use the same regression line for the `Past` and `Future` groups.
;; To do this, we avoid grouping by assigning  `[]` to `:haclo/group`.
;; The line is affected only by the past, since in the Future, `:y` is missing.
;; We use the numerical field `:yearmonth` as the regression predictor,
;; but for plotting, we still use the `:temporal` field `:date`.

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (tc/add-column :relative-time "Past")
    (tc/concat (tc/dataset {:date (-> datasets/economics-long
                                      :date
                                      last
                                      (datetime/plus-temporal-amount (range 96) :months))
                            :relative-time "Future"}))
    (tc/add-column :year #(datetime/long-temporal-field :years (:date %)))
    (tc/add-column :month #(datetime/long-temporal-field :months (:date %)))
    (tc/map-columns :yearmonth [:year :month] (fn [y m] (+ m (* 12 y))))
    (haclo/base #:haclo{:x :date
                        :y :value})
    (haclo/layer-smooth #:haclo{:color :relative-time
                                :mark-size 10
                                :group []
                                :predictors [:yearmonth]})
    ;; Keep only the past for the following layer:
    (haclo/update-data (fn [dataset]
                         (-> dataset
                             (tc/select-rows (fn [row]
                                               (-> row :relative-time (= "Past")))))))
    (haclo/layer-line #:haclo{:mark-color "purple"
                              :mark-size 3}))

;; ## Histograms

;; Histograms can also be represented as layers
;; with statistical processing:

(-> datasets/iris
    (haclo/layer-histogram #:haclo{:x :sepal-width}))

(-> datasets/iris
    (haclo/layer-histogram #:haclo{:x :sepal-width
                                   :histogram-nbins 30}))


;; ## Coming soon

;; ### Facets

;; ### Coordinates

;; ### Scales
