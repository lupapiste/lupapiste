(function() {
  "use strict";

  function Languages() {
    var self = this;
    self.languages = loc.getSupportedLanguages();
    self.languageMenuVisible = ko.observable(false);
    self.currentLanguage = loc.getCurrentLanguage();
    self.changeLanguage = function( lang ) {
      if( lang !== self.currentLanguage ) {
        hub.send("change-lang", { lang: lang });
      }
      self.languageMenuVisible( false );
    };
    self.toggleLanguageMenu = function() {
      self.languageMenuVisible( !self.languageMenuVisible() );
    };

    var cancel =   _.partial( self.languageMenuVisible, false);

    self.externalApiInUse = ko.pureComputed( function() {
      var api = lupapisteApp.models.rootVMO.externalApi;
      return api && api.enabled();
    });

    // We cancel (aka close the menu), on dialog-close event
    // that denotes user pressing esc key and clicks outside
    // of menu area.
    hub.subscribe( "dialog-close", cancel);
    $(document).on( "click", "#language-select", function( e ) {
      e.stopPropagation();
    });
    $(document).on( "click", cancel );
  }

  var langs = new Languages();

  $(function() {
    $("#language-select").applyBindings(langs);
    if (LUPAPISTE.Screenmessage) {
      LUPAPISTE.Screenmessage.refresh();
      $("#sys-notification").applyBindings( {screenMessage: LUPAPISTE.Screenmessage} );
    }
  });
})();
