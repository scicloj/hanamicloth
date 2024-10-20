;; # Plotly API In Depth - experimental - DRAFT 🛠

(ns tableplot-book.plotly-indepth
  (:require [scicloj.tableplot.v1.plotly :as plotly]
            [tablecloth.api :as tc]
            [tablecloth.column.api :as tcc]
            [tech.v3.datatype.datetime :as datetime]
            [tech.v3.dataset.print :as print]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            [scicloj.kindly.v4.api :as kindly]
            [tableplot-book.datasets :as datasets]
            [aerial.hanami.templates :as ht]))

(def example1
  (-> {:w (interleave (repeat 3 :A)
                      (repeat 3 :B))
       :x (range 6)}
      tc/dataset
      (tc/map-columns :y
                      [:w :x]
                      (fn [w x]
                        (case w
                          :A (* x x)
                          :B (- (* x x x)
                                (* 5 x x)))))
      (plotly/layer-point
       {:=x :x
        :=y :y
        :=color :w
        :=mark-size 20})))

example1

(-> example1
    plotly/plot)

(-> example1
    plotly/plot
    kind/pprint)

(kind/plotly
 {:data [{:y [0 4 16 36 64 100 144 196 256 324],
          :name ":A",
          :marker {:color "#1B9E77", :size 20},
          :mode :markers,
          :type "scatter",
          :x [0 2 4 6 8 10 12 14 16 18],}
         {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
          :name ":B",
          :marker {:color "#D95F02", :size 20},
          :mode :markers,
          :type "scatter",
          :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout {:width 500,
           :height 400,
           :plot_bgcolor "rgb(235,235,235)",
           :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
           :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}})

(plotly/plot
 {:data [{:y [0 4 16 36 64 100 144 196 256 324],
          :name ":A",
          :marker {:color "#1B9E77", :size 20},
          :mode :markers,
          :type "scatter",
          :x [0 2 4 6 8 10 12 14 16 18],}
         {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
          :name ":B",
          :marker {:color "#D95F02", :size 20},
          :mode :markers,
          :type "scatter",
          :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout {:width 500,
           :height 400,
           :plot_bgcolor "rgb(235,235,235)",
           :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
           :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}})

(plotly/plot
 {:data [{:y [0 4 16 36 64 100 144 196 256 324],
          :name ":A",
          :marker {:color "#1B9E77", :size 20},
          :mode :markers,
          :type "scatter",
          :x [0 2 4 6 8 10 12 14 16 18],}
         {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
          :name ":B",
          :marker {:color "#D95F02", :size 20},
          :mode :markers,
          :type "scatter",
          :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout {:width 500,
           :height 400,
           :plot_bgcolor "rgb(235,235,235)",
           :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
           :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}})

(plotly/plot
 ;; template
 {:data
  [{:y [0 4 16 36 64 100 144 196 256 324],
    :name ":A",
    :marker {:color "#1B9E77", :size 20},
    :mode :markers,
    :type "scatter",
    :x [0 2 4 6 8 10 12 14 16 18],}
   {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
    :name ":B",
    :marker {:color "#D95F02", :size 20},
    :mode :markers,
    :type "scatter",
    :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout :====layout}
 ;; substitution-keys
 {:====layout {:width 500,
               :height 400,
               :plot_bgcolor "rgb(235,235,235)",
               :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
               :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}})

(plotly/plot
 ;; template
 {:data
  [{:y [0 4 16 36 64 100 144 196 256 324],
    :name ":A",
    :marker {:color "#1B9E77", :size 20},
    :mode :markers,
    :type "scatter",
    :x [0 2 4 6 8 10 12 14 16 18],}
   {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
    :name ":B",
    :marker {:color "#D95F02", :size 20},
    :mode :markers,
    :type "scatter",
    :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout :====layout}
 ;; substitution-keys
 {:====layout {:width 500,
               :height 400,
               :plot_bgcolor :====bgcolor,
               :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
               :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}
  :====bgcolor "rgb(235,235,235)"})


(plotly/plot
 ;; template
 {:data
  [{:y [0 4 16 36 64 100 144 196 256 324],
    :name ":A",
    :marker {:color "#1B9E77", :size :====marker-size},
    :mode :markers,
    :type "scatter",
    :x [0 2 4 6 8 10 12 14 16 18],}
   {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
    :name ":B",
    :marker {:color "#D95F02", :size :====marker-size},
    :mode :markers,
    :type "scatter",
    :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout :====layout
  ::ht/defaults {:====marker-size 20}}
 ;; substitution-keys
 {:====layout {:width 500,
               :height 400,
               :plot_bgcolor :====bgcolor,
               :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
               :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}
  :====bgcolor "rgb(235,235,235)"})


(plotly/plot
 ;; template
 {:data
  [{:y [0 4 16 36 64 100 144 196 256 324],
    :name ":A",
    :marker {:color :====color0, :size :====marker-size},
    :mode :markers,
    :type "scatter",
    :x [0 2 4 6 8 10 12 14 16 18],}
   {:y [-19 -153 -375 -637 -891 -1089 -1183 -1125 -867 -361],
    :name ":B",
    :marker {:color :====color1, :size :====marker-size},
    :mode :markers,
    :type "scatter",
    :x [1 3 5 7 9 11 13 15 17 19],}],
  :layout :====layout
  ::ht/defaults {:====marker-size 20
                 :====color0 (fn [submap]
                               (-> submap
                                   :====colors
                                   first))
                 :====color1 (fn [submap]
                               (-> submap
                                   :====colors
                                   second))
                 :====colors ["#1B9E77" "#D95F02"]}}
 ;; substitution-keys
 {:====layout {:width 500,
               :height 400,
               :plot_bgcolor :====bgcolor,
               :xaxis {:gridcolor "rgb(255,255,255)", :title :x},
               :yaxis {:gridcolor "rgb(255,255,255)", :title :y},}
  :====bgcolor "rgb(235,235,235)"})
