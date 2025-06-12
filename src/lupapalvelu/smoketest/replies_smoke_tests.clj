(ns lupapalvelu.smoketest.replies-smoke-tests
  (:require [lupapalvelu.mongo :as mongo]
            [lupapalvelu.mongocheck.core :refer [mongocheck]]
            [lupapalvelu.replies-schemas :refer [LegacyReply]]
            [schema.core :as sc]))

(mongocheck :replies
  (comp (sc/checker LegacyReply) mongo/with-id))
