(ns walkthrough
  (:require [scicloj.hanacloth.v1.api :as hana]
            [scicloj.metamorph.ml.toydata :as toydata]
            [scicloj.metamorph.ml.toydata.ggplot :as toydata.ggplot]))


(delay
  (-> (toydata/iris-ds)
      (hana/base ht/point-chart
                 {:X :sepal_width
                  :Y :sepal_length
                  :MSIZE 200})))

(delay
  (-> (toydata/iris-ds)
      (hana/base {:X :sepal_width
                  :Y :sepal_length
                  :MSIZE 200})
      layer-point))

(delay
  (-> (toydata/iris-ds)
      (hana/base {:X :sepal_width
                  :Y :sepal_length})
      (layer-point {:MSIZE 200})))

(delay
  (-> (toydata/iris-ds)
      (hana/base)
      (layer-point {:X :sepal_width
                    :Y :sepal_length
                    :MSIZE 200})))

(delay
  (-> (toydata/iris-ds)
      (hana/base {:TITLE "dummy"
                  :MCOLOR "green"
                  :X :sepal_width
                  :Y :sepal_length})
      (layer-point
       {:MSIZE 100})
      (layer-line
       {:MSIZE 4
        :MCOLOR "brown"})
      (update-data tc/random 20)))


(delay
  (-> (toydata/iris-ds)
      (hana/base {:TITLE "dummy"
                  :MCOLOR "green"
                  :X :sepal_width

                  :Y :sepal_length})
      (layer-point {:MSIZE 100})
      (layer-line {:MSIZE 4
                   :MCOLOR "brown"})
      (update-data tc/random 20)
      hana/plot
      (assoc :background "lightgrey")))

(delay
  (-> (toydata/iris-ds)
      (tc/select-columns [:sepal_width :sepal_length])
      (hana/base {:X :sepal_width
                  :Y :sepal_length})
      layer-point
      layer-smooth))

(delay
  (-> (toydata/iris-ds)
      (hana/base {:X :sepal_width
             :Y :sepal_length
             :COLOR "species"
             :hana/group [:species]})
      layer-point
      layer-smooth))

(delay
  (-> (toydata/iris-ds)
      (tc/concat (tc/dataset {:sepal_width (range 4 10)
                              :sepal_length (repeat 6 nil)}))
      (tc/map-columns :relative-time
                      [:sepal_length]
                      #(if % "Past" "Future"))
      (hana/base {:X :sepal_width
                  :Y :sepal_length
                  :COLOR "relative-time"})
      layer-point
      layer-smooth))


(delay
  (-> (toydata/iris-ds)
      (hana/base {:X :sepal_width
             :Y :sepal_length})
      layer-point
      (layer-smooth {:predictors [:petal_width
                                  :petal_length]})))


(def random-walk
  (let [n 20]
    (-> {:x (range n)
         :y (->> (repeatedly n #(- (rand) 0.5))
                 (reductions +))}
        tc/dataset)))

(-> random-walk
    (hana/plot ht/point-chart
               {:MSIZE 200}))

(-> random-walk
    (hana/plot ht/point-chart
               {:MSIZE 200})
    kind/pprint)


(-> toydata.ggplot/mtcars
    (hana/plot ht/boxplot-chart
               {:X :gear
                :XTYPE :nominal
                :Y :mpg}))

(-> (toydata/iris-is)
    (hana/plot ht/rule-chart
               {:X :sepal-width
                :Y :sepal-length
                :X2 :petal-width
                :Y2 :petal-length
                :OPACITY 0.2
                :SIZE 3
                :COLOR "species"}))
