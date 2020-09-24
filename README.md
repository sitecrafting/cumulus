# Cumulus

A WordPress plugin for managing custom image crops through Cloudinary.

Store just the URLs for each crop of each image. Never worry about regenerating custom crops again: let Cloudinary take care of that for you, on the fly!

## How it works

Cumulus works by saving Cloudinary image URLs to the database for each of your images' crop sizes. It overwrites zero data, only saving extra data in the database. That means you can disable it any time and switch seamlessly back to serving your images from WordPress.

Inspired by [YoImages](https://wordpress.org/plugins/yoimages/), the Cumulus user interface allows you to specify a custom crop for each image _at each size_, so you don't have to worry about awkward stuff like parts of faces being cut off in automatic crops. Cumulus uses Cloudinary's dynamic `lfill`, or "limited fill" setting, as a sensible fallback when no custom crop is specified for a given size. Plus, by serving your images from Cloudinary's Content Delivery Network, you can serve your images at some of the highest speeds available on the web, and reduce load on your servers.

Here's an illustration of how it works internally:

![The Cumulus comic shows a WP Admin user specifying that for their cat.jpb image at custom_size, they want a specific crop. A friendly robot representing the Cumulus plugin says YES HUMAN. Cumulus the Robot in turn asks WordPress to "please store cloudinary_url for attachment 123 at custom_size," and WordPress responds, "You got it!" Later, a user requests a page from your website. WordPress thinks to itself, "Better run the wp_get_attachment_image_src filter" and says "here ya go" to the end user, serving them HTML containing the cloudinary_url. Finally, the user's browser requests cloudinary_url from Cloudinary, and hence flow the bits comprising cat.jpg!](https://raw.githubusercontent.com/sitecrafting/cumulus/main/cumulus-comic.png)

## Development

**TODO** revise this documentation.

This repo was based on the [Shadow CLJS Browser Quickstart](https://github.com/shadow-cljs/quickstart-browser.git) template.

### Required Software

These are the baseline requirements for the dev environment:

- [node.js (v6.0.0+)](https://nodejs.org/en/download/)
- [Yarn](https://yarnpkg.com/)
- [Java JDK (8+)](http://www.oracle.com/technetwork/java/javase/downloads/index.html) or [Open JDK (8+)](http://jdk.java.net/10/)

```bash
git clone https://github.com/sitecrafting/cumulus.git quickstart
cd quickstart
yarn
yarn dev
```

This runs the `shadow-cljs` server process which all following commands will talk to. Just leave it running and open a new terminal to continue.

The first startup takes a bit of time since it has to download all the dependencies and do some prep work. Once this is running we can get started.

```bash
yarn shadow watch main
```

This will begin the compilation of the configured `:app` build and re-compile whenever you change a file.

When you see a "Build completed." message your build is ready to be used.

```txt
[:app] Build completed. (23 files, 4 compiled, 0 warnings, 7.41s)
```

You can now then open [http://localhost:8020](http://localhost:8020).

The app is only a very basic skeleton with the most useful development tools configured.

`shadow-cljs` is configured by the `shadow-cljs.edn` config. It looks like this:

```clojure
{:source-paths
 ["src"] ;; .cljs files go here

 :dependencies
 [] ;; covered later

 :builds
 {:app {:target :browser
        :output-dir "public/js"
        :asset-path "/js"

        :modules
        {:main ;; <- becomes public/js/main.js
         {:entries [starter.browser]}}

        ;; start a development http server on http://localhost:8020
        :devtools
        {:http-root "public"
         :http-port 8020}
        }}}
```

It defines the `:app` build with the `:target` set to `:browser`. All output will be written to `public/js` which is a path relative to the project root (ie. the directory the `shadow-cljs.edn` config is in).

`:modules` defines the how the output should be bundled together. For now we just want one file. The `:main` module will be written to `public/js/main.js`, it will include the code from the `:entries` and all their dependencies.

`:devtools` configures some useful development things. The `http://localhost:8020` server we used earlier is controlled by the `:http-port` and serves the `:http-root` directory.

The last part is the actual `index.html` that is loaded when you open `http://localhost:8020`. It loads the generated `/js/main.js` and then calls `start.browser.init` which we defined in the `src/start/browser.cljs`.

```html
<!doctype html>
<html>
<head><title>Browser Starter</title></head>
<body>
<h1>shadow-cljs - Browser</h1>
<div id="app"></div>

<script src="/js/main.js"></script>
<script>starter.browser.init();</script>
</body>
</html>
```

## Live reload

To see the live reload in action you can edit the `src/start/browser.cljs`. Some output will be printed in the browser console.

## REPL

During development it the REPL is very useful.

From the command line use `npx shadow-cljs cljs-repl app`.

```
shadow-cljs - config .../shadow-cljs.edn version: 2.2.16
shadow-cljs - connected to server
[2:1]~cljs.user=>
```

This can now be used to eval code in the browser (assuming you still have it open). Try `(js/alert "Hi.")` and take it from there. You might want to use `rlwrap npx shadow-cljs cljs-repl app` if you intend to type a lot here.

You can exit the REPL by either `CTRL+C` or typing `:repl/quit`.

## Release

The `watch` process we started is all about development. It injects the code required for the REPL and the all other devtools but we do not want any of that when putting the code into "production" (ie. making it available publicly).

The `release` action will remove all development code and run the code through the Closure Compiler to produce a minified `main.js` file. Since that will overwrite the file created by the `watch` we first need to stop that.

Use `CTRL+C` to stop the `watch` process and instead run `npx shadow-cljs release app`.

When done you can open `http://localhost:8020` and see the `release` build in action. At this point you would usually copy the `public` directory to the "production" web server.

Note that in the default config we overwrote the `public/js/main.js` created by the `watch`. You can also configure a different path to use for release builds but writing the output to the same file means we do not have to change the `index.html` and test everything as is.
