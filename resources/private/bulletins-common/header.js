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

    var cancel = _.partial(self.languageMenuVisible, false);

    // We cancel (aka close the menu), on dialog-close event
    // that denotes user pressing esc key and clicks outside
    // of menu area.
    hub.subscribe( "dialog-close", cancel);
    $(document).on( "click", "#language-menu", function( e ) {
      e.stopPropagation();
    });
    $(document).on( "click", cancel );
  }

  function UserMenu(vetumaService) {
    var self = this;

    self.authenticated = vetumaService.authenticated;
    self.userName = ko.computed(function() {
      return [vetumaService.userInfo.firstName(),
        vetumaService.userInfo.lastName()].join(" ");
    });

    self.vetumaLogout = function() {
      hub.send("vetumaService::logoutRequested");
    };
  }

  var langs = new Languages();

  $(function() {
    $("#language-menu").applyBindings(langs);
    hub.subscribe("vetumaService::serviceCreated", function(vetumaService) {
      $("#header-user-menu").applyBindings(new UserMenu(vetumaService));
    });
  });
})();
