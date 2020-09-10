(ns com.sitecrafting.cumulus.cloudinary-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]))


(deftest test-crop->url

  (is (= "https://res.cloudinary.com/my-bucket/image/upload/x_123,y_456,w_1000,h_500,c_crop/test/cat.jpg"
         (cloud/crop->url {:bucket "my-bucket"
                           :folder "test"
                           :filename "cat.jpg"
                           :x 123
                           :y 456
                           :w 1000
                           :h 500}))))