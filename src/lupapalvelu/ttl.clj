(ns lupapalvelu.ttl
  (:import [java.time Duration]))

(set! *warn-on-reflection* true)

(def default-ttl (.toMillis (Duration/ofDays 180)))
(def reset-password-token-ttl (.toMillis (Duration/ofMinutes 30)))
(def change-email-token-ttl (.toMillis (Duration/ofDays 1)))
(def create-user-token-ttl (.toMillis (Duration/ofDays 180)))
(def idf-create-user-token-ttl (.toMillis (Duration/ofDays 180)))
(def company-invite-ttl (.toMillis (Duration/ofDays 90)))
(def neighbor-token-ttl (.toMillis (Duration/ofDays 14)))
(def map-token-ttl (.toMillis (Duration/ofHours 3)))
