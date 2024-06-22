;; # Preface

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

(md
 "**Source:** [![(GiuHub repo)](https://img.shields.io/badge/github-%23121011.svg?style=for-the-badge&logo=github&logoColor=white)](https://github.com/scicloj/hanamicloth)

**Artifact:** [![(Clojars coordinates)](https://img.shields.io/clojars/v/org.scicloj/hanamicloth.svg)](https://clojars.org/org.scicloj/hanamicloth)

**Status:** initial draft

Hanamicloth is a composition of
[Hanami](https://github.com/jsa-aerial/hanami) data visualization [templates](https://github.com/jsa-aerial/hanami?tab=readme-ov-file#templates-substitution-keys-and-transformations)
and [Tablecloth](https://scicloj.github.io/tablecloth/) datasets.

It adds a simplified set of Hanami templates and defaults alongside those of Hanami,
as well as a set of template-processing functions
inspired by [ggplot2](https://ggplot2.tidyverse.org/)'s
[layered grammar of graphics](https://vita.had.co.nz/papers/layered-grammar.html).

A more comprehensive documentation is coming soon.

The current draft was written by Daniel Slutsky,
mentored by jsa-aerial (Hanami author) and Kira McLean.

An early version of this library was demonstrated in Kira Mclean's
April 2024 talk at London Clojurians:
")

^{:kind/video true
  :kindly/hide-code true}
{:youtube-id "eUFf3-og_-Y"}

(md "

Hanamicloth is a composition of Hanami data visualizations and Tablecloth datasets

## Discussion
- development - topic threads under [#hanacloth-dev](https://clojurians.zulipchat.com/#narrow/stream/443101-hanacloth-dev) at the [Clojurians Zulip chat](https://scicloj.github.io/docs/community/chat/) or [Github Issues](https://github.com/scicloj/hanamicloth/issues)
- usage - topic threads under [#data-science](https://clojurians.zulipchat.com/#narrow/stream/151924-data-science) at the [Clojurians Zulip chat](https://scicloj.github.io/docs/community/chat/)


## Chapters in this book:
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
