(ns com.sitecrafting.cumulus.cloudinary
  (:require
   [clojure.string :refer [join]]))


(defn- params->url-segments
  "Given a map like {:w 1000 :h 400}, returns a string like 'w_1000,h_400'.
   If no crop strategy is passed, defaults to c_crop.
   Honors any keys passed to it; it is up to the caller to make sure they are valid
   Cloudinary URL parameters. See Cloudinary's documentation about image
   transformations: https://cloudinary.com/documentation/image_transformations"
  [params]
  (let [;; Apply some sensible defaults.
        params (-> params
                   ;; Set a default crop strategy of "c_crop"
                   (assoc :c (:c params "crop")))
        zero-width-or-height? (fn [k v]
                                (and (zero? v) (#{:w :h} k)))]
    (join "," (filter seq (map (fn [[k v]]
                                 (when (and v (not (zero-width-or-height? k v)))
                                   (str (name k) "_" v)))
                               params)))))


;; TODO we may need version if we need to fall back to the full URL.
(defn- params->url [{:keys [bucket filename transforms #_version]}]
  (let [segments (conj [bucket "image/upload"]
                       (join "/" (map params->url-segments transforms))
                       filename)]
    (str "https://res.cloudinary.com/" (join "/" (filter seq segments)))))

(defmulti url #(or (:mode %) :scale))

(defmethod url :crop [params]
  (let [manual-crop (select-keys params [:x :y :w :h])
        [w h] (:target-size params)
        scale (merge {:w w :h h :c "scale"} (:rescale-ops params))]
    (params->url (merge params {:transforms [manual-crop scale]}))))

(defmethod url :scale [params]
  (let [[w h] (:target-size params)
        crop {:w w
              :h h
              :c (:c params "lfill")}]
    (params->url (merge params {:transforms [crop]}))))


(comment

  (params->url-segments {:w 150 :h 100 :c "scale"})
  (params->url-segments {:w 150 :h 100 :c "lfill"})

  (url {:mode :crop
        :bucket "ctamayo"
        :x 0
        :y 0
        :w 2000
        :h 1650
        ;; no target size
        :filename "cumulus-test/grasshopper.jpg"})
  ;; => "https://res.cloudinary.com/ctamayo/image/upload/w_2000,h_1650,c_crop/c_scale/cumulus-test/grasshopper.jpg"

  (url {:mode :crop
        :bucket "ctamayo"
        :x 450
        :y 500
        :w 2000
        :h 1650
        :target-size [1000]
        :filename "cumulus-test/grasshopper.jpg"})
  ;; => "https://res.cloudinary.com/ctamayo/image/upload/x_450,y_500,w_2000,h_1650,c_crop/w_1000,c_scale/cumulus-test/grasshopper.jpg"

  (url {:mode :scale
        :bucket "ctamayo"
        :target-size [2000 1650]
        :filename "cumulus-test/grasshopper.jpg"})
  ;; => "https://res.cloudinary.com/ctamayo/image/upload/w_2000,h_1650,c_lfill/cumulus-test/grasshopper.jpg"

  (url {:mode :scale
        :bucket "ctamayo"
        :target-size [2000 1650]
        :c "lfill"
        :filename "cumulus-test/grasshopper.jpg"})
  ;; => "https://res.cloudinary.com/ctamayo/image/upload/w_2000,h_1650,c_lfill/cumulus-test/grasshopper.jpg"

  (url {:mode :scale
        :bucket "ctamayo"
        :target-size [2000 1650]
        :c "fit"
        :filename "cumulus-test/grasshopper.jpg"})
  ;; => "https://res.cloudinary.com/ctamayo/image/upload/w_2000,h_1650,c_fit/cumulus-test/grasshopper.jpg"

  ;;
  )