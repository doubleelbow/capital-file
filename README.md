# capital file [![CircleCI](https://circleci.com/gh/doubleelbow/capital-file.svg?style=svg)](https://circleci.com/gh/doubleelbow/capital-file) [![Clojars Project](https://img.shields.io/clojars/v/com.doubleelbow.capital/capital-file.svg)](https://clojars.org/com.doubleelbow.capital/capital-file)

Capital-file is a clojure library built on top of [capital](https://github.com/doubleelbow/capital) that provides cacheable file reads.

## Usage

Simple usage examples can be found in `dev/user.clj` and at [capital example](https://github.com/doubleelbow/capital-example).

Currently there are two public functions (`initial-context` and `read!`) in `com.doubleelbow.capital.file.alpha` namespace. `initial-context` creates a context (based on config map - explained later) which is then passed to `read!` along with absolute or relative file path.

```clojure
;; config map
{::base-path "path/to/base/dir"
 ::read-opts read-opts}
```

`::base-path` should be a string path either relative or absolute and is used when `read!` is executed with relative file path as first parameter. `::base-path` is optional in `config map`. If it doesn't exist current directory is set as base.

```clojure
;; read-opts
{::nonexistent ""
 ::cache cache-config
 ::format-config format-config}
```

`::nonexistent` should be a string that is returned as a substitute for file content if the file does not exist. This returned string is then subject to formatting. If `::nonexistent` is not present in `read-opts` map and also not in optional config parameter to `read!` function, then `FileNotFoundException` is thrown if desired file does not exist.

```clojure
;; cache-config
{::use-cache? true
 ::duration 1800
 ::check-if-newer? true}
```

`::use-cache?` is a (required but superfluous and soon to be removed) boolean denoting if cache should be used. It can be overriden with `::use-cache?` key in optional config parameter to `read!` function.

`::duration` states for how many seconds a content should be cached.

`::check-if-newer?` is a boolean. When set to true and cached file has changed the cache is not used even if the `::duration` has not expired yet.

```clojure
;; format-config
{"extension" format-fn}
```

`format-config` is a map with strings, that match file extension, as keys and 1-arity functions, that receives string content as parameter and should return parsed content, as values. In the process of initializing a context `format-config` map is merged with default formatters for "txt", "edn" and "xml" files. `format-config` map overrides defaults.

## Contributing

If you've found a bug or have an idea for new feature in mind, please start by creating a github issue. All contributions are welcome.

If you'd like to help but don't know where to start, here are some suggestions:

* cache interceptor should depend on capital's time interceptor to get current time
* `::use-cache?` is not needed in `cache-config` map
* add `write!` function
* documentation
* specs

## License

Copyright © 2018 doubleelbow

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
