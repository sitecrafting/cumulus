(ns com.sitecrafting.cumulus.ui-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.ui :as ui]))


(deftest test-size-name->label
  (is (= "cat" (ui/size-name->label "cat")))
  (is (= "a b c" (ui/size-name->label "a_b_c")))
  (is (= "a b c" (ui/size-name->label "a-b-c")))
  (is (= "a b c" (ui/size-name->label "a_b-c"))))

(deftest test-dimensions-cofx
  (is (= {:stuff :whatever
          :dimensions {:natural-width 1000
                       :natural-height 800
                       :rendered-width 100
                       :rendered-height 80}}
         (with-redefs [ui/!img (atom #js {:naturalWidth 1000
                                          :naturalHeight 800
                                          :width 100
                                          :height 80})]
           (ui/dimensions-cofx {:stuff :whatever} :_)))))