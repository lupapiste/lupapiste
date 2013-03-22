var loc;

;(function() {
  "use strict";

  function not(v) { return !v; }
  
  loc = function() {
    var term, i, len, key = arguments[0];

    if (_.some(arguments, not)) return null;
    
    if (arguments.length > 1) {
      len = arguments.length;
      for (i = 1; i < len; i++) {
        key = key + "." + arguments[i];
      }
    }

    term = loc.terms[key];

    if (term === undefined) {
      debug("Missing localization key", key);
      return "$$NOT_FOUND$$" + key;
    }
    
    return term;
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

  function resolveLang() {
    var url = window.parent ? window.parent.location.pathname : location.pathname;
    var langEndI = url.indexOf("/", 5);
    var lang = langEndI > 0 ? url.substring(5, langEndI) : null;
    return _.contains(loc.supported, lang) ? lang : "fi";
  }

  loc.setTerms = function(newTerms) {
    loc.supported = _.keys(newTerms);
    loc.currentLanguage = resolveLang();
    loc.terms = newTerms[loc.currentLanguage];
  };

  // FIXME: This does not work with new localizations.
  loc.toMap = function() { return loc.terms.error; };

  loc.getCurrentLanguage = function() { return loc.currentLanguage; };
  loc.getSupportedLanguages = function() { return loc.supported; };

})();
