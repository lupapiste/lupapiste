var loc;

;(function() {
  "use strict";

  var defaultLanguage = "fi";
  var supportedLanguages = ["fi", "sv"];

  function resolveLanguage() {
    var url = location.pathname;
    if (window.parent) {
      url = window.parent.location.pathname;
    }

    var langEndI = url.indexOf("/", 1);
    if (langEndI > 0) {
      var lang = url.substring(1, langEndI);
      if (_.contains(supportedLanguages, lang)) {
        return lang;
      }
    }
    // TODO remove
    debug("Returning default language", defaultLanguage);
    return defaultLanguage;
  }

  var currentLanguage = resolveLanguage();

  var terms = {"fi": {}, "sv": {}};
  
  function registerTerms(lang, localizedTerms) {
    terms[lang] = _.extend(terms[lang], localizedTerms);
  }

  function getIn(m, keyArray) {
    if (m && keyArray && keyArray.length > 0) {
      var key = keyArray[0];
      var val = m[key];
      if (typeof val === "string") {
        return val;
      }
      return getIn(val, keyArray.splice(1, keyArray.length - 1));
    }
  }

  hub.subscribe("change-lang", function(e) {
    var lang = e.lang;
    if (_.contains(supportedLanguages, lang)) {
      var pattern = "\/" + currentLanguage + "\/";
      var url = location.href.replace(pattern, "/" + lang + "/");
      window.location = url;
    }
  });

  function getTerm(key) {
    if (!key) { return; }
    var keyArray = key.split(/\./);
    return getIn(terms[currentLanguage], keyArray);
  }

  loc = function(key) {
    var term = getTerm(key);
    if (term === undefined) {
      debug("Missing localization key", key);
      return "$$NOT_FOUND$$" + key;
    }
    return term;
  };

  loc.termExists = function(key) {
    return getTerm(key) !== undefined;
  };

  loc.toMap = function() { return terms[currentLanguage].error; };

  loc.getCurrentLanguage = function() {return currentLanguage;};
  loc.getSupportedLanguages = function() {return supportedLanguages;};
  loc.registerTerms = registerTerms;
})();
