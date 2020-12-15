(ns com.sitecrafting.cumulus.cloudinary-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]))


(deftest test-url

  ;; For manual crops
  ;; https://cloudinary.com/documentation/image_transformations#crop
  (testing "when cropping manually"
    (is (= "https://res.cloudinary.com/my-bucket/image/upload/x_123,y_456,w_1000,h_500,c_crop/w_150,h_150,c_scale/test/cat.jpg"
           (cloud/url {:mode :crop
                       :bucket "my-bucket"
                       :filename "test/cat.jpg"
                       :x 123
                       :y 456
                       :w 1000
                       :h 500
                       :target-size [150 150]})))

    ;; Dimensions of zero should get filtered out
    (is (= "https://res.cloudinary.com/my-bucket/image/upload/x_123,y_456,w_1000,h_500,c_crop/w_150,c_scale/test/cat.jpg"
           (cloud/url {:mode :crop
                       :bucket "my-bucket"
                       :filename "test/cat.jpg"
                       :x 123
                       :y 456
                       :w 1000
                       :h 500
                       :target-size [150 0]})))
    (is (= "https://res.cloudinary.com/my-bucket/image/upload/x_123,y_456,w_1000,h_500,c_crop/h_150,c_scale/test/cat.jpg"
           (cloud/url {:mode :crop
                       :bucket "my-bucket"
                       :filename "test/cat.jpg"
                       :x 123
                       :y 456
                       :w 1000
                       :h 500
                       :target-size [0 150]}))))

  ;; c should default to lfill
  ;; https://cloudinary.com/documentation/image_transformations#lfill_limit_fill
  (testing "when scaling"
    (is (= "https://res.cloudinary.com/my-bucket/image/upload/w_1000,h_500,c_lfill/test/cat.jpg"
           (cloud/url {:mode :scale
                       :bucket "my-bucket"
                       :filename "test/cat.jpg"
                       :target-size [1000 500]
                       :w 0
                       :h 0})))

    ;; can override c with anything
    ;; https://cloudinary.com/documentation/image_transformations#resizing_and_cropping_images
    (is (= "https://res.cloudinary.com/my-bucket/image/upload/w_1000,h_500,c_fit/test/cat.jpg"
           (cloud/url {:mode :scale
                       :bucket "my-bucket"
                       :filename "test/cat.jpg"
                       :target-size [1000 500]
                       :w 0
                       :h 0
                       :c "fit"})))))