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
  wp option set cumulus_cloud_name "$CLOUD_NAME"
  wp option set cumulus_api_key "$API_KEY"
  wp option set cumulus_api_secret "$API_SECRET"
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
    CLOUD_NAME="$2"
    shift # past flag
    shift # past value
    ;;
  -k|--key)
    API_KEY="$2"
    shift # past flag
    shift # past value
    ;;
  -s|--secret)
    API_SECRET="$2"
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