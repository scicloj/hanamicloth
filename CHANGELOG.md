# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1-alpha9] - unreleased
- deps update

## [1-alpha8] - 2024-09-21
- deps update

## [1-alpha7-SNAPSHOT] - 2024-09-13
- plotlycloth - added text & font support
- plotlycloth - bugfix: broken x axis in histograms

## [1-alpha6-SNAPSHOT] - 2024-08-09
- plotlycloth - coordinates support - WIP
- plotlylcoth - styling changes
- plotlycloth - simplified the inference of mode and type a bit
- debugging support - WIP

## [1-alpha5-SNAPSHOT] - 2024-08-06
- added the `plotlycloth` API (experimental) generating [Plotly.js plots](https://plotly.com/javascript/)

## [1-alpha4-SNAPSHOT] - 2024-07-12
- breaking change: substitution keys are `:=abcd`-style rather than `:haclo/abcd`-style
- simplified CSV writing
- more careful handling of datasets - avoiding layer data when they can reuse the toplevel data
- related refactoring
- facets support - WIP

## [1-alpha3-SNAPSHOT] - 2024-06-28
- catching common problems
- bugfix (#1) of type inference after stat

## [1-alpha2-SNAPSHOT] - 2024-06-22
- renamed the `hanami` keyword namespace to `haclo` to avoid confusion

## [1-alpha1-SNAPSHOT] - 2024-06-22
- initial version
