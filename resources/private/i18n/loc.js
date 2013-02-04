var loc;

;(function() {

  loc = function(key) {
    var term = loc.terms[key];
    if (term === undefined) {
      debug("Missing localization key", key);
      return "$$NOT_FOUND$$" + key;
    }
    return term;
  };
  
  loc.terms = {}; // Will be overwritten by i18n.clj generated content.
  loc.supported = [];

  var defaultLanguage = "fi";
  var currentLanguage = null;

  loc.setTerms = function(newTerms) {
    loc.supported = _.keys(newTerms);
      
    var url = location.pathname;
    if (window.parent) url = window.parent.location.pathname;

    var lang = null;
    
    var langEndI = url.indexOf("/", 1);
    if (langEndI > 0) {
      var l = url.substring(1, langEndI);
      lang = _.contains(loc.supported, l) ? l : null;
    }

    if (lang == null) {
      debug("Returning default language", defaultLanguage);
      lang = defaultLanguage;
    }

    loc.terms = newTerms[lang];
  }

  hub.subscribe("change-lang", function(e) {
    var lang = e.lang;
    if (loc.terms[lang]) {
      var pattern = "/" + currentLanguage + "/";
      var url = location.href.replace(pattern, "/" + lang + "/");
      window.location = url;
    }
  });

  loc.termExists = function(key) {
    return loc.terms[key] !== undefined;
  };

  // FIXME: Called before lang is set, and does not react to lang changes.
  loc.toMap = function() { return loc.terms[currentLanguage] ? loc.terms["error"] : {}; };

  loc.getCurrentLanguage = function() { return currentLanguage; };
  loc.getSupportedLanguages = function() { return loc.supported; };
  
})();
