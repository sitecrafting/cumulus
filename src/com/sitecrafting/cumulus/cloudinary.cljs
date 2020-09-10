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
                   (assoc :c (:c params "crop")))]
    (join "," (filter seq (map (fn [[k v]]
                                 (when v
                                   (str (name k) "_" v)))
                               params)))))


(defn crop->url [{:keys [bucket folder filename #_version] :as params}]
  (let [segments (conj [bucket "image/upload"]
                       (params->url-segments (select-keys params [:x :y :w :h]))
                       folder
                       filename)]
    (str "https://res.cloudinary.com/" (join "/" (filter seq segments)))))