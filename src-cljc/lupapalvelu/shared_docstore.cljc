(ns lupapalvelu.shared-docstore
  (:require [sade.util :as util]))

(def docstore-languages
  "Currently the docstore et al are available only in Finnish. The order of the languages
  is (eventually...)  important."
  [:fi])

(defn docstore-language?
  [lang]
  (some #(util/=as-kw lang %) docstore-languages))
