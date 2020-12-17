#!/bin/bash

# MAIN TASK
# Install and configure WordPress if we haven't already
main() {
  BOLD=$(tput bold)
  NORMAL=$(tput sgr0)

  if ! [[ -d /app/wp/wp-content ]] ; then
    wp core download --path=wp
  fi

  # Symlink the cumulus plugin directory to the repo root.
  if ! [[ -d /app/wp/wp-content/plugins/cumulus ]] ; then
    echo 'Linking cumulus plugin directory...'
    ln -s ../../../ /app/wp/wp-content/plugins/cumulus
  fi

  echo 'Checking for WordPress config...'
  if wp_configured ; then
    echo 'WordPress is configured'
  else
    read -d '' extra_php <<'EOF'
// log all notices, warnings, etc.
error_reporting(E_ALL);

// enable debug logging
define('WP_DEBUG', true);
define('WP_DEBUG_LOG', true);
define('WP_DEBUG_DISPLAY', false);
EOF

    # Create a wp-config.php based on Lando WordPress recipe defaults:
    # https://docs.lando.dev/config/wordpress.html#connecting-to-your-database
    wp config create \
      --dbname="wordpress" \
      --dbuser="wordpress" \
      --dbpass="wordpress" \
      --dbhost="database" \
      --extra-php < <(echo "$extra_php")
  fi

  echo 'Checking for WordPress installation...'
  if wp_installed ; then
    echo 'WordPress is installed'
  else
    # install WordPress
    wp core install \
      --url='https://cumulus.lndo.site' \
      --title='Cumulus' \
      --admin_user='cumulus' \
      --admin_password='cumulus' \
      --admin_email='hello@sitecrafting.com' \
      --skip-email
  fi

  # configure plugins and theme
  uninstall_plugin akismet
  uninstall_plugin hello
  wp plugin activate cumulus advanced-custom-fields

  # symlink the test theme
  if [[ ! -d wp/wp-content/themes/cumulus ]] ; then
    ln -s ../../../test/theme wp/wp-content/themes/cumulus
  fi

  wp theme activate cumulus

  wp option set permalink_structure '/%postname%/'
  wp rewrite flush

  echo
  echo $BOLD'Done setting up!'$NORMAL
  echo
  echo 'Your WP username is: cumulus'
  echo 'Your password is: cumulus'
  echo

}


# Detect whether WP has been configured already
wp_configured() {
  [[ $(wp config path 2>/dev/null) ]] && return
  false
}

# Detect whether WP is installed
wp_installed() {
  wp --quiet core is-installed
  [[ $? = '0' ]] && return
  false
}

uninstall_plugin() {
  wp plugin is-installed $1 2>/dev/null
  if [[ "$?" = "0" ]] ; then
    wp plugin uninstall $1
  fi
}


main
