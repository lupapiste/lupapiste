(ns lupapalvelu.mongocheck.checks
  "Collection of commonly needed check")

(defn not-null-property
  "Usage: (mongocheck :someCollection (lupapalvelu.mongocheck.checks/not-null-property :notNullProperty) :notNullProperty)"
  [p]
  (fn [mongo-document]
    (when (nil? (get mongo-document p))
      (str (name p) " is null"))))
