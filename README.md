# Cumulus

A WordPress plugin for managing custom image crops through Cloudinary.

Store just the URLs for each crop of each image. Never worry about regenerating custom crops again: let Cloudinary take care of that for you, on the fly!

## How it works

Cumulus works by saving Cloudinary image URLs to the database for each of your images' crop sizes. It overwrites zero data, only saving extra data in the database. That means you can disable it any time and switch seamlessly back to serving your images from WordPress.

Inspired by [YoImages](https://wordpress.org/plugins/yoimages/), the Cumulus user interface allows you to specify a custom crop for each image _at each size_, so you don't have to worry about awkward stuff like parts of faces being cut off in automatic crops. Cumulus uses Cloudinary's dynamic `lfill`, or "limited fill" setting, as a sensible fallback when no custom crop is specified for a given size. Plus, by serving your images from Cloudinary's Content Delivery Network, you can serve your images at some of the highest speeds available on the web, and reduce load on your servers.

Here's an illustration of how it works internally:

![The Cumulus comic shows a WP Admin user specifying that for their cat.jpb image at custom_size, they want a specific crop. A friendly robot representing the Cumulus plugin says YES HUMAN. Cumulus the Robot in turn asks WordPress to "please store cloudinary_url for attachment 123 at custom_size," and WordPress responds, "You got it!" Later, a user requests a page from your website. WordPress thinks to itself, "Better run the wp_get_attachment_image_src filter" and says "here ya go" to the end user, serving them HTML containing the cloudinary_url. Finally, the user's browser requests cloudinary_url from Cloudinary, and hence flow the bits comprising cat.jpg!](https://raw.githubusercontent.com/sitecrafting/cumulus/main/cumulus-comic.png)

## Installation

Go to the GitHub [releases page](https://github.com/sitecrafting/cumulus/releases/) and download the .zip archive of the latest release. Make sure you download the release archive, **not** the source code archive. For example, if the latest release is called `v0.x.x`, click the download link that says **cumulus-v0.x.x.zip**. (You can also use the `tar.gz` archive if you want - they are the same code.)

Once downloaded and unzipped, place the extracted directory in `wp-content/plugins`. Activate the plugin from the WP Admin as you normally would.

### Via Composer

TODO

## Usage

### Managing Image Resizes

Once you activate the plugin, in the Attachment Details for each item in the Media Library, you will see a **Customize Image Crops** button:

![Customize Image Crops from Attachment Details screen](https://raw.githubusercontent.com/sitecrafting/cumulus/main/img/attachment-details.png)

Clicking on this button brings you to the **Customize Image Crops** screen, which shows you the default auto-scaled image for each crop size. Here it is displaying the auto-scaled thumbnail version of our image:

![Scaling an image to thumbnail inside the tool](https://raw.githubusercontent.com/sitecrafting/cumulus/main/img/default-scale-thumbnail.png)

Leaving it at this setting will cause this auto-scaled image to be served on the frontend for the **Thumbnail** crop size:

![Thumbnail image crop](https://raw.githubusercontent.com/sitecrafting/cumulus/main/img/heron-scaled-thumbnail.jpg)

Note that WordPress does not need to regenerate the image to do this. Cloudinary generates the correctly scaled image based on the URL alone!

Switching to **Crop** mode enables the manual cropping tool:

![Customizing Image Crops manually](https://raw.githubusercontent.com/sitecrafting/cumulus/main/img/customize-image-crops.png)

Saving this setting will similarly tell WordPress to apply this exact crop to the original image, and then scale down the derived image to the appropriate dimensions for the image size (in this case **Thumbnail**):

![Manually cropping for the Thumbnail size](https://raw.githubusercontent.com/sitecrafting/cumulus/main/img/heron-cropped-thumbnail.jpg)

Each setting for each image size is maintained independently, so for this one image, if you want to set a manual crop for one image size but let the other sizes auto-scale, Cumulus lets you do that.

### Managing Cloudinary Settings

Currently there is no admin settings UI. This will be implemented in a future version.

For now, there are a few ways to manage your Cumulus settings:

* Use standard WP options set through [WP-CLI `wp option` commands](https://developer.wordpress.org/cli/commands/option/)

  ```
  wp option set cumulus_cloud_name my-cloud
  wp option set cumulus_api_key my-api-key
  wp option set cumulus_api_secret my-secret
  wp option set cumulus_folder my-folder
  ```

* PHP constants:

  ```php
  define('CUMULUS_CLOUD_NAME', 'my-cloud');
  define('CUMULUS_API_KEY',    'my-api-key');
  define('CUMULUS_API_SECRET', 'my-api-secret');
  define('CUMULUS_FOLDER',     'my-folder');
  ```

* PHP environment variables:

  ```php
  $_ENV['CUMULUS_CLOUD_NAME'] = 'my-cloud';
  $_ENV['CUMULUS_API_KEY']    = 'my-api-key';
  $_ENV['CUMULUS_API_SECRET'] = 'my-api-secret';
  $_ENV['CUMULUS_FOLDER']     = 'my-folder';
  ```

Settings are resolved in that order so for example if the `CUMULUS_API_KEY` constant is defined, Cumulus does not consider the environment variable even if it is also set. By that same token, if the `cumulus_cloud_name` option is found in the database, neither the constant nor the environment variable are considered.

## Actions and Filters

**TODO**

## Development

This repo was based on the [Shadow CLJS Browser Quickstart](https://github.com/shadow-cljs/quickstart-browser.git) template.

### Required Software

These are the baseline requirements for the dev environment:

- [Lando](https://docs.lando.dev) for running the WordPress test environment
- [node.js (v6.0.0+)](https://nodejs.org/en/download/)
- [Yarn](https://yarnpkg.com/)
- [Java JDK (8+)](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or [Open JDK (8+)](http://jdk.java.net/10/)
- Recommended for Clojure(Script) beginners: [VS Code](https://code.visualstudio.com/) with the [Calva](https://calva.io/) extension

It's also worth it to familiarize yourself with [shadow-cljs](https://shadow-cljs.org/), the ClojureScript compiler.

### Setup

```bash
git clone git@github.com/sitecrafting/cumulus.git quickstart
cd quickstart
yarn
yarn dev
```

This runs the `shadow-cljs` server process which all following commands will talk to. Just leave it running and open a new terminal to continue.

The first startup takes a bit of time since it has to download all the dependencies and do some prep work. Once this is running we can get started.

### Starting WordPress

The WordPress test site runs inside Lando. To start it, just run:

```sh
lando start
```

This will install WP Core and activate the Cumulus plugin within the install. It will print out your local dev site URLs when it's done.

To build JavaScript assets for the Lando WP install, run:

```
yarn build
```

This will compile the JS code to `dist/js/main.js`.

#### Watching files

```bash
yarn shadow watch main
```

The `main` here refers to the name of the build; see the `:builds` map inside `shadow-cljs.edn` for exactly how this is configured. The above command will begin the compilation of the `:main` build and re-compile whenever you change a file.

When you see a "Build completed" message your build is ready to be used.

```txt
[:app] Build completed. (23 files, 4 compiled, 0 warnings, 7.41s)
```

You can now open [http://localhost:8008](http://localhost:8008).

#### Watching files: an alternative

Instead of `yarn shadow watch main`, you can also trigger watching files from the browser by going to http://localhost:9630/builds and clicking **start watch**.

The same goes for the **test** build (see below).

### Tests

ClojureScript unit tests run in the browser. To watch src and test files, run:

```
yarn shadow watch test
```

Then go to [http://localhost:8007/](http://localhost:8007/). It will run all tests whenever the ClojureScript compiler detects a change. You can even set it to alert you via desktop notifications.

## REPL

The Read-Eval Print Loop, or REPL, is an extremely useful dev tool and is the basis for "REPL-driven" development.

If you have run `yarn dev` and are watching the `main` build, you can start a REPL straight from your editor with most IDEs. This guide will cover how to do so with VS Code

## Release

Start by updating the release number in the `cumulus.php` header comment:

```php
/**
 * Plugin Name: Cumulus: Cloudinary Image Crops
 * Description: Serve your custom image crops from Cloudinary Image CDN
 * Version: 0.1.0 <-- UPDATE THIS
 * ...
 */
```

Make sure you commit this change before creating the actual release. Otherwise your release download will not include this update.

The `scripts/build-release.sh` tool creates a build archive that can be uploaded to GitHub as part of a release.

To create a release called `vX.Y.Z`, run:

```sh
scripts/build-release.sh vX.Y.Z
```

This will create a .tar.gz and a .zip archive which you can upload to a new release on GitHub.

If you have [`hub`](https://hub.github.com/) installed, it will also prompt you to optionally create a GitHub  release for you directly!

## TODO

* Support bulk upload of existing Media Library
* Admin Settings UI
* Deletion settings
* PHPUnit tests
* Document hooks
* Internationalization
* Submit to WordPress plugin repository
