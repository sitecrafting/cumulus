(ns com.sitecrafting.cumulus.ui-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.ui :as ui]))


(deftest test-size-name->label
  (is (= "cat" (ui/size-name->label "cat")))
  (is (= "a b c" (ui/size-name->label "a_b_c")))
  (is (= "a b c" (ui/size-name->label "a-b-c")))
  (is (= "a b c" (ui/size-name->label "a_b-c"))))