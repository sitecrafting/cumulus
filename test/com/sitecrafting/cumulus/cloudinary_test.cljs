(ns com.sitecrafting.cumulus.cloudinary-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.sitecrafting.cumulus.cloudinary :as cloud]))


(deftest test-params->transforms

  (testing "When cropping"
    (is (= [{:x 123 :y 456 :w 1000 :h 500 :c "crop"} {:w 150 :h 150 :c "scale"}]
           (cloud/params->transforms {:edit-mode :crop
                                      :x 123
                                      :y 456
                                      :w 1000
                                      :h 500
                                      :target-size [150 150]}))))
  (testing "When auto-scaling"
    (is (= [{:w 150 :h 150 :c "lfill"}]
           (cloud/params->transforms {:edit-mode :scale
                                      :target-size [150 150]})))))

;; https://cloudinary.com/documentation/image_transformations#crop
(deftest test-img-transforms->url

  (testing "With a dimension of zero"
    ;; Width/height dimensions of zero should get filtered out
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,w_1000,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 1000
                                                     :h 0
                                                     :c "crop"}]})))
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,h_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 0
                                                     :h 500
                                                     :c "crop"}]})))
    ;; x/y values of zero are valid and should NOT get filtered out
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_0,y_0,w_1000,h_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 0
                                                     :y 0
                                                     :w 1000
                                                     :h 500
                                                     :c "crop"}]}))))

  (testing "With nil values"
    ;; Nil values should also get filtered out
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,h_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w nil
                                                     :h 500
                                                     :c "crop"}]})))
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,w_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 500
                                                     :h nil
                                                     :c "crop"}]})))
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,w_1000,h_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y nil
                                                     :w 1000
                                                     :h 500
                                                     :c "crop"}]}))))

  (testing "With a single transform"
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,w_1000,h_500,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 1000
                                                     :h 500
                                                     :c "crop"}]}))))

  (testing "With multiple transforms"
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,w_1000,h_500,c_crop/w_150,h_150,c_scale/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 1000
                                                     :h 500
                                                     :c "crop"}
                                                    {:w 150
                                                     :h 150
                                                     :c "scale"}]})))
    (is (= "https://res.cloudinary.com/my-cloud/image/upload/x_123,y_456,w_1000,h_500,c_crop/w_150,h_150,g_face,c_crop/test/cat.jpg"
           (cloud/img-transforms->url {:cloud "my-cloud"
                                       :filename "test/cat.jpg"
                                       :transforms [{:x 123
                                                     :y 456
                                                     :w 1000
                                                     :h 500
                                                     :c "crop"}
                                                    {:w 150
                                                     :h 150
                                                     :g "face"
                                                     :c "crop"}]})))))