var loc;

;(function() {
  "use strict";

  function notValidLocParam(v) { return v === undefined || v === null || v === ""; }
  function joinTermArray(key) {
    if (_.isArray(key)) {
      if (_.some(key, notValidLocParam)) {
        return null;
      }
      key = key.join(".");
    }
    return key;
  }

  loc = function() {
    var args = Array.prototype.slice.call(arguments);

    var key = args[0];
    if (!key) {
      return null;
    }

    key = joinTermArray(key);
    if (!key) {
      return null;
    }

    var formatParams = args.slice(1);
    var term = loc.terms[key];

    if (term !== undefined) {
      _.each(formatParams, function(value, argIndex) {
        term = term.replace("{" + argIndex + "}", value);
      });
    } else {
      error("Missing localization key", key);
      term = LUPAPISTE.config.mode === "dev" ? "$$NOT_FOUND$$" + key : "";
    }

    return term;
  };

  loc.supported = LUPAPISTE.config.supportedLangs;
  loc.terms = {};
  loc.defaultLanguage = "fi";

  loc.hasTerm = function(key) {
    return loc.terms[joinTermArray(key)] !== undefined;
  };

  function resolveLangFromUrl() {
    var url = window.parent ? window.parent.location.pathname : location.pathname;
    var langEndI = url.indexOf("/", 5);
    return langEndI > 0 ? url.substring(5, langEndI) : null;
  }

  function resolveLangFromDocument() {
    var htmlRoot = document.getElementsByTagName("html")[0];
    return htmlRoot ? htmlRoot.lang : null;
  }

  function resolveLang() {
    var langFromDocument = resolveLangFromDocument();
    var lang = langFromDocument ? langFromDocument : resolveLangFromUrl();
    return _.includes(loc.supported, lang) ? lang : loc.defaultLanguage;
  }

  loc.currentLanguage = resolveLang();

  loc.setTerms = function(terms) {
    loc.terms = terms;
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
    if (_.includes(loc.supported, lang)) {
      var url = location.href.replace("/app/" + loc.currentLanguage + "/", "/app/" + lang + "/");
      window.location = url;
    }
  });

})();

