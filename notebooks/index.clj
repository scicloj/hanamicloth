;; # Preface

;; Hanamicloth is a composition of
;; [Hanami](https://github.com/jsa-aerial/hanami) data visualization [templates](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#templates-substitution-keys-and-transformations)
;; and [Tablecloth](https://scicloj.github.io/tablecloth/) datasets.
;; It adds a simplified set of Hanami templates and defaults alongside those of Hanami,
;; as well as a set of template-processing functions
;; inspired by [ggplot2](https://ggplot2.tidyverse.org/)'s
;; [layered grammar of graphics](https://vita.had.co.nz/papers/layered-grammar.html).

;; A more comprehensive documentation is coming soon.

;; The current draft was written by Daniel Slutsky,
;; mentored by jsa-aerial (Hanami author) and Kira McLean.

;; An early version of this library was demonstrated in Kira Mclean's
;; April 2024 talk at London Clojurians:
^{:kind/video true
  :kindly/hide-code true}
{:youtube-id "eUFf3-og_-Y"}

^:kindly/hide-code
(ns index
  (:require [scicloj.kindly.v4.api :as kindly]
            [scicloj.kindly.v4.kind :as kind]
            [clojure.string :as str]
            [clojure.string :as string]
            [scicloj.clay.v2.api :as clay]))

^:kindly/hide-code
(def md
  (comp kindly/hide-code kind/md))

(md "

Hanamicloth is a composition of Hanami data visualizations and Tablecloth datasets

## Existing chapters in this book:
")

^:kindly/hide-code
(defn chapter->title [chapter]
  (or (some->> chapter
               (format "notebooks/hanamicloth/%s.clj")
               slurp
               str/split-lines
               (filter #(re-matches #"^;; # .*" %))
               first
               (#(str/replace % #"^;; # " "")))
      chapter))

(->> "notebooks/chapters.edn"
     slurp
     clojure.edn/read-string
     (map (fn [chapter]
            (prn [chapter (chapter->title chapter)])
            (format "\n- [%s](hanamicloth.%s.html)\n"
                    (chapter->title chapter)
                    chapter)))
     (string/join "\n")
     md)
