(ns lupapalvelu.attachment.metadata
  (:require [lupapiste-commons.tos-metadata-schema :as tosmeta]))


(def visibilities (:values tosmeta/Nakyvyys))

(def public-visibility "julkinen")

(defn get-visibility [{metadata :metadata}]
  (get metadata :nakyvyys))

(defn get-publicity-class [{metadata :metadata}]
  (get metadata :julkisuusluokka))

(defn public-attachment?
  "Returns false if either julkisuusluokka or nakyvyys metadata is not public. Without metadata returns true."
  [attachment]
  (let [visibility (get-visibility attachment)
        publicity-class (get-publicity-class attachment)]
    (if (or publicity-class visibility)
      (= public-visibility (or publicity-class visibility))
      true)))
