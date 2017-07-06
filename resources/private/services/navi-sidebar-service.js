// Sidebar service is needed to sync the menu button, container CSS
// classes and the actual sidebar component.
LUPAPISTE.NaviSidebarService = function() {
  "use strict";
  var self = this;

  self.iconsOnly = ko.observable();
  self.showMenu  = ko.observable();

  var animate = ko.observable();

  self.containerCss = ko.pureComputed( function ()  {
    return {"container--icon": self.iconsOnly()};
  } );

  self.menuCss = ko.computed( function ()  {
    var menu = self.showMenu();
    return {"lupicon-menu": !menu,
            "lupicon-remove": menu,
            "spin-once": animate()};
  } );

  self.menuLoc = ko.pureComputed( function() {
    return self.showMenu() ? "close" : "navi-sidebar.menu";
  });


  ko.computed( function() {
    if( self.iconsOnly() && self.showMenu()) {
      self.showMenu( false );
    }
  });

  // By adding and removing animation (see menuCss), we make sure that
  // it runs every time the menu is toggled.
  self.showMenu.subscribe( function() {
    animate( true );
    _.delay( _.wrap( false, animate), 600);
  });

  function close() {
    if( self.showMenu() ) {
      self.showMenu( false );
    }
  }

  self.openPage = function( item ) {
    pageutil.openPage( item.page );
    close();
  };

  // Pressing Esc closes menu
  hub.subscribe( "dialog-close", close );
  // Pressing outside menu closes menu
  $(document).on( "click", close );
};
