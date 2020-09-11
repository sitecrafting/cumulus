(ns com.sitecrafting.cumulus.cloudinary-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]))


(deftest test-crop->url

  ;; For manual crops
  ;; https://cloudinary.com/documentation/image_transformations#crop
  (is (= "https://res.cloudinary.com/my-bucket/image/upload/x_123,y_456,w_1000,h_500,c_crop/test/cat.jpg"
         (cloud/crop->url {:bucket "my-bucket"
                           :folder "test"
                           :filename "cat.jpg"
                           :x 123
                           :y 456
                           :w 1000
                           :h 500}))))

(deftest test-scale->url

  ;; c should default to lfill
  ;; https://cloudinary.com/documentation/image_transformations#lfill_limit_fill
  (is (= "https://res.cloudinary.com/my-bucket/image/upload/w_1000,h_500,c_lfill/test/cat.jpg"
         (cloud/scale->url {:bucket "my-bucket"
                            :folder "test"
                            :filename "cat.jpg"
                            :w 1000
                            :h 500})))

  ;; can override c with anything
  ;; https://cloudinary.com/documentation/image_transformations#resizing_and_cropping_images
  (is (= "https://res.cloudinary.com/my-bucket/image/upload/w_1000,h_500,c_fit/test/cat.jpg"
         (cloud/scale->url {:bucket "my-bucket"
                            :folder "test"
                            :filename "cat.jpg"
                            :w 1000
                            :h 500
                            :c "fit"}))))