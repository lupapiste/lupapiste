var loc;

;(function() {
  "use strict";

  function notValidLocParam(v) { return v === undefined || v === null || v === ""; }

  loc = function() {
    var args = Array.prototype.slice.call(arguments);
    if (_.some(args, notValidLocParam)) {
      //debug("Not valid loc params found, the arguments passed for loc: ", args);
      return null;
    }
    var key = args[0];
    var params = args.slice(1);
    var formatParams = undefined;

    // If we only got key, return the term corresponding it.
    // If the argument after key, in index 1, is an array,
    //   the keys in the array are concatenated with the key using '.' as a separator.
    // Otherwise, extra parameters are used to format the key.

    if (!_.isEmpty(params)) {

      if (_.isArray(params[0])) {
        var concatParams = params[0];
        if (_.some(concatParams, notValidLocParam)) {
          //debug("Not valid loc params found, key & params: ", key, concatParams);
          return null;
        }
        for (var i in concatParams) {
          key = key + "." + concatParams[i];
        }
      } else {
        formatParams = params;
      }
    }

    var term = loc.terms[key];

    if (term !== undefined) {
      // If we have some format params, lets format the key with the params.
      if (formatParams !== undefined) {
        var formatParamsStr = _.map(formatParams, String);
        for(var argIndex in formatParamsStr) {
          term = term.replace('{' + argIndex + '}', formatParamsStr[argIndex]);
        }
      }
      return term;
    } else {
      debug("Missing localization key", key);
      return LUPAPISTE.config.mode === "dev" ? "$$NOT_FOUND$$" + key : "???";
    }
  };

  loc.supported = [];
  loc.currentLanguage = null;
  loc.terms = {};
  loc.defaultLanguage = "fi";

  loc.hasTerm = function(key) {
    return loc.terms[key] !== undefined;
  };

  function resolveLang() {
    var url = window.parent ? window.parent.location.pathname : location.pathname;
    var langEndI = url.indexOf("/", 5);
    var lang = langEndI > 0 ? url.substring(5, langEndI) : null;
    return _.contains(loc.supported, lang) ? lang : loc.defaultLanguage;
  }

  loc.setTerms = function(newTerms) {
    loc.supported = _.keys(newTerms);
    loc.currentLanguage = resolveLang();
    loc.terms = newTerms[loc.currentLanguage];
  };

  loc.getErrorMessages = function() {
    var errorKeys = _.filter(_.keys(loc.terms), function(term) {
      return term.indexOf("error.") === 0;
    });

    var errorMessages = {};
    _.each(errorKeys, function(key) {
      var errorType = key.substr(6);
      errorMessages[errorType] = loc(key);
    });

    return errorMessages;
  };

  loc.getCurrentLanguage = function() { return loc.currentLanguage; };
  loc.getSupportedLanguages = function() { return loc.supported; };
  loc.getNameByCurrentLanguage = function(obj) {
    if(obj.name) {
      if(loc.currentLanguage in obj.name) {
        return obj.name[loc.currentLanguage];
      } else if(loc.defaultLanguage in obj.name) {
        return obj.name[loc.defaultLanguage];
      }
    }
    return "$$noname$$";
  };


  hub.subscribe("change-lang", function(e) {
    var lang = e.lang;
    if (_.contains(loc.supported, lang)) {
      var url = location.href.replace("/app/" + loc.currentLanguage + "/", "/app/" + lang + "/");
      window.location = url;
    }
  });

})();
