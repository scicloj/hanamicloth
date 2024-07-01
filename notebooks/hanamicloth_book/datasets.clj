;; # Some datasets

;; In this documentation, we will use a few datasets from [RDatasets](https://vincentarelbundock.github.io/Rdatasets/articles/data.html).

(ns hanamicloth-book.datasets
  (:require [tablecloth.api :as tc]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

(defn fetch-dataset [dataset-name]
  (-> dataset-name
      (->> (format "https://vincentarelbundock.github.io/Rdatasets/csv/%s.csv"))
      (tc/dataset {:key-fn (fn [k]
                             (-> k
                                 str/lower-case
                                 (str/replace #"\." "-")
                                 keyword))})
      (tc/set-dataset-name dataset-name)))

(defn compact-view [dataset]
  (-> dataset
      (kind/table {:use-datatables true
                   :datatables {:scrollY 150
                                :searching false
                                :info false}})))

;; ## Edgar Anderson's Iris Data

(defonce iris
  (fetch-dataset "datasets/iris"))

(compact-view iris)

;; ## Motor Trend Car Road Tests

(defonce mtcars
  (fetch-dataset "datasets/mtcars"))

(compact-view mtcars)

;; ## US economic time series

(defonce economics-long
  (fetch-dataset "ggplot2/economics_long"))

(compact-view economics-long)
