(ns lupapalvelu.attachment-metadata
  (:require [lupapiste-commons.tos-metadata-schema :as tosmeta]))


(def visibilities (:values tosmeta/Nakyvyys))

(def public-visibility "julkinen")

(defn public-attachment?
  "Returns false if either julkisuusluokka or nakyvyys metadata is not public. Without metadata returns true."
  [{metadata :metadata :as attachment}]
  (let [visibility (get metadata :nakyvyys)
        publicity-class (get metadata :julkisuusluokka)]
    (if (or publicity-class visibility)
      (= public-visibility (or publicity-class visibility))
      true)))
