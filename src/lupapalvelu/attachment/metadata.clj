(ns lupapalvelu.attachment.metadata
  (:require [lupapiste-commons.tos-metadata-schema :as tosmeta]))


(def visibilities (:values tosmeta/Nakyvyys))

(def public-visibility "julkinen")

(defn get-visibility [{metadata :metadata}]
  (get metadata :nakyvyys))

(defn get-publicity-class [{metadata :metadata}]
  (get metadata :julkisuusluokka))

(defn public-attachment?
  "Returns false if julkisuusluokka is not public or if julkisuusluokka is not set and nakyvyys metadata is not public.
  Without metadata returns true."
  [attachment]
  (if-let [visibility (or (get-publicity-class attachment) (get-visibility attachment))]
    (= public-visibility visibility)
    true))
