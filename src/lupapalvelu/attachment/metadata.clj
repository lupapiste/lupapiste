(ns lupapalvelu.attachment.metadata
  (:require [lupapiste-commons.tos-metadata-schema :as tosmeta]))


(def visibilities (:values tosmeta/Nakyvyys))

(def public-visibility :julkinen)

(defn get-visibility [{metadata :metadata}]
  (get metadata :nakyvyys))

(defn get-publicity-class [{metadata :metadata}]
  (get metadata :julkisuusluokka))

(defn public-attachment?
  "Returns true if julkisuusluokka and nakyvyys are both public, or if only one of them is defined, that one is public,
   or if neither is defined. If either one is not public, this function must return false."
  [attachment]
  (let [publicity-class (keyword (get-publicity-class attachment))
        visibility (keyword (get-visibility attachment))]
    (cond
      (and visibility publicity-class) (and (= public-visibility visibility) (= publicity-class public-visibility))
      visibility (= public-visibility visibility)
      publicity-class (= public-visibility publicity-class)
      :else true)))
