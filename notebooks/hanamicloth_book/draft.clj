(ns hanamicloth.draft)

(-> datasets/iris
    (haclo/base #:haclo{:title "dummy"
                        :color :species
                        :x :sepal-width
                        :y :sepal-length
                        :facet :species})
    haclo/layer-point
    haclo/layer-smooth
    haclo/plot)

(-> datasets/iris
    (haclo/base haclo/point-chart
                #:haclo{:title "dummy"
                        :color :species
                        :x :sepal-width
                        :y :sepal-length
                        :column :species})
    haclo/plot)
