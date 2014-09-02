(ns lupapalvelu.stamper-test
  (:require [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]))

(testable-privates lupapalvelu.stamper calculate-x-y get-origin)

(fact "problematic-pdfs/pohjapiirros.pdf"
  (let [page-box {:left 0.0, :bottom -1795.2, :right 841.92, :top 0.0}
        crop-box {:left 0.0, :bottom -1795.2, :right 841.92, :top 0.0}
        [x y] (calculate-x-y page-box crop-box 0 0.0 0 0)]
    x => (roughly (:right crop-box))
    y => (roughly (:bottom crop-box))))

(fact "problematic-pdfs/001AS-B.pdf"
  (let [page-box {:left 0.0, :bottom 0.0, :right 842.0, :top 2976.0}
        crop-box {:left 9.14619, :bottom 2.14502, :right 842.0, :top 2969.46}
        [x y] (calculate-x-y page-box crop-box 270 0.0 0 0)]
    x => (roughly (:top crop-box))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/002P01-A.pdf"
  (let [page-box {:left 0.0, :bottom 0.0, :right 1728.0, :top 3456.0}
        crop-box {:left 0.0, :bottom -1172.88, :right 841.68, :top 0.0}
        [x y] (calculate-x-y page-box crop-box 270 0.0 0 0)]
    x => (roughly (:top crop-box))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/003P2U-A.pdf"
  (let [page-box-box {:left 0.0, :bottom 0.0, :right 1728.0, :top 3456.0}
        crop-box {:left 11.3639, :bottom 1068.41, :right 1692.55, :top 3448.4}
        [x y] (calculate-x-y page-box-box crop-box 270 0.0 0 0)]
    x => (roughly (- (:top crop-box) (:bottom crop-box)))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/690-101.pdf"
  (let [page-box {:left 0.0, :bottom 0.0, :right 2526.0, :top 5953.0}
        crop-box {:left 419.69, :bottom 1487.01, :right 2105.89, :top 4465.85}
        [x y] (calculate-x-y page-box crop-box 270 0.0 0 0)]
    x => (roughly (:top crop-box))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/690-102.pdf"
  (let [page-box {:left 0.0, :bottom 0.0, :right 2526.0, :top 5953.0}
        crop-box {:left 840.5, :bottom 1784.58, :right 1685.02, :top 4168.34}
        [x y] (calculate-x-y page-box crop-box 270 0.0 0 0)]
    x => (roughly (:top crop-box))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/2014-0337-3.pdf"
  (let [page-box {:left 0.0, :bottom -1172.88, :right 841.68, :top 0.0}
        crop-box {:left 0.0, :bottom -1172.88, :right 841.68, :top 0.0}
        [x y] (calculate-x-y page-box crop-box 90 0.0 0 0)]
    x => (roughly (:top crop-box))
    y => (roughly (:left crop-box))))

(fact "problematic-pdfs/scan.pdf"
   (let [page-box {:left 0.0, :bottom -2990.52, :right 841.68, :top 0.0}
        crop-box {:left 0.0, :bottom -2990.52, :right 841.68, :top 0.0}
        [x y] (calculate-x-y page-box crop-box 270 0.0 0 0)]
     x => (roughly (- (:top crop-box) (:bottom crop-box)))
     y => (roughly (:left crop-box))))

