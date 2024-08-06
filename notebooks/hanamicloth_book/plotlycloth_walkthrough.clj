;; # Plotlycloth Walkthrough 👣 - experimental 🛠

;; Plotlycloth is a Clojure API for creating [Plotly.js](https://plotly.com/javascript/) plots through layered pipelines. It is part of the Hanamicloth library.

;; Here, we provide a walkthrough of the API.

;; 🛠 This part of Hanamicloth is still in experimental stage.
;; Some of the details will change soon. Feedback and comments will help.

;; Soon, we will provide more in-depth explanations in additional chapters.

;; ## Known issues

;; - Plot legends are missing in some cases.

;; - We need to come up with reasonable defaults for `:=width` and `:=height`.

;; - Breaking changes are expected.

;; ## Setup
;; For this tutorial, we require:

;; * The plotlycloth API namepace

;; * [Tablecloth](https://scicloj.github.io/tablecloth/) for dataset processing

;; * the [datetime namespace](https://cnuernber.github.io/dtype-next/tech.v3.datatype.datetime.html) of [dtype-next](https://github.com/cnuernber/dtype-next)

;; * the [print namespace](https://techascent.github.io/tech.ml.dataset/tech.v3.dataset.print.html) of [tech.ml.dataset](https://github.com/techascent/tech.ml.dataset) for customized dataset printing

;; * [Kindly](https://scicloj.github.io/kindly-noted/) (to specify how certaiun values should be visualized)

;; * the datasets defined in the [Datasets chapter](./hanamicloth.datasets.html)

(ns hanamicloth-book.plotlycloth-walkthrough
  (:require [scicloj.hanamicloth.v1.plotlycloth :as ploclo]
            [tablecloth.api :as tc]
            [tech.v3.datatype.datetime :as datetime]
            [tech.v3.dataset.print :as print]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            [scicloj.kindly.v4.api :as kindly]
            [hanamicloth-book.datasets :as datasets]))

;; ## Basic usage

;; Plotlycloth plots are created by passing datasets to a pipeline
;; of layer functions.

;; Additional parameters to the functions are passed as maps.
;; Plotlycloth map keys begin with `=` (e.g., `:=color`).

;; For example, let us plot a scatterplot (a layer of points)
;; of 10 random items from the Iris dataset.

(-> datasets/iris
    (tc/random 10 {:seed 1})
    (ploclo/layer-point
     {:=x :sepal-width
      :=y :sepal-length
      :=color :species
      :=mark-size 20
      :=mark-opacity 0.6}))

;; ## Templates and parameters

;; (💡 You do neet need to understand these details for basic usage.)

;; Technically, the parameter maps contain [Hanami substitution keys](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#templates-substitution-keys-and-transformations),
;; which means they are processed by a [simple set of rules](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#basic-transformation-rules),
;; but you do not need to understand what this means yet.

;; The layer functions return a Hanami template. Let us print the resulting
;; structure of the previous plot.

(def example1
  (-> datasets/iris
      (tc/random 10 {:seed 1})
      (ploclo/layer-point
       {:=x :sepal-width
        :=y :sepal-length
        :=color :species
        :=mark-size 20
        :=mark-opacity 0.6})))

(kind/pprint example1)

;; This template has all the necessary knowledge, including the substitution
;; keys, to turn into a plot. This happens when your visual tool (e.g., Clay)
;; displays the plot. The tool knows what to do thanks to the Kindly metadata
;; and a special function attached to the plot.

(meta example1)

(:kindly/f example1)

;; ## Realizing the plot

;; If you wish to see the resulting plot specification before displaying it
;; as a plot, you can use the `plot` function. In this case,
;; it generates a Plotly.js plot:

(-> example1
    ploclo/plot
    kind/pprint)

;; It is annotated as `kind/plotly`, so that visual tools know how to
;; render it.

(-> example1
    ploclo/plot
    meta)

;; This can be useful if you wish to process the actual Plotly.js spec
;; rather than use Plotlycloth's API. Let us change the background colour,
;; for example:

(-> example1
    ploclo/plot
    (assoc-in [:layout :plot_bgcolor] "#eeeedd"))

;; ## Field type inference

;; Plotlycloth infers the type of relevant fields from the data.

;; The example above was colored as it were since `:species`
;; column was nominal, so it was assigned distinct colours.

;; In the following example, the coloring is by a quantitative
;; column, so a color gradient is used:

(-> datasets/mtcars
    (ploclo/layer-point
     {:=x :mpg
      :=y :disp
      :=color :cyl
      :=mark-size 20}))

;; We can override the inferred types and thus affect the generated plot:

(-> datasets/mtcars
    (ploclo/layer-point
     {:=x :mpg
      :=y :disp
      :=color :cyl
      :=color-type :nominal
      :=mark-size 20}))

;; ## More examples

;; ### Boxplot

(-> datasets/mtcars
    (ploclo/layer-boxplot
     {:=x :cyl
      :=y :disp}))

;; ### Segment plot

(-> datasets/iris
    (ploclo/layer-segment
     {:=x0 :sepal-width
      :=y0 :sepal-length
      :=x1 :petal-width
      :=y1 :petal-length
      :=mark-opacity 0.4
      :=mark-size 3
      :=color :species}))

;; ## Varying color and size

(-> {:ABCD (range 1 11)
     :EFGH [5 2.5 5 7.5 5 2.5 7.5 4.5 5.5 5]
     :IJKL [:A :A :A :A :A :B :B :B :B :B]
     :MNOP [:C :D :C :D :C :D :C :D :C :D]}
    tc/dataset
    (ploclo/base {:=title "IJKLMNOP"})
    (ploclo/layer-point {:=x :ABCD
                         :=y :EFGH
                         :=color :IJKL
                         :=size :MNOP
                         :=name "QRST1"})
    (ploclo/layer-line
     {:=title "IJKL MNOP"
      :=x :ABCD
      :=y :ABCD
      :=name "QRST2"
      :=mark-color "magenta"
      :=mark-size 20
      :=mark-opacity 0.2}))

;; ## Time series

;; Date and time fields are handle appropriately.
;; Let us, for example, draw the time series of unemployment counts.

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (ploclo/layer-line
     {:=x :date
      :=y :value
      :=mark-color "purple"}))

;; ## Multiple layers

;; We can draw more than one layer:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (ploclo/layer-point {:=x :date
                         :=y :value
                         :=mark-color "green"
                         :=mark-size 20
                         :=mark-opacity 0.5})
    (ploclo/layer-line {:=x :date
                        :=y :value
                        :=mark-color "purple"}))

;; We can also use the `base` function for the common parameters
;; across layers:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (ploclo/base {:=x :date
                  :=y :value})
    (ploclo/layer-point {:=mark-color "green"
                         :=mark-size 20
                         :=mark-opacity 0.5})
    (ploclo/layer-line {:=mark-color "purple"}))

;; ## Updating data

;; We can use the `update-data` function to vary the
;; dataset along a plotting pipeline, affecting
;; the layers that follow.

;; This functionality is inspired by [ggbuilder](https://github.com/mjskay/ggbuilder)
;; and [metamorph](https://github.com/scicloj/metamorph).

;; Here, for example, we draw a line,
;; then sample 5 data rows,
;; and draw them as points:

(-> datasets/economics-long
    (tc/select-rows #(-> % :variable (= "unemploy")))
    (ploclo/base {:=x :date
                  :=y :value})
    (ploclo/layer-line {:=mark-color "purple"})
    (ploclo/update-data tc/random 5)
    (ploclo/layer-point {:=mark-color "green"
                         :=mark-size 15
                         :=mark-opacity 0.5}))

;; ## Smoothing

;; `layer-smooth` is a layer that applies some statistical
;; processing to the dataset to model it as a smooth shape.
;; It is inspired by ggplot's [geom_smooth](https://ggplot2.tidyverse.org/reference/geom_smooth.html).

;; At the moment, it can only be used to model `:=y` by linear regression.
;; Soon we will add more ways of modelling the data.

(-> datasets/iris
    (ploclo/base {:=title "dummy"
                  :=mark-color "green"
                  :=x :sepal-width
                  :=y :sepal-length})
    ploclo/layer-point
    (ploclo/layer-smooth {:=mark-color "orange"})
    ploclo/plot)

;; By default, the regression is computed with only one predictor variable,
;; which is `:=x`.
;; But this can be overriden using the `:predictors` key.
;; We may compute a regression with more than one predictor.

(-> datasets/iris
    (ploclo/base {:=x :sepal-width
                  :=y :sepal-length})
    ploclo/layer-point
    (ploclo/layer-smooth {:=predictors [:petal-width
                                        :petal-length]
                          :=mark-opacity 0.5})
    ploclo/plot)

;; ## Grouping

;; The regression computed by `haclo/layer-smooth`
;; is affected by the inferred grouping of the data.

;; For example, here we recieve three regression lines,
;; each for every species.

(-> datasets/iris
    (ploclo/base {:=title "dummy"
                  :=color :species
                  :=x :sepal-width
                  :=y :sepal-length})
    ploclo/layer-point
    ploclo/layer-smooth)

;; This happened because the `:color` field was `:species`,
;; which is of `:nominal` type.

;; But we may override this using the `:group` key.
;; For example, let us avoid grouping:

(-> datasets/iris
    (ploclo/base {:=title "dummy"
                  :=color :species
                  :=group []
                  :=x :sepal-width
                  :=y :sepal-length})
    ploclo/layer-point
    ploclo/layer-smooth)

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
;; To do this, we avoid grouping by assigning  `[]` to `:=group`.
;; The line is affected only by the past, since in the Future, `:=y` is missing.
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
    (ploclo/base {:=x :date
                  :=y :value})
    (ploclo/layer-smooth {:=color :relative-time
                          :=mark-size 20
                          :=group []
                          :=predictors [:yearmonth]})
    ;; Keep only the past for the following layer:
    (ploclo/update-data (fn [dataset]
                          (-> dataset
                              (tc/select-rows (fn [row]
                                                (-> row :relative-time (= "Past")))))))
    (ploclo/layer-line {:=mark-color "purple"
                        :=mark-size 3}))

;; ## Histograms

;; Histograms can also be represented as layers
;; with statistical processing:

(-> datasets/iris
    (ploclo/layer-histogram {:=x :sepal-width}))


(-> datasets/iris
    (ploclo/layer-histogram {:=x :sepal-width
                             :=histogram-nbins 30}))
;; ## Coming soon

;; ### Facets

;; ### Coordinates

;; ### Scales
