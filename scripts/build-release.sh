#!/usr/bin/env bash

RED="tput setaf 1"
BOLD="tput bold"
RESET="tput sgr 0"


function usage() {
  echo 'Usage:'
  echo
  echo '  build-release.sh RELEASE'
  echo
  echo 'RELEASE: the name of the release, e.g. "v1.2.3"'
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
  if ! [[ -f ./cumulus.php ]] ; then
    fail 'Error: not in root cumulus directory?'
  fi

  RELEASE="$1"

  if [[ -z "$RELEASE" ]] ; then
    fail 'Error: no release number specified'
  fi

  # prompt for the letter "v"
  first_char="${RELEASE:0:1}"
  if ! [[ "$first_char" = 'v' ]] ; then
    if [[ -z $NO_INTERACTION ]] ; then
      read -p "Prepend a 'v' (v${RELEASE})? (y/N) " prepend
    fi
    if [[ "$prepend" = "y" ]] ; then
      RELEASE="v${RELEASE}"
    fi
  fi

  if [[ -z $NO_INTERACTION ]] ; then
    # check tag
    git rev-parse --verify "$RELEASE" 2>/dev/null
    if ! [[ "$?" -eq 0 ]] ; then

      # prompt for creating a tag
      read -p "'${RELEASE}' is not a Git revision. Create tag ${RELEASE}? (y/N) " create
      if ! [[ "$create" = "y" ]] ; then
        echo 'aborted.'
        exit
      fi
    fi
  fi

  backup_vendor

  # Build JS dist.
  shadow-cljs release main

  # Make sure we have the latest CSS for ReactCrop
  cp node_modules/react-image-crop/dist/ReactCrop.css dist/css/react-image-crop.css

  # Copy plugin CSS
  cp dev/css/main.css dist/css/main.css

  tar_name="cumulus-${RELEASE}.tar.gz"
  zip_name="cumulus-${RELEASE}.zip"
  composer install --no-dev --prefer-dist

  # Create a symlink cumulus directory, so that when extracted, the
  # archives we create have a top-level directory.
  ln -sfn . cumulus

  # archive plugins distro files inside a top-level cumulus/ dir
  tar -cvzf "$tar_name" \
    cumulus/dist \
    cumulus/cumulus.php \
    cumulus/api.php \
    cumulus/wp-cli.php \
    cumulus/vendor \
    cumulus/views \
    cumulus/LICENSE.txt \
    cumulus/README.txt

  # ditto for zip
  zip -r "${zip_name}" \
    cumulus/dist \
    cumulus/cumulus.php \
    cumulus/api.php \
    cumulus/wp-cli.php \
    cumulus/vendor \
    cumulus/views \
    cumulus/LICENSE.txt \
    cumulus/README.txt

  # remove hackish symlink
  rm -rf ./cumulus

  restore_vendor

  echo "Created ${tar_name}, ${zip_name}"

  if [[ -z $NO_INTERACTION ]] ; then
    create_github_release "$RELEASE" "$tar_name" "$zip_name"
  fi
}

function create_github_release() {
  if [[ $(which hub) ]] ; then
    echo $($BOLD)hub detected! You win at Git!$($RESET)
    read -p 'Create a GitHub release? (y/N) ' create
    if [[ "$create" = "y" ]] ; then
      echo 'pushing latest changes and tags...'
      git push origin main
      git push --tags
      hub release create --prerelease \
        --attach="$2" \
        --attach="$3" \
        --edit --message="$1" \
        "$1"
    else
      echo 'skipping GitHub release.'
    fi
  fi
}

function backup_vendor() {
  echo 'backing up vendor...'
  if [[ -d vendor ]] ; then
    mv vendor vendor.bak
  fi
}

function restore_vendor() {
  echo 'restoring vendor...'
  if [[ -d vendor.bak ]] ; then
    rm -rf vendor
    mv vendor.bak vendor
  fi
}



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
  -n|--no-interaction)
    NO_INTERACTION=1
    shift # past flag
    ;;
  *)
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters



main "$@"
