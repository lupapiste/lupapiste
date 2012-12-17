(ns lupapalvelu.perf-test)

(defn a [x]
  (Thread/sleep 100)
  "a")

(defn b [x]
  (Thread/sleep 200)
  "b")

(defn c []
  (a "1")
  (a "2")
  (b "3"))

(defn d []
  (c)
  (a "1"))
