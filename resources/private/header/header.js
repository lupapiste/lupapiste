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

    // We cancel (aka close the menu), on dialog-close event
    // that denotes user pressing esc key and clicks outside
    // of menu area.
    hub.subscribe( "dialog-close", cancel);
    $(document).on( "click", ".language-menu", function( e ) {
      e.stopPropagation();
    });
    $(document).on( "click", cancel );
  }

  var langs = new Languages();

  $(function() {
    $( "#language-select").applyBindings( langs );
    $( ".language-menu").applyBindings( langs );
  });
})();
