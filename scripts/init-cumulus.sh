#!/usr/bin/env bash

RED="tput setaf 1"
BOLD="tput bold"
RESET="tput sgr 0"


function usage() {
  echo 'Usage:'
  echo
  echo '  init-cumulus.sh [OPTIONS]'
  echo
  echo 'OPTIONS:'
  echo
  echo '  -c|--cloud <cloud_name>'
  echo '  -k|--key <api_key>'
  echo '  -s|--secret <api_secret>'
  echo
}

function fail() {
  echo $($RED; $BOLD)
  echo "$1"
  echo $($RESET)
  usage
  exit 1
}


function main() {
  wp option set cumulus_cloud_name "$CUMULUS_CLOUD_NAME"
  wp option set cumulus_api_key "$CUMULUS_API_KEY"
  wp option set cumulus_api_secret "$CUMULUS_API_SECRET"
  wp option set cumulus_folder "$CUMULUS_FOLDER"

  # set up home page
  home_id=$(wp post create --post_type=page --post_title="Home" --post_status=publish --porcelain)
  wp option set page_on_front $home_id
  wp option set show_on_front page
  wp rewrite flush

  grasshopper=$(wp media import dev/img/grasshopper.jpg --porcelain \
    --title="Grasshopper" \
    --alt="Grasshopper by Coby")

  spider=$(wp media import dev/img/spider.jpg --porcelain \
    --title="Spider" \
    --alt="Spider by Coby")

  heron=$(wp media import dev/img/heron.jpg --porcelain \
    --title="Heron" \
    --alt="Heron by Coby")

  grasshopper_pdf=$(wp media import dev/img/grasshopper.pdf --porcelain \
    --title="Grasshopper PDF" \
    --alt="Grasshopper PDF by Coby" \
    --caption="This should NOT have been uploaded due to its MIME type")

  wp post meta set $home_id test_image_1 $grasshopper
  wp post meta set $home_id test_image_2 $spider
  wp post meta set $home_id test_image_3 $heron

  wp media import dev/img/sculpture.jpg --title="Sculpture" \
    --caption="Photo Credit: Chris Robinson https://www.flickr.com/people/11829757@N02" \
    --alt="A sculpture at the entrance to the Palace of Versaille"

  wp plugin deactivate cumulus
  wp media import dev/img/orca.jpg --title="Orca" \
    --alt="Orca by Coby" \
    --desc="This image does not have cumulus_image metadata because it was uploaded while cumulus was disabled."
  wp plugin activate cumulus
}


# NOTE: we have to do this **before** we parse args, so CLI opts can
# override values from .env
if [[ -f .env ]] ; then
  source .env
fi

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
  -h|--help)
    # show usage and bail
    usage
    exit
    ;;
  -c|--cloud)
    CUMULUS_CLOUD_NAME="$2"
    shift # past flag
    shift # past value
    ;;
  -k|--key)
    CUMULUS_API_KEY="$2"
    shift # past flag
    shift # past value
    ;;
  -s|--secret)
    CUMULUS_API_SECRET="$2"
    shift # past flag
    shift # past value
    ;;
  -f|--folder)
    CUMULUS_FOLDER"$2"
    shift # past flag
    shift # past value
    ;;
  *)
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters


main "$@"
