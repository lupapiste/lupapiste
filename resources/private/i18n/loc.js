var loc;

;(function() {
  "use strict";

  function not(v) { return !v; }

  loc = function() {
    var term, i, len, key = arguments[0];

    if (_.some(arguments, not)) return null;

    if (arguments.length > 1) {


      // jari TODO: Testausta varten,    TODO:  poista tämä ->
      debug("Korjaa taman lokalisaation kaytto koodissa: ", arguments );
      return "$$Korjaa-taman-lokalisaation-kaytto-koodissa$$:" + arguments
      // <- jari TODO

      len = arguments.length;
      for (i = 1; i < len; i++) {
        key = key + "." + arguments[i];
      }
    }

    term = loc.terms[key];

    if (term === undefined) {
      debug("Missing localization key", key);
      return LUPAPISTE.config.mode === "dev" ? "$$NOT_FOUND$$" + key : "???";
    }

    return term;
  };

  loc.getFormatted = function() {
    var args = Array.prototype.slice.call(arguments);
    var key = args[0];
    var params = args.slice(1);
    var term = loc.terms[key];

    if (_.some(arguments, not)) return null;

    if (term !== undefined) {

      if (_.isEmpty(params)) {
        return term;
      } else {
        var paramsStr = _.map(params, String);
        var formatted = term;
        for(var argIndex in paramsStr) {
          formatted = formatted.replace('{' + argIndex + '}', paramsStr[argIndex]);
        }
        return formatted;
      }

    } else {
      debug("Missing localization key", key);
      return LUPAPISTE.config.mode === "dev" ? "$$NOT_FOUND$$" + key : "???";
    }
  };



  hub.subscribe("change-lang", function(e) {
    var lang = e.lang;
    if (_.contains(loc.supported, lang)) {
      var url = location.href.replace("/app/" + loc.currentLanguage + "/", "/app/" + lang + "/");
      window.location = url;
    }
  });

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

})();
